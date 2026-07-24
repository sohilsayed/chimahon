package com.canopus.chimareader.data

import com.canopus.chimareader.data.epub.EpubBook
import com.canopus.chimareader.data.epub.EpubParseException
import com.canopus.chimareader.data.epub.EpubParser
import kotlinx.serialization.json.Json
import java.io.File

object BookStorage {

    fun getBooksDirectory(context: android.content.Context): File {
        return File(context.filesDir, "novels")
    }

    fun loadEpub(directory: File): EpubBook {
        deleteObsoleteSpineCache(directory)
        return EpubParser.parse(directory)
    }

    fun getBookDirectory(context: android.content.Context, bookId: String): File {
        return File(getBooksDirectory(context), bookId)
    }

    fun copyFile(from: File, to: File): File {
        to.parentFile?.mkdirs()
        from.copyTo(to, overwrite = true)
        return to
    }

    fun delete(file: File): Boolean {
        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    inline fun <reified T> save(`object`: T, directory: File, fileName: String) where T : Any {
        val targetFile = File(directory, fileName)
        directory.mkdirs()
        val json = kotlinx.serialization.json.Json { prettyPrint = true }
        val data = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.serializer<T>(),
            `object`,
        )
        targetFile.writeText(data)
        // Reader hot path uses save() directly (not saveBookmark/saveMetadata).
        when (fileName) {
            FileNames.bookmark, FileNames.metadata, FileNames.bookinfo ->
                chimahon.widget.ImmersionWidgetSignals.notifyNovelsChanged()
            FileNames.statistics ->
                chimahon.widget.ImmersionWidgetSignals.notifyStatsChanged()
        }
    }

    inline fun <reified T> load(directory: File, fileName: String): T? {
        val file = File(directory, fileName)
        if (!file.exists()) return null
        return try {
            val json = kotlinx.serialization.json.Json
            val content = file.readText()
            kotlinx.serialization.json.Json.decodeFromString<T>(content)
        } catch (e: Exception) {
            null
        }
    }
    fun saveBookmark(bookmark: Bookmark, directory: File) {
        save(bookmark, directory, FileNames.bookmark)
    }

    fun loadBookmark(directory: File): Bookmark? {
        return load(directory, FileNames.bookmark)
    }

    fun saveBookInfo(bookInfo: BookInfo, directory: File) {
        save(bookInfo, directory, FileNames.bookinfo)
    }

    fun loadBookInfo(directory: File): BookInfo? {
        return load(directory, FileNames.bookinfo)
    }

    fun saveMetadata(metadata: BookMetadata, directory: File) {
        save(metadata, directory, FileNames.metadata)
    }

    fun loadMetadata(directory: File): BookMetadata? {
        return load(directory, FileNames.metadata)
    }

    fun saveStatistics(statistics: List<Statistics>, directory: File) {
        save(statistics, directory, FileNames.statistics)
    }

    fun loadStatistics(directory: File): List<Statistics>? {
        return load(directory, FileNames.statistics)
    }

    fun saveSasayakiMatchData(matchData: SasayakiMatchData, directory: File) {
        save(matchData, directory, FileNames.sasayakiMatches)
    }

    fun loadSasayakiMatchData(directory: File): SasayakiMatchData? {
        return load(directory, FileNames.sasayakiMatches)
    }

    fun saveSasayakiPlaybackData(playbackData: SasayakiPlaybackData, directory: File) {
        save(playbackData, directory, FileNames.sasayakiPlayback)
    }

    fun loadSasayakiPlaybackData(directory: File): SasayakiPlaybackData? {
        return load(directory, FileNames.sasayakiPlayback)
    }

    fun loadAllBooks(context: android.content.Context): List<BookMetadata> {
        val booksDir = getBooksDirectory(context)
        if (!booksDir.exists()) return emptyList()

        return loadStoredBookDirs(booksDir)
            .groupBy { bookIdentityKey(it.metadata) }
            .map { (_, dupes) ->
                if (dupes.size == 1) {
                    dupes.first().metadata
                } else {
                    dupes.minWith(
                        compareBy<StoredBookDir>(
                            { it.metadata.isGhost },
                            { !hasImportedBookContent(it.directory) },
                            { it.directory.name != bookIdentityKey(it.metadata) },
                            { -it.metadata.lastAccess },
                        ),
                    ).metadata
                }
            }
    }

    fun bookIdentityKey(metadata: BookMetadata): String {
        val titleKey = metadata.title?.trim()?.lowercase().orEmpty()
        val authorKey = metadata.author?.trim()?.lowercase().orEmpty()
        if (titleKey.isNotEmpty() || authorKey.isNotEmpty()) {
            return md5Hex("$titleKey|$authorKey")
        }

        metadata.hash?.takeIf { it.isNotBlank() }?.let { return it }

        return metadata.id
    }

    fun hasImportedBookContent(directory: File): Boolean {
        val contentExtensions = setOf("opf", "xhtml", "html", "htm", "ncx")
        return directory.walkTopDown().any { file ->
            file.isFile && file.extension.lowercase() in contentExtensions
        }
    }

    fun deleteBook(context: android.content.Context, bookId: String): Boolean {
        val booksDir = getBooksDirectory(context)
        val bookDir = getBookDirectory(context, bookId)
        val metadata = loadMetadata(bookDir)
        val duplicateDirs = metadata?.let { targetMetadata ->
            val targetKey = bookIdentityKey(targetMetadata)
            loadStoredBookDirs(booksDir)
                .filter { bookIdentityKey(it.metadata) == targetKey }
                .map { it.directory }
        }.orEmpty()

        val dirsToDelete = (duplicateDirs + bookDir).distinctBy { it.absolutePath }
        val deleted = dirsToDelete.all { delete(it) }
        if (deleted) {
            chimahon.widget.ImmersionWidgetSignals.notifyNovelsChanged()
        }
        return deleted
    }

    private fun loadStoredBookDirs(booksDir: File): List<StoredBookDir> {
        if (!booksDir.exists()) return emptyList()

        return booksDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { bookDir ->
                val metadata = loadMetadata(bookDir)?.let {
                    val hasContent = hasImportedBookContent(bookDir)
                    val normalizedMetadata = if (it.isGhost && hasContent) {
                        it.copy(isGhost = false)
                    } else {
                        it
                    }
                    normalizedMetadata.copy(
                        id = bookDir.name,
                        folder = bookDir.name,
                    )
                } ?: return@mapNotNull null

                StoredBookDir(bookDir, metadata)
            }
            .orEmpty()
    }

    private data class StoredBookDir(
        val directory: File,
        val metadata: BookMetadata,
    )

    private fun deleteObsoleteSpineCache(directory: File) {
        File(directory, "spine_cache.json")
            .takeIf { it.exists() }
            ?.delete()
    }

    enum class BookStorageError {
        ACCESS_DENIED,
        DOCUMENTS_DIRECTORY_NOT_FOUND,
        EPUB_IMPORT_FAILED,
        ;

        fun message(): String = when (this) {
            ACCESS_DENIED -> "Could not access .epub file"
            DOCUMENTS_DIRECTORY_NOT_FOUND -> "Documents directory not found"
            EPUB_IMPORT_FAILED -> "Could not import .epub file"
        }
    }
}
