package chimahon.ocr

import android.content.Context
import android.text.format.Formatter
import chimahon.ocr.OcrBlockData
import chimahon.ocr.OcrPageData
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private const val CURRENT_VERSION = 2

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
    private val downloadCache: DownloadCache = Injekt.get(),
    invalidationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()

    companion object {
        private const val OCR_CACHE_FILE = ".ocr_cache.json"
        private const val OCR_SIDECAR_SUFFIX = ".ocr.json"
        private const val TMP_SUFFIX = ".tmp"
        private const val CURRENT_VERSION = 2
        private const val INTERNAL_OCR_DIR = "ocr_cache"
        private const val MEMO_MAX_CHAPTERS = 4
    }

    // ========== In-memory chapter memo ==========
    //
    // The on-disk format stores a whole chapter per file, so a naive per-page read
    // re-resolves the chapter location (SAF IPC round trips) and re-parses every
    // page's blocks just to return one page - O(pages^2) work across a chapter.
    // The memo keeps that parse so every read after the first (including misses
    // for pages with no OCR data yet) is an in-memory map lookup.
    //
    // Coherence model, per storage side:
    //  - Internal storage ({filesDir}/ocr_cache) is app-private and only this
    //    class reads or writes it, so the memoized parse of that side cannot go
    //    stale while the entry exists.
    //  - The download side is NOT single-writer: DownloadManager deletes chapter
    //    directories and .ocr.json sidecars directly, Downloader creates new
    //    chapter directories, and the user can mutate the downloads tree from
    //    outside the app entirely. The memo therefore subscribes to
    //    [DownloadCache.changes] and drops every entry whenever the downloads
    //    index reports a mutation (download completed, chapter/manga deleted,
    //    rename, cache renew). App-initiated changes invalidate immediately;
    //    external changes are picked up when DownloadCache renews - the same
    //    staleness bound the rest of the app accepts for download state.
    //
    // Callers that turn cache state into persisted decisions (the OCR queue's
    // "already processed?" checks) must not trust the memo at all; they use
    // [getCachedPageIndexes], which always re-reads disk. All memo access is
    // guarded by [mutex].

    private data class ChapterKey(val sourceId: Long, val mangaId: Long, val chapterId: Long)

    /**
     * Parsed view of a chapter's OCR cache across both storage locations.
     *
     * [downloadData]/[internalData] are null when the backing file exists but is
     * unreadable, so the save path keeps refusing to overwrite corrupt files.
     */
    private class MemoizedChapter(
        val location: ChapterLocation?,
        val isDownloaded: Boolean,
        val downloadData: OcrChapterData?,
        val internalData: OcrChapterData?,
    )

    private val chapterMemo = object : LinkedHashMap<ChapterKey, MemoizedChapter>(
        MEMO_MAX_CHAPTERS,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ChapterKey, MemoizedChapter>,
        ): Boolean = size > MEMO_MAX_CHAPTERS
    }

    init {
        // Downloaded chapters can be deleted, re-downloaded, or renamed without
        // this class being involved; any change to the downloads index makes the
        // memoized download-side parses untrustworthy. The memo holds at most
        // MEMO_MAX_CHAPTERS entries, so clearing it wholesale is cheap and the
        // next read simply re-parses from disk.
        downloadCache.changes
            .onEach { invalidateMemo() }
            .launchIn(invalidationScope)
    }

    /** Drop every memoized chapter so the next read re-parses from disk. */
    private suspend fun invalidateMemo() {
        mutex.withLock { chapterMemo.clear() }
    }

    private fun memoKey(manga: Manga, chapter: Chapter, source: Source): ChapterKey {
        return ChapterKey(sourceId = source.id, mangaId = manga.id, chapterId = chapter.id)
    }

    private fun emptyChapterData() = OcrChapterData(pages = emptyMap(), version = CURRENT_VERSION)

    /**
     * Get the memoized parse of a chapter's OCR cache, loading and memoizing it
     * on first access. Must be called with [mutex] held.
     */
    private fun getOrLoadChapterLocked(manga: Manga, chapter: Chapter, source: Source): MemoizedChapter {
        val key = memoKey(manga, chapter, source)
        chapterMemo[key]?.let { return it }

        val memoized = loadChapterLocked(manga, chapter, source)
        chapterMemo[key] = memoized
        return memoized
    }

    /**
     * Resolve the chapter location and parse both cache files from disk,
     * ignoring the memo. Must be called with [mutex] held.
     */
    private fun loadChapterLocked(manga: Manga, chapter: Chapter, source: Source): MemoizedChapter {
        val location = findChapterLocation(manga, chapter, source)
        val isDownloaded = isChapterDownloaded(manga, chapter, source)

        val downloadData = if (location != null && isDownloaded) {
            when (location) {
                is ChapterLocation.Directory -> readChapterData(location.dir.findFile(OCR_CACHE_FILE))
                is ChapterLocation.Cbz -> readChapterData(findSidecarFile(location.file))
            }
        } else {
            emptyChapterData()
        }

        val internalFile = getInternalCacheFile(manga, chapter, source)
        val internalData = if (internalFile.exists()) {
            UniFile.fromFile(internalFile)?.let { readOcrData(it) }
        } else {
            emptyChapterData()
        }

        return MemoizedChapter(location, isDownloaded, downloadData, internalData)
    }

    /** Parse a whole cache file: absent file -> empty data, unreadable file -> null. */
    private fun readChapterData(file: UniFile?): OcrChapterData? {
        if (file == null) return emptyChapterData()
        return readOcrData(file)
    }

    /**
     * Atomically write JSON to a file by writing to a temp file first, then renaming.
     * If interrupted mid-write, only the .tmp file is corrupted; the original is preserved.
     *
     * Returns true only when the data actually reached the target file. Callers
     * must treat false as "nothing was written" - in particular, nothing may be
     * recorded as persisted (memo or otherwise) on a false return.
     */
    private fun atomicWrite(targetFile: UniFile, jsonString: String): Boolean {
        val tmpFile = targetFile.parentFile?.createFile("${targetFile.name}$TMP_SUFFIX")
            ?: run {
                logcat(LogPriority.ERROR) { "OcrCache: Failed to create temp file for atomic write" }
                return false
            }
        try {
            tmpFile.openOutputStream().bufferedWriter().use {
                it.write(jsonString)
                it.flush()
            }
            val targetName = targetFile.name
            if (targetName == null) {
                tmpFile.delete()
                logcat(LogPriority.ERROR) { "OcrCache: target file has no name, aborting atomic write" }
                return false
            }
            targetFile.delete()
            if (!tmpFile.renameTo(targetName)) {
                logcat(LogPriority.WARN) { "OcrCache: rename failed, trying fallback" }
                // Fallback: write directly (some storage backends don't support rename)
                targetFile.openOutputStream().bufferedWriter().use {
                    it.write(jsonString)
                    it.flush()
                }
                tmpFile.delete()
            }
            return true
        } catch (e: Exception) {
            tmpFile.delete()
            logcat(LogPriority.ERROR, e) { "OcrCache: atomic write failed" }
            throw e
        }
    }

    /**
     * Read and parse OCR data from a file, returning null if the file is corrupt.
     */
    private fun readOcrData(cacheFile: UniFile): OcrChapterData? {
        return try {
            val content = cacheFile.openInputStream().bufferedReader().use { it.readText() }
            if (content.isBlank()) {
                OcrChapterData(pages = emptyMap(), version = CURRENT_VERSION)
            } else {
                json.decodeFromString<OcrChapterData>(content)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "OcrCache: corrupt cache file, skipping write to avoid data loss" }
            null
        }
    }

    /**
     * Save OCR blocks for a single page to the chapter's OCR cache file.
     *
     * The read-modify-write base comes from the chapter memo when it was built
     * against the same target file, so per-page saves don't re-read and re-parse
     * the whole chapter file; the memo is refreshed with the written data.
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
            val key = memoKey(manga, chapter, source)
            val memo = chapterMemo[key]

            // Always re-resolve the write target so a chapter downloaded or
            // removed mid-session is picked up; the memo is refreshed below.
            val chapterLocation = findChapterLocation(manga, chapter, source)
            val isDownloaded = isChapterDownloaded(manga, chapter, source)

            val newPage = OcrPageData(
                blocks = blocks.map { it.toBlockData() },
                language = language,
                version = CURRENT_VERSION,
            )

            try {
                if (isDownloaded && chapterLocation != null) {
                    // Reuse the memoized parse as the read-modify-write base only
                    // when it was built against this exact target file.
                    val knownData = if (
                        memo != null &&
                        memo.isDownloaded &&
                        memo.location?.identity() == chapterLocation.identity()
                    ) {
                        memo.downloadData
                    } else {
                        null
                    }
                    val written = when (chapterLocation) {
                        is ChapterLocation.Directory ->
                            saveToDirectory(chapterLocation.dir, pageIndex, newPage, knownData)
                        is ChapterLocation.Cbz ->
                            saveToCbz(chapterLocation.file, pageIndex, newPage, knownData)
                    }
                    refreshMemoAfterSave(key, chapterLocation, isDownloaded, written, wroteToDownload = true)
                } else {
                    val written = saveToInternal(manga, chapter, source, pageIndex, newPage, memo?.internalData)
                    refreshMemoAfterSave(key, chapterLocation, isDownloaded, written, wroteToDownload = false)
                }
            } catch (e: Exception) {
                // Disk state is uncertain after a failed write; drop the memo so
                // the next read re-parses whatever is actually on disk.
                chapterMemo.remove(key)
                throw e
            }
        }
    }

    /**
     * Refresh the chapter memo after a save attempt. [written] is the full
     * chapter data now on disk for the written side, or null when the save was
     * aborted. Only an existing memo entry is updated in place; building one
     * from scratch is left to the next read so both sides always come from a
     * real parse.
     */
    private fun refreshMemoAfterSave(
        key: ChapterKey,
        location: ChapterLocation?,
        isDownloaded: Boolean,
        written: OcrChapterData?,
        wroteToDownload: Boolean,
    ) {
        if (written == null) {
            chapterMemo.remove(key)
            return
        }
        val old = chapterMemo[key] ?: return

        chapterMemo[key] = if (wroteToDownload) {
            MemoizedChapter(
                location = location,
                isDownloaded = isDownloaded,
                downloadData = written,
                // The internal cache path is fixed by ids, so its parse stays valid.
                internalData = old.internalData,
            )
        } else {
            // Keep the memoized download-side parse only if it still describes the
            // freshly resolved target; otherwise fall back to the same
            // "not downloaded" default the load path uses.
            val downloadStillValid = old.isDownloaded == isDownloaded &&
                old.location?.identity() == location?.identity()
            MemoizedChapter(
                location = location,
                isDownloaded = isDownloaded,
                downloadData = if (downloadStillValid) old.downloadData else emptyChapterData(),
                internalData = written,
            )
        }
    }

    /**
     * Load OCR blocks for a single page from cache.
     *
     * The first read of a chapter resolves its location and parses each cache
     * file once; subsequent reads (including misses for pages that have no OCR
     * data yet) are served from the in-memory chapter memo.
     */
    suspend fun loadOcrBlocks(
        manga: Manga,
        chapter: Chapter,
        source: Source,
        pageIndex: Int,
    ): List<OcrTextBlock>? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val memo = getOrLoadChapterLocked(manga, chapter, source)
            val pageData = memo.downloadData?.pages?.get(pageIndex)
                ?: memo.internalData?.pages?.get(pageIndex)
            pageData?.blocks?.map { it.toTextBlock() }
        }
    }

    /**
     * The set of page indexes with OCR data on disk right now, from a fresh
     * location resolve and parse that bypasses the memo (the fresh parse
     * re-primes it).
     *
     * The OCR queue uses this for its persisted decisions - skipping
     * already-processed pages and judging a chapter complete - because those
     * must reflect the actual files, never memoized state: the downloads tree
     * can be deleted or replaced without this class being told. One call per
     * chapter replaces the old per-page probes, so the queue stays O(pages)
     * per chapter.
     */
    suspend fun getCachedPageIndexes(
        manga: Manga,
        chapter: Chapter,
        source: Source,
    ): Set<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val fresh = loadChapterLocked(manga, chapter, source)
            chapterMemo[memoKey(manga, chapter, source)] = fresh
            buildSet {
                fresh.downloadData?.pages?.keys?.let(::addAll)
                fresh.internalData?.pages?.keys?.let(::addAll)
            }
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
                    findSidecarFile(chapterLocation.file)?.exists() == true
                }
            }
        } else {
            false
        }

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
            chapterMemo.remove(memoKey(manga, chapter, source))

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
            chapterMemo.keys.removeAll { it.sourceId == source.id && it.mangaId == manga.id }

            val mangaDir = downloadProvider.findMangaDir(manga.ogTitle, source)
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
            chapterMemo.clear()
            getInternalOcrDir().deleteRecursively()
        }
    }

    // ========== Private helpers ==========

    private fun isChapterDownloaded(manga: Manga, chapter: Chapter, source: Source): Boolean {
        return downloadManager.isChapterDownloaded(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            chapterUrl = chapter.url,
            mangaTitle = manga.ogTitle,
            sourceId = source.id,
        )
    }

    private sealed class ChapterLocation {
        data class Directory(val dir: UniFile) : ChapterLocation()
        data class Cbz(val file: UniFile) : ChapterLocation()

        private val target: UniFile
            get() = when (this) {
                is Directory -> dir
                is Cbz -> file
            }

        /**
         * Stable identity of the backing location, used to decide whether a
         * memoized parse still describes the same target file. Prefers the
         * plain file path (cheap for raw files, IPC-free for SAF documents)
         * and falls back to the URI string.
         */
        fun identity(): String = target.filePath ?: target.uri.toString()
    }

    private fun findChapterLocation(manga: Manga, chapter: Chapter, source: Source): ChapterLocation? {
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.ogTitle,
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
     * Returns the full chapter data now on disk, or null if the save was aborted.
     */
    private fun saveToDirectory(
        dir: UniFile,
        pageIndex: Int,
        newPage: OcrPageData,
        knownData: OcrChapterData?,
    ): OcrChapterData? {
        val cacheFile = dir.findFile(OCR_CACHE_FILE) ?: dir.createFile(OCR_CACHE_FILE)
        if (cacheFile == null) {
            logcat(LogPriority.ERROR) { "OcrCache: Failed to create cache file in directory" }
            return null
        }

        val chapterData = knownData ?: readOcrData(cacheFile) ?: return null

        val updatedPages = chapterData.pages.toMutableMap()
        updatedPages[pageIndex] = newPage

        val newData = chapterData.copy(pages = updatedPages)
        if (!atomicWrite(cacheFile, json.encodeToString(newData))) return null
        return newData
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

    private fun findSidecarFile(cbzFile: UniFile): UniFile? {
        val parent = cbzFile.parentFile ?: return null
        return parent.findFile("${cbzFile.name}$OCR_SIDECAR_SUFFIX")
    }

    /**
     * Save OCR data to a CBZ sidecar file.
     * Returns the full chapter data now on disk, or null if the save was aborted.
     */
    private fun saveToCbz(
        cbzFile: UniFile,
        pageIndex: Int,
        newPage: OcrPageData,
        knownData: OcrChapterData?,
    ): OcrChapterData? {
        val sidecarFile = getSidecarFile(cbzFile)
        if (sidecarFile == null) {
            logcat(LogPriority.ERROR) { "OcrCache: Failed to create sidecar file for CBZ" }
            return null
        }

        val chapterData = knownData ?: readOcrData(sidecarFile) ?: return null

        val updatedPages = chapterData.pages.toMutableMap()
        updatedPages[pageIndex] = newPage

        val newData = chapterData.copy(pages = updatedPages)
        if (!atomicWrite(sidecarFile, json.encodeToString(newData))) return null
        return newData
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
     * Returns the full chapter data now on disk, or null if the save failed.
     */
    private fun saveToInternal(
        manga: Manga,
        chapter: Chapter,
        source: Source,
        pageIndex: Int,
        newPage: OcrPageData,
        knownData: OcrChapterData?,
    ): OcrChapterData? {
        val cacheFile = getInternalCacheFile(manga, chapter, source)

        val chapterData = knownData ?: if (cacheFile.exists()) {
            val uniFile = UniFile.fromFile(cacheFile) ?: return null
            readOcrData(uniFile) ?: return null
        } else {
            emptyChapterData()
        }

        val updatedPages = chapterData.pages.toMutableMap()
        updatedPages[pageIndex] = newPage

        val newData = chapterData.copy(pages = updatedPages)
        return try {
            cacheFile.writeText(json.encodeToString(newData))
            newData
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OcrCache: Failed to save to internal storage" }
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
        lineGeometries = lineGeometries?.map { lg ->
            chimahon.ocr.OcrLineGeometry(lg.xmin, lg.ymin, lg.xmax, lg.ymax, lg.rotation)
        },
        language = language,
    )

    private fun OcrBlockData.toTextBlock() = OcrTextBlock(
        xmin = xmin,
        ymin = ymin,
        xmax = xmax,
        ymax = ymax,
        lines = lines,
        vertical = vertical,
        lineGeometries = lineGeometries?.map { lg ->
            chimahon.ocr.OcrLineGeometry(lg.xmin, lg.ymin, lg.xmax, lg.ymax, lg.rotation)
        },
        language = language,
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
    val version: Int = 2,
)
