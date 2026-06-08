package com.canopus.chimareader.ttusync

import android.content.Context
import android.util.Log
import com.canopus.chimareader.data.Bookmark
import com.canopus.chimareader.data.BookInfo
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.ChapterInfo
import com.canopus.chimareader.data.SasayakiPlaybackData
import com.canopus.chimareader.data.Statistics
import kotlinx.serialization.json.Json
import java.io.File

class TtuSyncManager(
    private val context: Context,
    private val authManager: TtuOAuthManager,
    private val settingsRepository: SyncSettingsRepository,
    private val driveClient: TtuDriveClient = TtuDriveClient(context, authManager),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {

    val settingsFlow: kotlinx.coroutines.flow.Flow<SyncSettings>
        get() = settingsRepository.settings

    val autoSyncEnabled: Boolean
        get() = settingsRepository.currentSettings().autoSyncEnabled

    val autoSyncOnOpen: Boolean
        get() = settingsRepository.currentSettings().autoSyncOnOpen

    val autoSyncOnClose: Boolean
        get() = settingsRepository.currentSettings().autoSyncOnClose

    val autoSyncPeriodic: Boolean
        get() = settingsRepository.currentSettings().autoSyncPeriodic

    val autoSyncIntervalMins: Int
        get() = settingsRepository.currentSettings().autoSyncIntervalMins

    val statisticsSyncEnabled: Boolean
        get() = settingsRepository.currentSettings().statisticsSyncEnabled

    val audioBookSyncEnabled: Boolean
        get() = settingsRepository.currentSettings().audioBookSyncEnabled

    val statisticsSyncMode: StatisticsSyncMode
        get() = settingsRepository.currentSettings().statisticsSyncMode

    fun loadSettings(): SyncSettings = settingsRepository.currentSettings()

    fun saveSettings(settings: SyncSettings) {
        settingsRepository.update { settings }
    }

    fun updateSettings(transform: (SyncSettings) -> SyncSettings) {
        settingsRepository.update(transform)
    }

    val isEnabled: Boolean get() = settingsRepository.currentSettings().enabled && authManager.isConnected

    fun clearCache() {
        driveClient.clearCache()
    }

    suspend fun syncBook(
        bookMetadata: BookMetadata,
        direction: SyncDirection = SyncDirection.AUTO,
        importOnly: Boolean = false,
    ): SyncResult {
        val titleForLog = bookMetadata.title ?: "Unknown"
        Log.d("TtuSyncManager", "syncBook requested: id=${bookMetadata.id}, title='$titleForLog', direction=$direction, importOnly=$importOnly")
        if (!isEnabled) {
            Log.d(
                "TtuSyncManager",
                "syncBook skipped: enabled=${settingsRepository.currentSettings().enabled}, connected=${authManager.isConnected}",
            )
            return SyncResult.Skipped
        }

        val bookDir = BookStorage.getBookDirectory(context, bookMetadata.id)
        if (!bookDir.exists()) {
            Log.d("TtuSyncManager", "syncBook skipped: book directory does not exist: ${bookDir.absolutePath}")
            return SyncResult.Skipped
        }

        if (bookMetadata.title.isNullOrBlank()) {
            Log.d("TtuSyncManager", "syncBook skipped: title is blank")
            return SyncResult.Skipped
        }
        val displayTitle = bookMetadata.title

        return try {
            performSync(bookMetadata, bookDir, direction, importOnly, displayTitle).also {
                Log.d("TtuSyncManager", "syncBook finished: title='$displayTitle', result=$it")
            }
        } catch (e: DriveFileNotFoundException) {
            Log.w("TtuSyncManager", "syncBook got stale Drive file, clearing cache and retrying", e)
            driveClient.clearCache()
            try {
                performSync(bookMetadata, bookDir, direction, importOnly, displayTitle).also {
                    Log.d("TtuSyncManager", "syncBook retry finished: title='$displayTitle', result=$it")
                }
            } catch (e2: Exception) {
                Log.e("TtuSyncManager", "syncBook retry failed: title='$displayTitle'", e2)
                SyncResult.Failed(displayTitle, e2.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            Log.e("TtuSyncManager", "syncBook failed: title='$displayTitle'", e)
            SyncResult.Failed(displayTitle, e.message ?: "Unknown error")
        }
    }

    private fun performSync(
        bookMetadata: BookMetadata,
        bookDir: File,
        direction: SyncDirection,
        importOnly: Boolean,
        displayTitle: String,
    ): SyncResult {
        val rootId = driveClient.findOrCreateRootFolder()
        val sanitizedTitle = TtuSyncRules.sanitizeTtuFilename(bookMetadata.title ?: "")
        val folderName = bookMetadata.ttuFolderName ?: sanitizedTitle

        val bookFolderId = driveClient.findOrCreateBookFolder(
            rootId = rootId,
            folderName = folderName,
            coverDataProvider = bookMetadata.cover?.let { coverPath ->
                val coverFile = if (File(coverPath).isAbsolute) {
                    File(coverPath)
                } else {
                    File(bookDir, coverPath)
                }
                if (coverFile.isFile) {
                    { coverFile.readBytes() }
                } else {
                    null
                }
            },
        )

        if (bookMetadata.ttuFolderName == null) {
            saveTtuFolderName(bookMetadata, folderName, bookDir)
        }

        val remoteFiles = driveClient.listSyncFiles(bookFolderId)
        val localBookmark = BookStorage.loadBookmark(bookDir)
        Log.d(
            "TtuSyncManager",
            "performSync state: folder='$folderName', localLastModified=${localBookmark?.lastModified}, remoteProgress=${remoteFiles.progress?.name}",
        )

        val resolvedDirection = if (direction != SyncDirection.AUTO) {
            direction
        } else {
            val localTs = localBookmark?.lastModified
            TtuSyncRules.determineDirection(localTs, remoteFiles.progress)
        }
        Log.d("TtuSyncManager", "performSync direction: requested=$direction, resolved=$resolvedDirection")

        if (importOnly && resolvedDirection != SyncDirection.IMPORT) {
            Log.d("TtuSyncManager", "performSync importOnly skipped: resolvedDirection=$resolvedDirection")
            return SyncResult.Synced(displayTitle)
        }

        return when (resolvedDirection) {
            SyncDirection.IMPORT -> importFromTtu(bookMetadata, bookDir, localBookmark, remoteFiles, displayTitle)
            SyncDirection.EXPORT -> exportToTtu(bookMetadata, bookDir, localBookmark, remoteFiles, bookFolderId, displayTitle)
            SyncDirection.SYNCED -> SyncResult.Synced(displayTitle)
            SyncDirection.AUTO -> SyncResult.Skipped
        }
    }

    private fun importFromTtu(
        bookMetadata: BookMetadata,
        bookDir: File,
        localBookmark: Bookmark?,
        remoteFiles: DriveSyncFiles,
        displayTitle: String,
    ): SyncResult {
        var imported = false
        var importedCharacterCount = localBookmark?.characterCount ?: 0

        if (remoteFiles.progress != null) {
            try {
                val content = driveClient.downloadFile(remoteFiles.progress.id)
                val ttuProgress = json.decodeFromString<TtuProgress>(content)
                val bookInfo = loadOrBuildBookInfo(bookDir)
                val resolved = bookInfo?.resolveCharacterPosition(ttuProgress.exploredCharCount)
                val bookmark = Bookmark(
                    chapterIndex = resolved?.first ?: 0,
                    progress = (resolved?.second ?: ttuProgress.progress).coerceIn(0.0, 1.0),
                    characterCount = ttuProgress.exploredCharCount,
                    lastModified = ttuProgress.lastBookmarkModified,
                )
                BookStorage.saveBookmark(bookmark, bookDir)
                importedCharacterCount = ttuProgress.exploredCharCount
                imported = true
                Log.d(
                    "TtuSyncManager",
                    "importProgress saved: title='$displayTitle', remoteFile='${remoteFiles.progress.name}', " +
                        "chapter=${bookmark.chapterIndex}, progress=${bookmark.progress}, chars=${bookmark.characterCount}",
                )
            } catch (e: Exception) {
                Log.w("TtuSyncManager", "importProgress failed: title='$displayTitle', file='${remoteFiles.progress.name}'", e)
            }
        } else {
            Log.d("TtuSyncManager", "importProgress skipped: title='$displayTitle', no remote progress file")
        }

        if (settingsRepository.currentSettings().statisticsSyncEnabled && remoteFiles.statistics != null) {
            try {
                val content = driveClient.downloadFile(remoteFiles.statistics.id)
                importStatistics(bookDir, content)
            } catch (_: Exception) {
            }
        }

        if (settingsRepository.currentSettings().audioBookSyncEnabled && remoteFiles.audioBook != null) {
            try {
                val content = driveClient.downloadFile(remoteFiles.audioBook.id)
                val ttuAudio = json.decodeFromString<TtuAudioBook>(content)
                val playback = SasayakiPlaybackData(
                    lastPosition = ttuAudio.playbackPosition,
                )
                BookStorage.saveSasayakiPlaybackData(playback, bookDir)
            } catch (_: Exception) {
            }
        }

        val title = displayTitle
        return if (imported) SyncResult.Imported(title, importedCharacterCount) else SyncResult.Synced(title)
    }

    private fun exportToTtu(
        bookMetadata: BookMetadata,
        bookDir: File,
        localBookmark: Bookmark?,
        remoteFiles: DriveSyncFiles,
        bookFolderId: String,
        displayTitle: String,
    ): SyncResult {
        val title = displayTitle
        val bookInfo = loadOrBuildBookInfo(bookDir)
        var progressExported = false
        var progressCharacterCount = localBookmark?.characterCount ?: 0

        if (localBookmark != null) {
            val lastModified = localBookmark.lastModified ?: System.currentTimeMillis()
            // Round to millisecond precision (matching iOS behavior)
            val unixTimestamp = lastModified
            val charBasedProgress = if (bookInfo != null && bookInfo.characterCount > 0) {
                localBookmark.characterCount.toDouble() / bookInfo.characterCount
            } else {
                Log.w("TtuSyncManager", "exportProgress using bookmark progress fallback: title='$title', bookInfo=${bookInfo != null}")
                localBookmark.progress
            }.coerceIn(0.0, 1.0)

            // Fetch remote progress to preserve dataId
            val remoteProgress = try {
                remoteFiles.progress?.let { driveClient.downloadFile(it.id) }
                    ?.let { json.decodeFromString<TtuProgress>(it) }
            } catch (_: Exception) {
                null
            }

            val ttuProgress = TtuProgress(
                dataId = remoteProgress?.dataId ?: 0,
                exploredCharCount = localBookmark.characterCount,
                progress = charBasedProgress,
                lastBookmarkModified = unixTimestamp,
            )
            val content = json.encodeToString(TtuProgress.serializer(), ttuProgress)
            val fileName = TtuSyncRules.progressFileName(ttuProgress)

            if (remoteFiles.progress != null) {
                driveClient.updateFile(remoteFiles.progress.id, fileName, content)
            } else {
                driveClient.uploadFile(bookFolderId, fileName, content)
            }
            progressExported = true
            progressCharacterCount = localBookmark.characterCount
            Log.d(
                "TtuSyncManager",
                "exportProgress saved: title='$title', file='$fileName', chars=${localBookmark.characterCount}, progress=$charBasedProgress",
            )

            // Save rounded timestamp back to local bookmark (matching iOS behavior)
            BookStorage.saveBookmark(
                localBookmark.copy(lastModified = unixTimestamp),
                bookDir,
            )
        } else {
            Log.w("TtuSyncManager", "exportProgress skipped: title='$title', no local bookmark")
        }

        if (settingsRepository.currentSettings().statisticsSyncEnabled) {
            val localStats = BookStorage.loadStatistics(bookDir)
            if (localStats != null) {
                val mergedStats = if (remoteFiles.statistics != null) {
                    try {
                        val remoteContent = driveClient.downloadFile(remoteFiles.statistics.id)
                        val remoteStats = json.decodeFromString<List<Statistics>>(remoteContent)
                        mergeStatisticsForExport(remoteStats, localStats)
                    } catch (_: Exception) {
                        localStats
                    }
                } else {
                    localStats
                }
                val content = json.encodeToString(mergedStats)
                val fileName = TtuSyncRules.statisticsFileName(mergedStats)
                if (remoteFiles.statistics != null) {
                    driveClient.updateFile(remoteFiles.statistics.id, fileName, content)
                } else {
                    driveClient.uploadFile(bookFolderId, fileName, content)
                }
            }
        }

        if (settingsRepository.currentSettings().audioBookSyncEnabled) {
            val playback = BookStorage.loadSasayakiPlaybackData(bookDir)
            if (playback != null) {
                val ttuAudio = TtuAudioBook(
                    title = title,
                    playbackPosition = playback.lastPosition,
                    lastAudioBookModified = System.currentTimeMillis(),
                )
                val content = json.encodeToString(TtuAudioBook.serializer(), ttuAudio)
                val fileName = TtuSyncRules.audioBookFileName(ttuAudio)
                if (remoteFiles.audioBook != null) {
                    driveClient.updateFile(remoteFiles.audioBook.id, fileName, content)
                } else {
                    driveClient.uploadFile(bookFolderId, fileName, content)
                }
            }
        }

        return if (progressExported) {
            SyncResult.Exported(title, progressCharacterCount)
        } else {
            SyncResult.Synced(title)
        }
    }

    private fun loadOrBuildBookInfo(bookDir: File): BookInfo? {
        BookStorage.loadBookInfo(bookDir)?.let { return it }
        return try {
            val epub = BookStorage.loadEpub(bookDir)
            var runningTotal = 0
            val chapters = linkedMapOf<String, ChapterInfo>()
            for (index in epub.linearSpineItems.indices) {
                val chapterCount = epub.getChapterCharacters(index)
                chapters[index.toString()] = ChapterInfo(
                    spineIndex = index,
                    currentTotal = runningTotal,
                    chapterCount = chapterCount,
                )
                runningTotal += chapterCount
            }
            BookInfo(
                characterCount = runningTotal,
                chapterInfo = chapters,
            ).also {
                BookStorage.saveBookInfo(it, bookDir)
                Log.d(
                    "TtuSyncManager",
                    "Built missing bookinfo: dir='${bookDir.name}', chapters=${chapters.size}, chars=$runningTotal",
                )
            }
        } catch (e: Exception) {
            Log.w("TtuSyncManager", "Failed to build missing bookinfo: dir='${bookDir.name}'", e)
            null
        }
    }

    // iOS export: start with remote, overlay with local (newer wins)
    private fun mergeStatisticsForExport(base: List<Statistics>, overlay: List<Statistics>): List<Statistics> {
        if (settingsRepository.currentSettings().statisticsSyncMode == StatisticsSyncMode.Replace) return overlay
        val grouped = linkedMapOf<String, Statistics>()
        for (stat in base) grouped[stat.dateKey] = stat
        for (stat in overlay) {
            val existing = grouped[stat.dateKey]
            if (existing == null || stat.lastStatisticModified > existing.lastStatisticModified) {
                grouped[stat.dateKey] = stat
            }
        }
        return grouped.values.toList()
    }

    // iOS import: start with local, overlay with remote
    private fun importStatistics(bookDir: File, remoteContent: String) {
        val remoteStats = try {
            json.decodeFromString<List<Statistics>>(remoteContent)
        } catch (_: Exception) {
            return
        }
        val localStats = BookStorage.loadStatistics(bookDir) ?: emptyList()
        val merged = if (settingsRepository.currentSettings().statisticsSyncMode == StatisticsSyncMode.Replace) {
            remoteStats
        } else {
            val grouped = linkedMapOf<String, Statistics>()
            for (stat in localStats) grouped[stat.dateKey] = stat
            for (stat in remoteStats) {
                val existing = grouped[stat.dateKey]
                if (existing == null || stat.lastStatisticModified > existing.lastStatisticModified) {
                    grouped[stat.dateKey] = stat
                }
            }
            grouped.values.toList()
        }
        BookStorage.saveStatistics(merged, bookDir)
    }

    private fun saveTtuFolderName(metadata: BookMetadata, folderName: String, bookDir: File) {
        val updated = metadata.copy(ttuFolderName = folderName)
        BookStorage.saveMetadata(updated, bookDir)
    }
}
