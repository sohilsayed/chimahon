package eu.kanade.tachiyomi.data.sync.ttsu

import android.content.Context
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.Bookmark
import com.canopus.chimareader.data.epub.EpubBook
import com.canopus.chimareader.data.Statistics
import com.canopus.chimareader.data.BookMetadata

/**
 * Data format matching TTSU's BooksDbV6BookmarkData
 */
@Serializable
data class TtsuBookmarkData(
    val dataId: Int = 1,
    val exploredCharCount: Int,
    val progress: Double,
    val lastBookmarkModified: Long
)

class TtsuSyncManager(private val context: Context) {
    private val googleDriveService = GoogleDriveService(context)
    private val json = Json { ignoreUnknownKeys = true }

    // TTSU version constants
    private val exporterVersion = 4
    private val dbVersion = 6

    suspend fun pushProgressToGoogleDrive(
        bookTitle: String,
        exploredCharCount: Int,
        progress: Double,
        lastModified: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val drive = googleDriveService.driveService
        if (drive == null) {
            logcat(LogPriority.ERROR) { "Google Drive not authenticated" }
            return@withContext false
        }

        try {
            googleDriveService.refreshToken()
            
            val folderId = getOrCreateFolder(drive, bookTitle)

            // Prepare the payload
            val bookmarkData = TtsuBookmarkData(
                exploredCharCount = exploredCharCount,
                progress = progress,
                lastBookmarkModified = lastModified
            )
            val jsonContent = json.encodeToString(bookmarkData)
            val byteArrayContent = ByteArrayContent("application/json", jsonContent.toByteArray())

            // TTSU specific naming convention
            val progressInt = (progress * 100).toInt()
            val fileName = "progress_${exporterVersion}_${dbVersion}_${lastModified}_${progressInt}.json"

            // 1. Check for existing progress files in this folder
            val fileQuery = "name contains 'progress_' and mimeType='application/json' and '$folderId' in parents and trashed=false"
            val existingFiles = drive.files().list()
                .setQ(fileQuery)
                .setFields("files(id, name)")
                .execute().files

            logcat(LogPriority.DEBUG, tag = "TTSU-SYNC") { "Resolved progress filename: $fileName" }
            
            if (!existingFiles.isNullOrEmpty()) {
                // Update the first found file (PATCH)
                val existingFileId = existingFiles[0].id
                val fileMetadata = File().apply {
                    name = fileName
                }
                drive.files().update(existingFileId, fileMetadata, byteArrayContent).execute()
                
                // If there are more than 1 file, delete others to keep it clean
                if (existingFiles.size > 1) {
                    for (i in 1 until existingFiles.size) {
                        try { drive.files().delete(existingFiles[i].id).execute() } catch (e: Exception) {}
                    }
                }
                logcat(LogPriority.INFO, tag = "TTSU-SYNC") { "Successfully UPDATED TTSU progress (PATCH): $fileName" }
            } else {
                // Create new file (POST)
                val fileMetadata = File().apply {
                    name = fileName
                    mimeType = "application/json"
                    parents = listOf(folderId)
                }
                drive.files().create(fileMetadata, byteArrayContent).execute()
                logcat(LogPriority.INFO, tag = "TTSU-SYNC") { "Successfully CREATED TTSU progress (POST): $fileName" }
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, tag = "TTSU-SYNC") { "Failed to sync TTSU progress: ${e.message}" }
            false
        }
    }

    suspend fun pullBookProgressById(bookId: String): Boolean {
        val books = BookStorage.loadAllBooks(context)
        val book = books.find { it.id == bookId } ?: return false
        return pullBookProgress(book)
    }

