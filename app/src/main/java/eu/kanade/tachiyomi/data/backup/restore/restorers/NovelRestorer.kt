package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.Bookmark
import com.canopus.chimareader.data.NovelCategory
import com.canopus.chimareader.data.Statistics
import com.canopus.chimareader.data.md5Hex
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelCategory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelRestorer(
    private val context: Context,
    private val novelCategoryStorage: com.canopus.chimareader.data.NovelCategoryStorage = Injekt.get()
) {

    fun restore(
        backupNovels: List<BackupNovel>,
        categoryIdMap: Map<String, String> = emptyMap(),
    ) {
        backupNovels.forEach { restoreNovel(it, categoryIdMap) }
    }

    fun restoreNovel(
        backupNovel: BackupNovel,
        categoryIdMap: Map<String, String> = emptyMap(),
    ) {
        val novelId = stableNovelId(backupNovel)
        val bookDir = BookStorage.getBookDirectory(context, novelId)
        val backupCategoryIds = normalizeCategoryIds(
            backupNovel.categoryIds.map { categoryIdMap[it] ?: it },
        )

        if (bookDir.exists()) {
            // Merge Metadata
            val localMetadata = BookStorage.loadMetadata(bookDir)
            if (localMetadata != null) {
                val hasImportedContent = BookStorage.hasImportedBookContent(bookDir)
                val updatedMetadata = localMetadata.copy(
                    author = backupNovel.author ?: localMetadata.author,
                    cover = backupNovel.cover ?: localMetadata.cover,
                    lang = backupNovel.lang ?: localMetadata.lang,
                    isGhost = if (hasImportedContent) false else localMetadata.isGhost,
                    categoryIds = mergeCategoryIds(localMetadata.categoryIds, backupCategoryIds)
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
                id = novelId,
                title = backupNovel.title,
                author = backupNovel.author,
                cover = backupNovel.cover, // Might be broken link until EPUB import
                folder = novelId,
                lastAccess = backupNovel.lastModified,
                hash = novelId,
                isGhost = true,
                lang = backupNovel.lang,
                categoryIds = backupCategoryIds
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

    fun restoreCategories(backupCategories: List<BackupNovelCategory>): Map<String, String> {
        val categoryIdMap = mutableMapOf(
            NovelCategory.UNCATEGORIZED_ID to NovelCategory.UNCATEGORIZED_ID,
        )
        if (backupCategories.isEmpty()) return categoryIdMap

        val currentCategories = novelCategoryStorage.loadAllCategories().toMutableList()
        var changed = false

        backupCategories.forEach { backupCategory ->
            val existingIndex = currentCategories.indexOfFirst {
                it.id == backupCategory.id || categoryKey(it.name) == categoryKey(backupCategory.name)
            }

            if (existingIndex == -1) {
                val restoredCategory = NovelCategory(
                    id = backupCategory.id,
                    name = backupCategory.name,
                    order = backupCategory.order.toInt(),
                    flags = backupCategory.flags,
                )
                currentCategories.add(restoredCategory)
                categoryIdMap[backupCategory.id] = restoredCategory.id
                changed = true
            } else {
                val existing = currentCategories[existingIndex]
                categoryIdMap[backupCategory.id] = existing.id

                val updatedCategory = existing.copy(
                    name = backupCategory.name,
                    order = backupCategory.order.toInt(),
                    flags = backupCategory.flags,
                )
                if (updatedCategory != existing) {
                    currentCategories[existingIndex] = updatedCategory
                    changed = true
                }
            }
        }

        if (changed) {
            novelCategoryStorage.saveCategories(currentCategories)
        }

        return categoryIdMap
    }

    private fun mergeCategoryIds(localIds: List<String>, backupIds: List<String>): List<String> {
        return normalizeCategoryIds(localIds + backupIds)
    }

    private fun normalizeCategoryIds(categoryIds: List<String>): List<String> {
        val distinctIds = categoryIds
            .filter { it.isNotBlank() }
            .distinct()

        return if (distinctIds.any { it != NovelCategory.UNCATEGORIZED_ID }) {
            distinctIds.filterNot { it == NovelCategory.UNCATEGORIZED_ID }
        } else {
            distinctIds
        }
    }

    private fun stableNovelId(backupNovel: BackupNovel): String {
        val title = backupNovel.title.trim().lowercase()
        val author = backupNovel.author?.trim()?.lowercase().orEmpty()
        return if (title.isNotEmpty() || author.isNotEmpty()) {
            md5Hex("$title|$author")
        } else {
            backupNovel.id
        }
    }

    private fun categoryKey(name: String): String {
        return name.trim().lowercase()
    }
}
