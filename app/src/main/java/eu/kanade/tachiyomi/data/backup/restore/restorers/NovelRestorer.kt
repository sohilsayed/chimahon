package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.Bookmark
import com.canopus.chimareader.data.Statistics
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelCategory
import java.io.File
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelRestorer(
    private val context: Context,
    private val novelCategoryStorage: com.canopus.chimareader.data.NovelCategoryStorage = Injekt.get()
) {

    fun restore(backupNovels: List<BackupNovel>) {
        if (backupNovels.isEmpty()) return

        backupNovels.forEach { backupNovel ->
            val bookDir = BookStorage.getBookDirectory(context, backupNovel.id)
            
            // Check if folder exists
            if (bookDir.exists()) {
                // Merge Metadata
                val localMetadata = BookStorage.loadMetadata(bookDir)
                if (localMetadata != null) {
                    val updatedMetadata = localMetadata.copy(
                        author = backupNovel.author ?: localMetadata.author,
                        categoryIds = (localMetadata.categoryIds + backupNovel.categoryIds).distinct()
                    )
                    BookStorage.saveMetadata(updatedMetadata, bookDir)
                }

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
                    author = backupNovel.author,
                    cover = backupNovel.cover, // Might be broken link until EPUB import
                    folder = backupNovel.id,
                    lastAccess = backupNovel.lastModified,
                    hash = backupNovel.id,
                    isGhost = true,
                    categoryIds = backupNovel.categoryIds
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

    fun restoreCategories(backupCategories: List<BackupNovelCategory>) {
        if (backupCategories.isEmpty()) return

        val currentCategories = novelCategoryStorage.loadAllCategories().toMutableList()
        var changed = false

        backupCategories.forEach { backupCategory ->
            val existing = currentCategories.find { it.id == backupCategory.id || it.name == backupCategory.name }
            if (existing == null) {
                currentCategories.add(
                    com.canopus.chimareader.data.NovelCategory(
                        id = backupCategory.id,
                        name = backupCategory.name,
                        order = backupCategory.order.toInt(),
                        flags = backupCategory.flags.toInt()
                    )
                )
                changed = true
            }
        }

        if (changed) {
            novelCategoryStorage.saveAllCategories(currentCategories)
        }
    }
}