    suspend fun pullBookProgress(book: com.canopus.chimareader.data.BookMetadata): Boolean = withContext(Dispatchers.IO) {
        val drive = googleDriveService.driveService ?: return@withContext false
        val title = book.title ?: return@withContext false
        
        try {
            googleDriveService.refreshToken()
            
            // Find root folder
            val rootQuery = "mimeType='application/vnd.google-apps.folder' and name = 'ttu-reader-data' and trashed=false"
            val rootFolderList = drive.files().list().setQ(rootQuery).setFields("files(id)").execute().files
            if (rootFolderList.isNullOrEmpty()) return@withContext false
            val rootId = rootFolderList[0].id

            // Find book folder
            val sanitizedTitle = title.replace("'", "\\'")
            val folderQuery = "mimeType='application/vnd.google-apps.folder' and name = '$sanitizedTitle' and '$rootId' in parents and trashed=false"
            val folderList = drive.files().list().setQ(folderQuery).setFields("files(id)").execute().files
            if (folderList.isNullOrEmpty()) return@withContext false
            val folderId = folderList[0].id

            // Fetch latest progress JSON file
            val fileQuery = "name contains 'progress_' and mimeType='application/json' and '$folderId' in parents and trashed=false"
            val progressFiles = drive.files().list()
                .setQ(fileQuery)
                .setFields("files(id, name, modifiedTime)")
                .setOrderBy("modifiedTime desc")
                .execute().files

            if (progressFiles.isNullOrEmpty()) return@withContext false
            val latestFile = progressFiles[0]
            
            val parts = latestFile.name.split("_", ".")
            if (parts.size < 5) return@withContext false
            
            val remoteTimestamp = parts[3].toLongOrNull() ?: 0L
            val folder = book.folder ?: return@withContext false
            val bookDir = BookStorage.getBookDirectory(context, folder)
            val localBookmark = BookStorage.loadBookmark(bookDir)
            val localTimestamp = localBookmark?.lastModified ?: 0L
            
            if (remoteTimestamp > localTimestamp) {
                logcat(LogPriority.INFO) { "TTSU: Found newer progress for '$title'. Downloading..." }
                val outputStream = java.io.ByteArrayOutputStream()
                drive.files().get(latestFile.id).executeMediaAndDownloadTo(outputStream)
                val jsonContent = outputStream.toString("UTF-8")
                val bookmarkData = json.decodeFromString<TtsuBookmarkData>(jsonContent)
                
                val epubBook = BookStorage.loadEpub(bookDir)
                val (newIndex, newProgress) = epubBook.convertCharsToProgress(bookmarkData.exploredCharCount)
                
                BookStorage.saveBookmark(
                    bookmark = Bookmark(
                        chapterIndex = newIndex,
                        progress = newProgress,
                        characterCount = bookmarkData.exploredCharCount,
                        lastModified = remoteTimestamp
                    ),
                    directory = bookDir
                )
                logcat(LogPriority.INFO) { "TTSU: Successfully applied progress for '$title'" }
                return@withContext true
            }
            false
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to pull progress for $title: ${e.message}" }
            false
        }
    }

    suspend fun pullAllProgress(): Boolean = withContext(Dispatchers.IO) {
        try {
            val localBooks = BookStorage.loadAllBooks(context)
            if (localBooks.isEmpty()) return@withContext true

            var anyUpdated = false
            for (book in localBooks) {
                if (pullBookProgress(book)) {
                    anyUpdated = true
                }
            }
            anyUpdated
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to pull all TTSU progress: ${e.message}" }
            false
        }
    }

