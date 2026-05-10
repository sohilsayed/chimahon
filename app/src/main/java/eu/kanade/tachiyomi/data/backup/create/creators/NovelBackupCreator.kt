package eu.kanade.tachiyomi.data.backup.create.creators

import android.content.Context
import android.util.Log
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelCategory
import eu.kanade.tachiyomi.data.backup.models.BackupStatEntry
import java.security.MessageDigest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelBackupCreator(
    private val context: Context,
    private val novelCategoryStorage: com.canopus.chimareader.data.NovelCategoryStorage = Injekt.get()
) {

    private val TAG = "NovelBackupCreator"

    fun backupNovels(): List<BackupNovel> {
        val backupNovels = mutableListOf<BackupNovel>()
        val booksDir = BookStorage.getBooksDirectory(context) ?: return emptyList()

        booksDir.listFiles()?.forEach { bookDir ->
            if (!bookDir.isDirectory) return@forEach

            try {
                val metadata = BookStorage.loadMetadata(bookDir) ?: return@forEach
                val bookmark = BookStorage.loadBookmark(bookDir)
                val stats = BookStorage.loadStatistics(bookDir)

                val stableId = md5Hex(metadata.title ?: bookDir.name)

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
                } ?: emptyList<BackupStatEntry>()

                backupNovels.add(
                    BackupNovel(
                        id = stableId,
                        title = metadata.title ?: "",
                        author = metadata.author,
                        cover = metadata.cover,
                        chapterIndex = bookmark?.chapterIndex ?: 0,
                        progress = bookmark?.progress ?: 0.0,
                        characterCount = bookmark?.characterCount ?: 0,
                        lastModified = bookmark?.lastModified ?: 0L,
                        stats = backupStats,
                        categoryIds = metadata.categoryIds
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to backup novel in ${bookDir.name}", e)
            }
        }

        return backupNovels
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

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
