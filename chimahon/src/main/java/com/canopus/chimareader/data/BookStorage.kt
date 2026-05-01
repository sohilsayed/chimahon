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
        val cachedSpine = loadSpineCache(directory)
        return EpubParser.parse(directory, cachedSpine)
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
    fun saveSpineCache(spine: com.canopus.chimareader.data.epub.EpubSpine, directory: File) {
        save(spine, directory, "spine_cache.json")
    }

    fun loadSpineCache(directory: File): com.canopus.chimareader.data.epub.EpubSpine? {
        return load(directory, "spine_cache.json")
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

        val books = mutableListOf<BookMetadata>()
        booksDir.listFiles()?.filter { it.isDirectory }?.forEach { bookDir ->
            val metadata = loadMetadata(bookDir)
            if (metadata != null) {
                books.add(metadata)
            }
        }
        return books
            .groupBy { it.id }
            .map { (_, dupes) ->
                if (dupes.size == 1) {
                    dupes.first()
                } else {
                    dupes.minWith(compareBy({ it.isGhost }, { -(it.lastAccess) }))
                }
            }
    }

    fun deleteBook(context: android.content.Context, bookId: String): Boolean {
        val bookDir = getBookDirectory(context, bookId)
        return delete(bookDir)
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
