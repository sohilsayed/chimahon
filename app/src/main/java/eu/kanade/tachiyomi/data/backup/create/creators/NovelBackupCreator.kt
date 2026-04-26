package eu.kanade.tachiyomi.data.backup.create.creators

import android.content.Context
import android.util.Log
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupStatEntry
import java.security.MessageDigest

class NovelBackupCreator(private val context: Context) {

    private val TAG = "NovelBackupCreator"

    fun backupNovels(): List<BackupNovel> {
        val booksDir = BookStorage.getBooksDirectory(context)
        if (!booksDir.exists()) {
            Log.d(TAG, "Books directory does not exist, nothing to backup")
            return emptyList()
        }

        val bookDirs = booksDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        Log.d(TAG, "Found ${bookDirs.size} book directories")

        val backupNovels = mutableListOf<BackupNovel>()

        bookDirs.forEach { bookDir ->
            try {
                // Try to load metadata; if missing, attempt to parse the EPUB as a fallback
                val metadata: BookMetadata = BookStorage.loadMetadata(bookDir) ?: run {
                    Log.w(TAG, "${bookDir.name}: no metadata.json, attempting EPUB parse fallback")
                    try {
                        val book = BookStorage.loadEpub(bookDir)
                        val title = book.title ?: "Unknown"
                        val author = book.author ?: ""
                        val hash = md5Hex("${title.trim().lowercase()}|${author.trim().lowercase()}")
                        val coverAbsPath = book.coverPath?.let { java.io.File(bookDir, it).absolutePath }
                        BookMetadata(
                            id = hash,
                            title = title,
                            cover = coverAbsPath,
                            folder = hash,
                            hash = hash,
                            isGhost = false
                        ).also { BookStorage.saveMetadata(it, bookDir) }
                    } catch (e: Exception) {
                        Log.e(TAG, "${bookDir.name}: EPUB parse fallback also failed: ${e.message}")
                        return@forEach
                    }
                }

                val bookmark = BookStorage.loadBookmark(bookDir)
                val stats = BookStorage.loadStatistics(bookDir) ?: emptyList()

                // Skip ghost books with no progress
                if (metadata.isGhost && bookmark == null && stats.isEmpty()) {
                    Log.d(TAG, "${bookDir.name}: ghost with no progress, skipping")
                    return@forEach
                }

                val backupStats = stats.map {
                    BackupStatEntry(
                        dateKey = it.dateKey,
                        charactersRead = it.charactersRead,
                        readingTime = it.readingTime,
                        minReadingSpeed = it.minReadingSpeed,
                        altMinReadingSpeed = it.altMinReadingSpeed,
                        lastReadingSpeed = it.lastReadingSpeed,
                        maxReadingSpeed = it.maxReadingSpeed,
                        lastStatisticModified = it.lastStatisticModified
                    )
                }

                val stableId = metadata.hash ?: metadata.id
                Log.d(TAG, "Backing up: '${metadata.title}' id=$stableId bookmark=${bookmark != null} stats=${stats.size}")

                backupNovels.add(
                    BackupNovel(
                        id = stableId,
                        title = metadata.title ?: "",
                        author = null,
                        cover = metadata.cover,
                        chapterIndex = bookmark?.chapterIndex ?: 0,
                        progress = bookmark?.progress ?: 0.0,
                        characterCount = bookmark?.characterCount ?: 0,
                        lastModified = bookmark?.lastModified ?: 0L,
                        stats = backupStats
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error backing up ${bookDir.name}: ${e.message}")
            }
        }

        Log.d(TAG, "Novel backup complete: ${backupNovels.size} novels")
        return backupNovels
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
