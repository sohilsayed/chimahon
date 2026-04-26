package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.Bookmark
import com.canopus.chimareader.data.Statistics
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import java.io.File

class NovelRestorer(private val context: Context) {

    fun restore(backupNovels: List<BackupNovel>) {
        if (backupNovels.isEmpty()) return

        backupNovels.forEach { backupNovel ->
            val bookDir = BookStorage.getBookDirectory(context, backupNovel.id)
            
            // Check if folder exists
            if (bookDir.exists()) {
                // Merge Bookmark
                val localBookmark = BookStorage.loadBookmark(bookDir)
                if (localBookmark == null || backupNovel.lastModified > (localBookmark.lastModified ?: 0L)) {
                    val newBookmark = Bookmark(
                        chapterIndex = backupNovel.chapterIndex,
                        progress = backupNovel.progress,
                        characterCount = backupNovel.characterCount,
                        lastModified = backupNovel.lastModified
                    )
                    BookStorage.saveBookmark(newBookmark, bookDir)
                }

                // Merge Statistics
                val localStats = BookStorage.loadStatistics(bookDir)?.toMutableList() ?: mutableListOf()
                var statsUpdated = false
                
                backupNovel.stats.forEach { backupStat ->
                    val existingIndex = localStats.indexOfFirst { it.dateKey == backupStat.dateKey }
                    if (existingIndex != -1) {
                        val existingStat = localStats[existingIndex]
                        if (backupStat.lastStatisticModified > existingStat.lastStatisticModified) {
                            localStats[existingIndex] = Statistics(
                                title = backupNovel.title,
                                dateKey = backupStat.dateKey,
                                charactersRead = backupStat.charactersRead,
                                readingTime = backupStat.readingTime,
                                minReadingSpeed = backupStat.minReadingSpeed,
                                altMinReadingSpeed = backupStat.altMinReadingSpeed,
                                lastReadingSpeed = backupStat.lastReadingSpeed,
                                maxReadingSpeed = backupStat.maxReadingSpeed,
                                lastStatisticModified = backupStat.lastStatisticModified
                            )
                            statsUpdated = true
                        }
                    } else {
                        localStats.add(
                            Statistics(
                                title = backupNovel.title,
                                dateKey = backupStat.dateKey,
                                charactersRead = backupStat.charactersRead,
                                readingTime = backupStat.readingTime,
                                minReadingSpeed = backupStat.minReadingSpeed,
                                altMinReadingSpeed = backupStat.altMinReadingSpeed,
                                lastReadingSpeed = backupStat.lastReadingSpeed,
                                maxReadingSpeed = backupStat.maxReadingSpeed,
                                lastStatisticModified = backupStat.lastStatisticModified
                            )
                        )
                        statsUpdated = true
                    }
                }
                
                if (statsUpdated) {
                    BookStorage.saveStatistics(localStats, bookDir)
                }
            } else {
                // Ghost book
                bookDir.mkdirs()

                val metadata = BookMetadata(
                    id = backupNovel.id,
                    title = backupNovel.title,
                    cover = backupNovel.cover, // Might be broken link until EPUB import
                    folder = backupNovel.id,
                    lastAccess = backupNovel.lastModified,
                    hash = backupNovel.id,
                    isGhost = true
                )
                BookStorage.saveMetadata(metadata, bookDir)

                // Bookmark
                if (backupNovel.lastModified > 0) {
                    val newBookmark = Bookmark(
                        chapterIndex = backupNovel.chapterIndex,
                        progress = backupNovel.progress,
                        characterCount = backupNovel.characterCount,
                        lastModified = backupNovel.lastModified
                    )
                    BookStorage.saveBookmark(newBookmark, bookDir)
                }

                // Statistics
                if (backupNovel.stats.isNotEmpty()) {
                    val stats = backupNovel.stats.map {
                        Statistics(
                            title = backupNovel.title,
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
                    BookStorage.saveStatistics(stats, bookDir)
                }
            }
        }
    }
}