    private fun getOrCreateFolder(drive: Drive, folderName: String): String {
        // Find or create 'ttu-reader-data'
        val rootQuery = "mimeType='application/vnd.google-apps.folder' and name = 'ttu-reader-data' and trashed=false"
        var rootList = drive.files().list().setQ(rootQuery).setFields("files(id)").execute().files
        
        val rootId = if (rootList.isNullOrEmpty()) {
            val rootMeta = File().apply {
                name = "ttu-reader-data"
                mimeType = "application/vnd.google-apps.folder"
            }
            drive.files().create(rootMeta).setFields("id").execute().id
        } else {
            rootList[0].id
        }

        // Find or create the book folder inside root
        val sanitizedTitle = folderName.replace("'", "\\'")
        val query = "mimeType='application/vnd.google-apps.folder' and name = '$sanitizedTitle' and '$rootId' in parents and trashed=false"
        val fileList = drive.files().list()
            .setQ(query)
            .setFields("files(id, name)")
            .execute()
            .files

        if (!fileList.isNullOrEmpty()) {
            return fileList[0].id
        }

        val fileMetadata = File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(rootId)
        }
        val folder = drive.files().create(fileMetadata).setFields("id").execute()
        return folder.id
    }

    suspend fun pushStatisticsToGoogleDrive(
        bookTitle: String,
        statistics: List<Statistics>
    ): Boolean = withContext(Dispatchers.IO) {
        val drive = googleDriveService.driveService ?: return@withContext false
        if (statistics.isEmpty()) return@withContext true

        try {
            googleDriveService.refreshToken()
            val folderId = getOrCreateFolder(drive, bookTitle)
            
            // Merging with remote data if exists
            val remoteData = pullStatisticsData(drive, folderId)
            val mergedStats = if (remoteData != null) {
                mergeStatistics(statistics, remoteData.statistics)
            } else {
                statistics
            }

            val lastModified = mergedStats.maxOfOrNull { it.lastStatisticModified } ?: System.currentTimeMillis()
            val fileName = getStatisticsFileName(mergedStats, lastModified)
            logcat(LogPriority.DEBUG, tag = "TTSU-SYNC") { "Resolved statistics filename: $fileName" }
            
            val jsonContent = json.encodeToString(mergedStats)
            val byteArrayContent = ByteArrayContent("application/json", jsonContent.toByteArray())

            val fileQuery = "name contains 'statistics_' and mimeType='application/json' and '$folderId' in parents and trashed=false"
            val existingFiles = drive.files().list().setQ(fileQuery).setFields("files(id, name)").execute().files

            if (!existingFiles.isNullOrEmpty()) {
                val existingFileId = existingFiles[0].id
                val fileMetadata = File().apply { name = fileName }
                drive.files().update(existingFileId, fileMetadata, byteArrayContent).execute()
                if (existingFiles.size > 1) {
                    for (i in 1 until existingFiles.size) {
                        try { drive.files().delete(existingFiles[i].id).execute() } catch (e: Exception) {}
                    }
                }
                logcat(LogPriority.INFO, tag = "TTSU-SYNC") { "Successfully UPDATED TTSU statistics (PATCH): $fileName" }
            } else {
                val fileMetadata = File().apply {
                    name = fileName
                    mimeType = "application/json"
                    parents = listOf(folderId)
                }
                drive.files().create(fileMetadata, byteArrayContent).execute()
                logcat(LogPriority.INFO, tag = "TTSU-SYNC") { "Successfully CREATED new TTSU statistics: $fileName" }
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, tag = "TTSU-SYNC") { "Failed to sync TTSU statistics: ${e.message}" }
            false
        }
    }

    private data class StatisticsFetchResult(val statistics: List<Statistics>, val fileName: String)

    private suspend fun pullStatisticsData(drive: Drive, folderId: String): StatisticsFetchResult? {
        val fileQuery = "name contains 'statistics_' and mimeType='application/json' and '$folderId' in parents and trashed=false"
        val files = drive.files().list().setQ(fileQuery).setFields("files(id, name, modifiedTime)").setOrderBy("modifiedTime desc").execute().files
        if (files.isNullOrEmpty()) return null
        
        val latestFile = files[0]
        val outputStream = java.io.ByteArrayOutputStream()
        drive.files().get(latestFile.id).executeMediaAndDownloadTo(outputStream)
        val stats = json.decodeFromString<List<Statistics>>(outputStream.toString("UTF-8"))
        return StatisticsFetchResult(stats, latestFile.name)
    }

    suspend fun pullStatistics(book: BookMetadata): Boolean = withContext(Dispatchers.IO) {
        val drive = googleDriveService.driveService ?: return@withContext false
        val title = book.title ?: return@withContext false
        try {
            googleDriveService.refreshToken()
            val rootQuery = "mimeType='application/vnd.google-apps.folder' and name = 'ttu-reader-data' and trashed=false"
            val rootFolders = drive.files().list().setQ(rootQuery).setFields("files(id)").execute().files
            if (rootFolders.isNullOrEmpty()) return@withContext false
            val rootId = rootFolders[0].id

            val sanitizedTitle = title.replace("'", "\\'")
            val folderQuery = "mimeType='application/vnd.google-apps.folder' and name = '$sanitizedTitle' and '$rootId' in parents and trashed=false"
            val folders = drive.files().list().setQ(folderQuery).setFields("files(id)").execute().files
            if (folders.isNullOrEmpty()) return@withContext false
            val folderId = folders[0].id

            val remoteResult = pullStatisticsData(drive, folderId) ?: return@withContext false
            val remoteFileName = remoteResult.fileName
            val remoteStats = remoteResult.statistics

            val parts = remoteFileName.split("_", ".")
            if (parts.size < 4) return@withContext false
            val remoteTimestamp = parts[3].toLongOrNull() ?: 0L

            val folderName = book.folder ?: return@withContext false
            val bookDir = BookStorage.getBookDirectory(context, folderName)
            val localStats = BookStorage.loadStatistics(bookDir) ?: emptyList()
            val localTimestamp = localStats.maxOfOrNull { it.lastStatisticModified } ?: 0L

            if (remoteTimestamp > localTimestamp) {
                val merged = mergeStatistics(localStats, remoteStats)
                BookStorage.saveStatistics(merged, bookDir)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to pull statistics for $title: ${e.message}" }
            false
        }
    }

    private fun mergeStatistics(local: List<Statistics>, remote: List<Statistics>): List<Statistics> {
        val combined = (local + remote).groupBy { it.dateKey }
        return combined.map { (_, entries) ->
            entries.maxBy { it.lastStatisticModified }
        }.sortedBy { it.dateKey }
    }

    private fun getStatisticsFileName(stats: List<Statistics>, lastModified: Long): String {
        var totalChars = 0
        var totalTime = 0.0
        var minSpeed = Int.MAX_VALUE
        var altMinSpeed = Int.MAX_VALUE
        var maxSpeed = 0
        var weightedSum = 0.0
        var validDays = 0
        var finishDate = "na"

        for (s in stats) {
            totalChars += s.charactersRead
            totalTime += s.readingTime
            if (s.minReadingSpeed > 0) minSpeed = minOf(minSpeed, s.minReadingSpeed)
            if (s.altMinReadingSpeed > 0) altMinSpeed = minOf(altMinSpeed, s.altMinReadingSpeed)
            maxSpeed = maxOf(maxSpeed, s.maxReadingSpeed)
            weightedSum += (s.readingTime / 1000.0) * s.charactersRead
            if (s.readingTime > 0) validDays++
            
            s.completedData?.let {
                if (finishDate == "na" || it.dateKey > finishDate) {
                    finishDate = it.dateKey
                }
            }
        }
        
        if (minSpeed == Int.MAX_VALUE) minSpeed = 0
        if (altMinSpeed == Int.MAX_VALUE) altMinSpeed = 0

        val avgTime = if (validDays > 0) (totalTime / validDays).toLong() else 0L
        val avgWeightedTime = if (totalChars > 0) (weightedSum / totalChars).toLong() else 0L
        val avgChars = if (validDays > 0) totalChars / validDays else 0
        val avgWeightedChars = if (totalTime > 0) (weightedSum / totalTime).toInt() else 0
        
        val lastSpeed = if (totalTime > 0) (totalChars / (totalTime / 3600.0)).toInt() else 0
        val avgSpeed = if (avgTime > 0) (avgChars / (avgTime / 3600.0)).toInt() else 0
        val avgWeightedSpeed = if (avgWeightedTime > 0) (avgWeightedChars / (avgWeightedTime / 3600.0)).toInt() else 0

        return "statistics_${exporterVersion}_${dbVersion}_${lastModified}_${totalChars}_${totalTime.toLong()}_${minSpeed}_${altMinSpeed}_${lastSpeed}_${maxSpeed}_${avgTime}_${avgWeightedTime}_${avgChars}_${avgWeightedChars}_${avgSpeed}_${avgWeightedSpeed}_${finishDate}.json"
    }
}
