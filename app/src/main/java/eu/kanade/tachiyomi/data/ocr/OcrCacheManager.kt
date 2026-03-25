package chimahon.ocr


import android.content.Context
import android.text.format.Formatter
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import chimahon.ocr.OcrBlockData
import chimahon.ocr.OcrPageData
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get


private const val CURRENT_VERSION = 1


/**
 * Manages OCR cache storage with dual locations:
 * - Downloaded chapters: {downloads}/Tachiyomi/Source/Manga/Chapter/.ocr_cache.json or .ocr.json sidecar
 * - Online chapters: {filesDir}/ocr_cache/{sourceId}/{mangaId}/{chapterId}.json
 *
 * Storage format:
 * - For directory chapters: /Downloads/Tachiyomi/Source/Manga/Chapter/.ocr_cache.json
 * - For CBZ chapters: /Downloads/Tachiyomi/Source/Manga/Chapter.cbz.ocr.json (sidecar file)
 */
class OcrCacheManager(
    private val context: Context,
    private val json: Json,
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadProvider: DownloadProvider = Injekt.get(),
) {
    private val mutex = Mutex()

    companion object {
        private const val OCR_CACHE_FILE = ".ocr_cache.json"
        private const val OCR_SIDECAR_SUFFIX = ".ocr.json"
        private const val CURRENT_VERSION = 1
        private const val INTERNAL_OCR_DIR = "ocr_cache"
    }

    /**
     * Save OCR blocks for a single page to the chapter's OCR cache file.
     */
    suspend fun saveOcrBlocks(
        manga: Manga,
        chapter: Chapter,
        source: Source,
        pageIndex: Int,
        blocks: List<OcrTextBlock>,
        language: String,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val chapterLocation = findChapterLocation(manga, chapter, source)
            val isDownloaded = isChapterDownloaded(manga, chapter, source)

            when {
                isDownloaded && chapterLocation != null -> {
                    when (chapterLocation) {
                        is ChapterLocation.Directory -> {
                            saveToDirectory(chapterLocation.dir, pageIndex, blocks, language)
                        }
                        is ChapterLocation.Cbz -> {
                            saveToCbz(chapterLocation.file, pageIndex, blocks, language)
                        }
                    }
                }
                else -> {
                    saveToInternal(manga, chapter, source, pageIndex, blocks, language)
                }
            }
        }
    }

    /**
     * Load OCR blocks for a single page from cache.
     */
    suspend fun loadOcrBlocks(
        manga: Manga,
        chapter: Chapter,
        source: Source,
        pageIndex: Int,
    ): List<OcrTextBlock>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val chapterLocation = findChapterLocation(manga, chapter, source)
            val isDownloaded = isChapterDownloaded(manga, chapter, source)

            val downloadBlocks = if (chapterLocation != null && isDownloaded) {
                when (chapterLocation) {
                    is ChapterLocation.Directory -> loadFromDirectory(chapterLocation.dir, pageIndex)
                    is ChapterLocation.Cbz -> loadFromCbz(chapterLocation.file, pageIndex)
                }
            } else null

            if (downloadBlocks != null) {
                return@withLock downloadBlocks
            }

            loadFromInternal(manga, chapter, source, pageIndex)
        }
    }

    /**
     * Check if OCR data exists for a chapter.
     */
    suspend fun hasOcrData(
        manga: Manga,
        chapter: Chapter,
        source: Source,
    ): Boolean = withContext(Dispatchers.IO) {
        val chapterLocation = findChapterLocation(manga, chapter, source)
        val isDownloaded = isChapterDownloaded(manga, chapter, source)

        val hasDownloadCache = if (chapterLocation != null && isDownloaded) {
            when (chapterLocation) {
                is ChapterLocation.Directory -> {
                    chapterLocation.dir.findFile(OCR_CACHE_FILE)?.exists() == true
                }
                is ChapterLocation.Cbz -> {
                    getSidecarFile(chapterLocation.file)?.exists() == true
                }
            }
        } else false

        val hasInternalCache = getInternalCacheFile(manga, chapter, source).exists()

        hasDownloadCache || hasInternalCache
    }

    /**
     * Delete OCR data for a chapter.
     */
    suspend fun deleteOcrForChapter(
        manga: Manga,
        chapter: Chapter,
        source: Source,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val chapterLocation = findChapterLocation(manga, chapter, source)
            val isDownloaded = isChapterDownloaded(manga, chapter, source)

            if (chapterLocation != null && isDownloaded) {
                when (chapterLocation) {
                    is ChapterLocation.Directory -> {
                        chapterLocation.dir.findFile(OCR_CACHE_FILE)?.delete()
                    }
                    is ChapterLocation.Cbz -> {
                        getSidecarFile(chapterLocation.file)?.delete()
                    }
                }
            }

            getInternalCacheFile(manga, chapter, source).delete()
        }
    }

    /**
     * Delete all OCR data for a manga.
     */
    suspend fun deleteOcrForManga(
        manga: Manga,
        source: Source,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val mangaDir = downloadProvider.findMangaDir(manga.title, source)
            mangaDir?.listFiles()?.forEach { chapterEntry ->
                when {
                    chapterEntry.isDirectory -> {
                        chapterEntry.findFile(OCR_CACHE_FILE)?.delete()
                    }
                    chapterEntry.name?.endsWith(".cbz") == true -> {
                        chapterEntry.parentFile?.findFile("${chapterEntry.name}$OCR_SIDECAR_SUFFIX")?.delete()
                    }
                }
            }

            val internalDir = java.io.File(getInternalOcrDir(), source.id.toString())
            val mangaCacheDir = java.io.File(internalDir, manga.id.toString())
            mangaCacheDir.deleteRecursively()
        }
    }

    /**
     * Get total OCR cache size from internal storage.
     */
    suspend fun getReadableSize(): String = withContext(Dispatchers.IO) {
        var totalSize = 0L

        val internalDir = getInternalOcrDir()
        internalDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
            }
        }

        Formatter.formatFileSize(context, totalSize)
    }

    /**
     * Clear all OCR cache from internal storage.
     */
    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            getInternalOcrDir().deleteRecursively()
        }
    }

    // ========== Private helpers ==========

    private fun isChapterDownloaded(manga: Manga, chapter: Chapter, source: Source): Boolean {
        return downloadManager.isChapterDownloaded(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            chapterUrl = chapter.url,
            mangaTitle = manga.title,
            sourceId = source.id,
        )
    }

    private sealed class ChapterLocation {
        data class Directory(val dir: UniFile) : ChapterLocation()
        data class Cbz(val file: UniFile) : ChapterLocation()
    }

    private fun findChapterLocation(manga: Manga, chapter: Chapter, source: Source): ChapterLocation? {
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        ) ?: return null

        return when {
            chapterDir.isDirectory -> ChapterLocation.Directory(chapterDir)
            chapterDir.name?.endsWith(".cbz") == true -> ChapterLocation.Cbz(chapterDir)
            else -> {
                logcat(LogPriority.WARN) { "OcrCache: Unknown chapter format: ${chapterDir.name}" }
                null
            }
        }
    }

    /**
     * Save OCR data to a directory-based chapter.
     */
    private fun saveToDirectory(dir: UniFile, pageIndex: Int, blocks: List<OcrTextBlock>, language: String) {
        val cacheFile = dir.findFile(OCR_CACHE_FILE) ?: dir.createFile(OCR_CACHE_FILE)
        if (cacheFile == null) {
            logcat(LogPriority.ERROR) { "OcrCache: Failed to create cache file in directory" }
            return
        }

        val chapterData = try {
            val content = cacheFile.openInputStream().bufferedReader().use { it.readText() }
            if (content.isBlank()) {
                OcrChapterData(pages = emptyMap(), version = CURRENT_VERSION)
            } else {
                json.decodeFromString<OcrChapterData>(content)
            }
        } catch (e: Exception) {
            OcrChapterData(pages = emptyMap(), version = CURRENT_VERSION)
        }

        val updatedPages = chapterData.pages.toMutableMap()
        updatedPages[pageIndex] = OcrPageData(
            blocks = blocks.map { it.toBlockData() },
            language = language,
            version = CURRENT_VERSION,
        )

        val newData = chapterData.copy(pages = updatedPages)
        cacheFile.openOutputStream().bufferedWriter().use {
            it.write(json.encodeToString(newData))
        }
    }

    /**
     * Load OCR data from a directory-based chapter.
     */
    private fun loadFromDirectory(dir: UniFile, pageIndex: Int): List<OcrTextBlock>? {
        val cacheFile = dir.findFile(OCR_CACHE_FILE) ?: return null

        return try {
            val content = cacheFile.openInputStream().bufferedReader().use { it.readText() }
            if (content.isBlank()) {
                logcat(LogPriority.WARN) { "OcrCache: Directory cache file is empty" }
                return null
            }
            val chapterData = json.decodeFromString<OcrChapterData>(content)
            chapterData.pages[pageIndex]?.blocks?.map { it.toTextBlock() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OcrCache: Failed to load from directory" }
            null
        }
    }

    /**
     * Get or create the sidecar file for a CBZ.
     * Sidecar file: Chapter.cbz -> Chapter.cbz.ocr.json
     */
    private fun getSidecarFile(cbzFile: UniFile): UniFile? {
        val parent = cbzFile.parentFile ?: return null
        val sidecarName = "${cbzFile.name}$OCR_SIDECAR_SUFFIX"
        return parent.findFile(sidecarName) ?: parent.createFile(sidecarName)
    }

    /**
     * Save OCR data to a CBZ sidecar file.
     */
    private fun saveToCbz(cbzFile: UniFile, pageIndex: Int, blocks: List<OcrTextBlock>, language: String) {
        val sidecarFile = getSidecarFile(cbzFile)
        if (sidecarFile == null) {
            logcat(LogPriority.ERROR) { "OcrCache: Failed to create sidecar file for CBZ" }
            return
        }

        val chapterData = try {
            val content = sidecarFile.openInputStream().bufferedReader().use { it.readText() }
            if (content.isBlank()) {
                OcrChapterData(pages = emptyMap(), version = CURRENT_VERSION)
            } else {
                json.decodeFromString<OcrChapterData>(content)
            }
        } catch (e: Exception) {
            OcrChapterData(pages = emptyMap(), version = CURRENT_VERSION)
        }

        val updatedPages = chapterData.pages.toMutableMap()
        updatedPages[pageIndex] = OcrPageData(
            blocks = blocks.map { it.toBlockData() },
            language = language,
            version = CURRENT_VERSION,
        )

        val newData = chapterData.copy(pages = updatedPages)
        sidecarFile.openOutputStream().bufferedWriter().use {
            it.write(json.encodeToString(newData))
        }
    }

    /**
     * Load OCR data from a CBZ sidecar file.
     */
    private fun loadFromCbz(cbzFile: UniFile, pageIndex: Int): List<OcrTextBlock>? {
        val sidecarFile = cbzFile.parentFile?.findFile("${cbzFile.name}$OCR_SIDECAR_SUFFIX") ?: return null

        return try {
            val content = sidecarFile.openInputStream().bufferedReader().use { it.readText() }
            if (content.isBlank()) {
                logcat(LogPriority.WARN) { "OcrCache: CBZ sidecar file is empty" }
                return null
            }
            val chapterData = json.decodeFromString<OcrChapterData>(content)
            chapterData.pages[pageIndex]?.blocks?.map { it.toTextBlock() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OcrCache: Failed to load from CBZ sidecar" }
            null
        }
    }

    /**
     * Get the internal storage directory for OCR cache.
     */
    private fun getInternalOcrDir(): java.io.File {
        val dir = java.io.File(context.filesDir, INTERNAL_OCR_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get the cache file path for an online chapter.
     * Path: {filesDir}/ocr_cache/{sourceId}/{mangaId}/{chapterId}.json
     */
    private fun getInternalCacheFile(manga: Manga, chapter: Chapter, source: Source): java.io.File {
        val baseDir = getInternalOcrDir()
        val sourceDir = java.io.File(baseDir, source.id.toString())
        val mangaDir = java.io.File(sourceDir, manga.id.toString())
        if (!mangaDir.exists()) {
            mangaDir.mkdirs()
        }
        return java.io.File(mangaDir, "${chapter.id}.json")
    }

    /**
     * Save OCR data to internal storage (for online chapters).
     */
    private fun saveToInternal(
        manga: Manga,
        chapter: Chapter,
        source: Source,
        pageIndex: Int,
        blocks: List<OcrTextBlock>,
        language: String,
    ) {
        val cacheFile = getInternalCacheFile(manga, chapter, source)

        val chapterData = try {
            if (cacheFile.exists()) {
                val content = cacheFile.readText()
                if (content.isBlank()) {
                    OcrChapterData(pages = emptyMap(), version = CURRENT_VERSION)
                } else {
                    json.decodeFromString<OcrChapterData>(content)
                }
            } else {
                OcrChapterData(pages = emptyMap(), version = CURRENT_VERSION)
            }
        } catch (e: Exception) {
            OcrChapterData(pages = emptyMap(), version = CURRENT_VERSION)
        }

        val updatedPages = chapterData.pages.toMutableMap()
        updatedPages[pageIndex] = OcrPageData(
            blocks = blocks.map { it.toBlockData() },
            language = language,
            version = CURRENT_VERSION,
        )

        val newData = chapterData.copy(pages = updatedPages)
        try {
            cacheFile.writeText(json.encodeToString(newData))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OcrCache: Failed to save to internal storage" }
        }
    }

    /**
     * Load OCR data from internal storage (for online chapters).
     */
    private fun loadFromInternal(
        manga: Manga,
        chapter: Chapter,
        source: Source,
        pageIndex: Int,
    ): List<OcrTextBlock>? {
        val cacheFile = getInternalCacheFile(manga, chapter, source)
        if (!cacheFile.exists()) {
            return null
        }

        return try {
            val content = cacheFile.readText()
            if (content.isBlank()) {
                logcat(LogPriority.WARN) { "OcrCache: Internal cache file is empty" }
                return null
            }
            val chapterData = json.decodeFromString<OcrChapterData>(content)
            chapterData.pages[pageIndex]?.blocks?.map { it.toTextBlock() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OcrCache: Failed to load from internal storage" }
            null
        }
    }

    private fun OcrTextBlock.toBlockData() = OcrBlockData(
        xmin = xmin,
        ymin = ymin,
        xmax = xmax,
        ymax = ymax,
        lines = lines,
        vertical = vertical,
    )

    private fun OcrBlockData.toTextBlock() = OcrTextBlock(
        xmin = xmin,
        ymin = ymin,
        xmax = xmax,
        ymax = ymax,
        lines = lines,
        vertical = vertical,
    )

    // Legacy methods for backward compatibility during migration
    @Deprecated("Use new method with Manga, Chapter, Source parameters")
    suspend fun saveOcrBlocks(
        mangaId: Long,
        chapterUrl: String,
        pageIndex: Int,
        blocks: List<OcrTextBlock>,
        language: String,
    ) {
        // Legacy - no-op, will be removed
    }

    @Deprecated("Use new method with Manga, Chapter, Source parameters")
    suspend fun loadOcrBlocks(
        mangaId: Long,
        chapterUrl: String,
        pageIndex: Int,
    ): List<OcrTextBlock>? = null

    @Deprecated("Use new method with Manga, Chapter, Source parameters")
    suspend fun deleteOcrForChapter(mangaId: Long, chapterUrl: String) {
        // Legacy - no-op
    }

    @Deprecated("Use new method with Manga, Chapter, Source parameters")
    suspend fun deleteOcrForManga(mangaId: Long) {
        // Legacy - no-op
    }
}

/**
 * Data class for storing all OCR data for a chapter in a single file.
 */
@Serializable
data class OcrChapterData(
    val pages: Map<Int, OcrPageData>,
    val version: Int = 1,
)