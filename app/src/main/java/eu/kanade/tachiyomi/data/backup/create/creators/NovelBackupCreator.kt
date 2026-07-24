package eu.kanade.tachiyomi.data.backup.create.creators

import android.content.Context
import android.util.Log
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.NovelCategory
import com.canopus.chimareader.data.md5Hex
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelCategory
import eu.kanade.tachiyomi.data.backup.models.BackupStatEntry
import java.io.File
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelBackupCreator(
    private val context: Context,
    private val novelCategoryStorage: com.canopus.chimareader.data.NovelCategoryStorage = Injekt.get()
) {

    private val TAG = "NovelBackupCreator"

    fun backupNovels(): List<BackupNovel> {
        val backupNovelsById = linkedMapOf<String, BackupNovel>()
        val booksDir = BookStorage.getBooksDirectory(context) ?: return emptyList()

        val bookEntries = booksDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { bookDir ->
                val metadata = BookStorage.loadMetadata(bookDir)?.let {
                    if (it.isGhost && BookStorage.hasImportedBookContent(bookDir)) {
                        it.copy(isGhost = false)
                    } else {
                        it
                    }
                } ?: return@mapNotNull null
                BookEntry(bookDir, metadata)
            }
            .orEmpty()

        bookEntries.forEach { entry ->
            try {
                val backupNovel = createBackupNovel(entry)
                val existing = backupNovelsById[backupNovel.id]
                backupNovelsById[backupNovel.id] = if (existing == null) {
                    backupNovel
                } else {
                    mergeBackupNovel(existing, backupNovel)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to backup novel in ${entry.directory.name}", e)
            }
        }

        return backupNovelsById.values.toList()
    }

    fun backupCategories(): List<BackupNovelCategory> {
        val categories = novelCategoryStorage.loadAllCategories()
        return categories.map {
            BackupNovelCategory(
                id = it.id,
                name = it.name,
                order = it.order.toLong(),
                flags = it.flags.toLong()
            )
        }
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

    private fun stableIdFor(metadata: BookMetadata): String {
        val title = (metadata.title ?: "").trim().lowercase()
        val author = (metadata.author ?: "").trim().lowercase()
        return if (title.isNotEmpty() || author.isNotEmpty()) {
            md5Hex("$title|$author")
        } else {
            metadata.id
        }
    }

    private fun createBackupNovel(entry: BookEntry): BackupNovel {
        val bookDir = entry.directory
        val metadata = entry.metadata
        val bookmark = BookStorage.loadBookmark(bookDir)
        val stats = BookStorage.loadStatistics(bookDir)
        val stableId = stableIdFor(metadata)

        val backupStats = stats?.map {
            BackupStatEntry(
                dateKey = it.dateKey,
                charactersRead = it.charactersRead,
                readingTime = it.readingTime,
                minReadingSpeed = it.minReadingSpeed,
                altMinReadingSpeed = it.altMinReadingSpeed,
                lastReadingSpeed = it.lastReadingSpeed,
                maxReadingSpeed = it.maxReadingSpeed,
                lastStatisticModified = it.lastStatisticModified,
            )
        } ?: emptyList()

        return BackupNovel(
            id = stableId,
            title = metadata.title ?: "",
            author = metadata.author,
            cover = metadata.cover,
            chapterIndex = bookmark?.chapterIndex ?: 0,
            progress = bookmark?.progress ?: 0.0,
            characterCount = bookmark?.characterCount ?: 0,
            lastModified = bookmark?.lastModified ?: 0L,
            stats = mergeBackupStats(backupStats),
            categoryIds = normalizeCategoryIds(metadata.categoryIds),
            lang = metadata.lang,
        )
    }

    private fun mergeBackupNovel(first: BackupNovel, second: BackupNovel): BackupNovel {
        val latest = if (first.lastModified >= second.lastModified) first else second
        val fallback = if (latest == first) second else first

        return latest.copy(
            id = first.id,
            author = latest.author ?: fallback.author,
            cover = latest.cover ?: fallback.cover,
            stats = mergeBackupStats(first.stats + second.stats),
            categoryIds = normalizeCategoryIds(first.categoryIds + second.categoryIds),
            lang = latest.lang ?: fallback.lang,
        )
    }

    private fun mergeBackupStats(stats: List<BackupStatEntry>): List<BackupStatEntry> {
        return stats
            .groupBy { it.dateKey }
            .map { (_, entries) ->
                entries.reduce { latest, candidate ->
                    if (candidate.lastStatisticModified > latest.lastStatisticModified) candidate else latest
                }
            }
    }

    private data class BookEntry(
        val directory: File,
        val metadata: BookMetadata,
    )

}
