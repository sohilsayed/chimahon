package eu.kanade.tachiyomi.data.ocr

import android.content.Context
import chimahon.ocr.LensClient
import chimahon.ocr.OcrCacheManager
import chimahon.ocr.OcrLanguage
import chimahon.ocr.OcrTextBlock as ChimahonOcrTextBlock
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import mihon.core.archive.archiveReader
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class OcrManager(
    private val context: Context,
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val chapterRepository: ChapterRepository = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val ocrStore: OcrStore = Injekt.get(),
) {
    private val ocrCacheManager: OcrCacheManager = Injekt.get()
    private val lensClient: LensClient = Injekt.get()

    private val downloadManager: DownloadManager by lazy { Injekt.get() }

    private suspend fun <T> retryWithBackoff(
        times: Int = 3,
        initialDelayMs: Long = 1000,
        maxDelayMs: Long = 10000,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelayMs
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.WARN) { "Retry failed, retrying in ${currentDelay}ms: ${e.message}" }
            }
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
        return block()
    }
    private val downloadProvider: DownloadProvider by lazy { Injekt.get() }

    private val _queueState = MutableStateFlow<List<OcrQueueItem>>(emptyList())
    val queueState: StateFlow<List<OcrQueueItem>> = _queueState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()

    init {
        scope.launch {
            ocrStore.normalizeForRestore()
            promoteDownloadedWaitingTasks()
            refreshQueueState()
            if (ocrStore.hasRunnableTasks()) {
                OcrJob.start(context)
            }
        }
    }

    val isRunning: Boolean
        get() = _queueState.value.any { it.status.isActionable() }

    fun isChapterRunning(chapterId: Long): Boolean {
        return _queueState.value.any {
            it.chapter.id == chapterId && it.status == OcrQueueStatus.PROCESSING
        }
    }

    fun isChapterQueued(chapterId: Long): Boolean = ocrStore.get(chapterId) != null

    fun queueChapters(manga: Manga, chapters: List<Chapter>, waitForDownload: Boolean = true) {
        if (chapters.isEmpty()) return
        val changed = ocrStore.enqueue(
            chapters.map { chapter ->
                OcrEnqueueRequest(
                    mangaId = manga.id,
                    chapterId = chapter.id,
                    waitForDownload = waitForDownload,
                )
            },
        )

        scope.launch {
            _queueState.update { current ->
                val existingIds = current.map { it.chapter.id }.toSet()
                val queued = chapters
                    .filterNot { it.id in existingIds }
                    .map { chapter ->
                        OcrQueueItem(
                            manga = manga,
                            chapter = chapter,
                            progress = 0f,
                            currentPage = 0,
                            totalPages = 0,
                            status = if (waitForDownload) OcrQueueStatus.WAITING_DOWNLOAD else OcrQueueStatus.PENDING,
                        )
                    }
                current + queued
            }
            refreshQueueState()
        }

        if (changed || ocrStore.hasRunnableTasks()) {
            OcrJob.start(context)
        }
    }

    fun markChapterReadyForOcr(manga: Manga, chapter: Chapter): Boolean {
        val current = ocrStore.get(chapter.id) ?: return false
        if (current.status != OcrQueueStatus.WAITING_DOWNLOAD) return false

        ocrStore.save(
            current.copy(
                mangaId = manga.id,
                waitForDownload = false,
                status = OcrQueueStatus.PENDING,
                currentPage = 0,
                totalPages = 0,
            ),
        )

        scope.launch {
            refreshQueueState()
        }
        OcrJob.start(context)
        return true
    }

    fun markChapterDownloadFailed(chapterId: Long): Boolean {
        val current = ocrStore.get(chapterId) ?: return false
        if (current.status != OcrQueueStatus.WAITING_DOWNLOAD) return false

        ocrStore.save(
            current.copy(
                waitForDownload = false,
                status = OcrQueueStatus.ERROR,
                currentPage = 0,
                totalPages = 0,
            ),
        )

        scope.launch {
            refreshQueueState()
        }
        return true
    }

    suspend fun cancelChapter(chapterId: Long) {
        ocrStore.remove(chapterId)
        stateMutex.withLock {
            _queueState.update { current ->
                current.filterNot { it.chapter.id == chapterId }
            }
        }
    }

    suspend fun deleteOcrForChapter(manga: Manga, chapter: Chapter) {
        val source = sourceManager.get(manga.source) ?: return
        ocrCacheManager.deleteOcrForChapter(manga, chapter, source)
        chapterRepository.update(ChapterUpdate(id = chapter.id, isOcrReady = false))
    }

    suspend fun deleteOcrForManga(manga: Manga) {
        val source = sourceManager.get(manga.source) ?: return
        ocrCacheManager.deleteOcrForManga(manga, source)
        // Note: Individual chapter isOcrReady flags will be stale but will be
        // corrected when chapters are re-fetched or when OCR is run again
    }

    suspend fun getStorageSize(): String = ocrCacheManager.getReadableSize()

    suspend fun clearCache() {
        ocrCacheManager.clear()
    }

    fun clearPendingChapters(chapterIds: Collection<Long>) {
        if (chapterIds.isEmpty()) return
        ocrStore.removeAll(chapterIds)
        scope.launch {
            refreshQueueState()
        }
    }

    suspend fun runPendingQueue(stopRequested: () -> Boolean) {
        while (!stopRequested()) {
            val task = ocrStore.getAll().firstOrNull { it.status.isRunnable() } ?: return
            val manga = mangaRepository.getMangaById(task.mangaId)
            val chapter = chapterRepository.getChapterById(task.chapterId)

            if (manga == null || chapter == null) {
                ocrStore.remove(task.chapterId)
                refreshQueueState()
                continue
            }

            val result = try {
                processTask(task, manga, chapter, stopRequested)
            } catch (e: CancellationException) {
                if (ocrStore.get(task.chapterId) != null) {
                    updateStoredTask(task.chapterId) { current ->
                        current.copy(status = OcrQueueStatus.PENDING)
                    }
                }
                refreshQueueState()
                throw e
            }

            when (result) {
                OcrTaskResult.SUCCESS,
                OcrTaskResult.CANCELLED -> {
                    ocrStore.remove(task.chapterId)
                }
                OcrTaskResult.ERROR -> Unit
                OcrTaskResult.STOPPED -> return
            }
            refreshQueueState()
        }
    }

    private suspend fun promoteDownloadedWaitingTasks() {
        ocrStore.getAll()
            .filter { it.status == OcrQueueStatus.WAITING_DOWNLOAD }
            .forEach { task ->
                val manga = mangaRepository.getMangaById(task.mangaId) ?: return@forEach
                val chapter = chapterRepository.getChapterById(task.chapterId) ?: return@forEach
                val isDownloaded = downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    chapter.url,
                    manga.title,
                    manga.source,
                )

                if (isDownloaded) {
                    ocrStore.save(
                        task.copy(
                            waitForDownload = false,
                            status = OcrQueueStatus.PENDING,
                            currentPage = 0,
                            totalPages = 0,
                        ),
                    )
                }
            }
    }

    private suspend fun refreshQueueState() {
        stateMutex.withLock {
            val tasks = ocrStore.getAll()
            val invalidChapterIds = mutableListOf<Long>()
            val mangaCache = mutableMapOf<Long, Manga?>()
            val items = tasks.mapNotNull { task ->
                val manga = mangaCache.getOrPut(task.mangaId) { mangaRepository.getMangaById(task.mangaId) }
                val chapter = chapterRepository.getChapterById(task.chapterId)

                if (manga == null || chapter == null) {
                    invalidChapterIds += task.chapterId
                    return@mapNotNull null
                }

                OcrQueueItem(
                    manga = manga,
                    chapter = chapter,
                    progress = if (task.totalPages > 0) task.currentPage.toFloat() / task.totalPages else 0f,
                    currentPage = task.currentPage,
                    totalPages = task.totalPages,
                    status = task.status,
                )
            }

            if (invalidChapterIds.isNotEmpty()) {
                ocrStore.removeAll(invalidChapterIds)
            }

            _queueState.value = items
        }
    }

    private fun updateStoredTask(chapterId: Long, transform: (OcrTask) -> OcrTask) {
        val updated = ocrStore.update(chapterId, transform) ?: return
        _queueState.update { current ->
            current.map { item ->
                if (item.chapter.id != chapterId) {
                    item
                } else {
                    item.copy(
                        currentPage = updated.currentPage,
                        totalPages = updated.totalPages,
                        progress = if (updated.totalPages > 0) updated.currentPage.toFloat() / updated.totalPages else 0f,
                        status = updated.status,
                    )
                }
            }
        }
    }

    private suspend fun processTask(
        _task: OcrTask,
        manga: Manga,
        chapter: Chapter,
        stopRequested: () -> Boolean,
    ): OcrTaskResult {
        val chapterId = chapter.id
        logcat { "OcrManager: processing chapter ${chapter.name}" }

        try {
            val source = sourceManager.get(manga.source) as? HttpSource
            if (source == null) {
                logcat(LogPriority.WARN) { "OcrManager: source not found for manga ${manga.id}" }
                chapterRepository.update(ChapterUpdate(id = chapterId, isOcrReady = false))
                updateStoredTask(chapterId) { it.copy(status = OcrQueueStatus.ERROR) }
                return OcrTaskResult.ERROR
            }

            if (stopRequested()) return OcrTaskResult.STOPPED
            if (ocrStore.get(chapterId) == null) return OcrTaskResult.CANCELLED

            val isDownloaded = downloadManager.isChapterDownloaded(
                chapter.name,
                chapter.scanlator,
                chapter.url,
                manga.title,
                manga.source,
            )

            if (!isDownloaded) {
                logcat(LogPriority.WARN) { "OcrManager: chapter not downloaded, skipping OCR" }
                chapterRepository.update(ChapterUpdate(id = chapterId, isOcrReady = false))
                updateStoredTask(chapterId) { it.copy(status = OcrQueueStatus.ERROR) }
                return OcrTaskResult.ERROR
            }

            val imageProvider = getChapterImageProvider(manga, chapter, source)
            if (imageProvider == null) {
                logcat(LogPriority.WARN) { "OcrManager: no images found in downloaded chapter" }
                chapterRepository.update(ChapterUpdate(id = chapterId, isOcrReady = false))
                updateStoredTask(chapterId) { it.copy(status = OcrQueueStatus.ERROR) }
                return OcrTaskResult.ERROR
            }

            var failedPages = 0
            val pageCount = imageProvider.pageCount
            if (pageCount <= 0) {
                imageProvider.close()
                logcat(LogPriority.WARN) { "OcrManager: no pages available for chapter ${chapter.name}" }
                chapterRepository.update(ChapterUpdate(id = chapterId, isOcrReady = false))
                updateStoredTask(chapterId) { it.copy(status = OcrQueueStatus.ERROR) }
                return OcrTaskResult.ERROR
            }

            try {
                updateStoredTask(chapterId) {
                    it.copy(
                        status = OcrQueueStatus.PROCESSING,
                        totalPages = pageCount,
                    )
                }

                for (pageIndex in 0 until pageCount) {
                    if (stopRequested()) {
                        updateStoredTask(chapterId) { current ->
                            current.copy(status = OcrQueueStatus.PENDING)
                        }
                        return OcrTaskResult.STOPPED
                    }
                    if (ocrStore.get(chapterId) == null) {
                        logcat { "OcrManager: cancelled chapter ${chapter.name}" }
                        return OcrTaskResult.CANCELLED
                    }

                    val existingBlocks = ocrCacheManager.loadOcrBlocks(manga, chapter, source, pageIndex)
                    if (existingBlocks != null) {
                        logcat { "OcrManager: page $pageIndex already cached for chapter ${chapter.name}" }
                        updateStoredTask(chapterId) {
                            it.copy(
                                currentPage = pageIndex + 1,
                                totalPages = pageCount,
                                status = OcrQueueStatus.PROCESSING,
                            )
                        }
                        continue
                    }

                    val bytes = try {
                        imageProvider.getPageBytes(pageIndex)
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "OcrManager: failed to read image for page $pageIndex" }
                        failedPages++
                        updateStoredTask(chapterId) {
                            it.copy(currentPage = pageIndex + 1, totalPages = pageCount, status = OcrQueueStatus.PROCESSING)
                        }
                        continue
                    }

                    if (bytes == null) {
                        logcat(LogPriority.WARN) { "OcrManager: null bytes for page $pageIndex" }
                        failedPages++
                        updateStoredTask(chapterId) {
                            it.copy(currentPage = pageIndex + 1, totalPages = pageCount, status = OcrQueueStatus.PROCESSING)
                        }
                        continue
                    }

                    val blocks = try {
                        val result = retryWithBackoff(times = 3) {
                            lensClient.getDebugOcrData(
                                bytes = bytes,
                                language = OcrLanguage.JAPANESE,
                            )
                        }
                        result.mergedResults.mapNotNull { r ->
                            val bbox = r.tightBoundingBox
                            val xmin = bbox.x.toFloat().coerceIn(0f, 1f)
                            val ymin = bbox.y.toFloat().coerceIn(0f, 1f)
                            val xmax = (bbox.x + bbox.width).toFloat().coerceIn(0f, 1f)
                            val ymax = (bbox.y + bbox.height).toFloat().coerceIn(0f, 1f)
                            if (xmax <= xmin || ymax <= ymin) {
                                null
                            } else {
                                OcrTextBlock(
                                    xmin = xmin,
                                    ymin = ymin,
                                    xmax = xmax,
                                    ymax = ymax,
                                    lines = r.text.split("\n").filter { it.isNotBlank() },
                                    vertical = r.forcedOrientation == "vertical",
                                )
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "OcrManager: OCR failed for page $pageIndex after retries" }
                        failedPages++
                        updateStoredTask(chapterId) {
                            it.copy(currentPage = pageIndex + 1, totalPages = pageCount, status = OcrQueueStatus.PROCESSING)
                        }
                        continue
                    }

                    try {
                        ocrCacheManager.saveOcrBlocks(
                            manga = manga,
                            chapter = chapter,
                            source = source,
                            pageIndex = pageIndex,
                            blocks = blocks.map {
                                ChimahonOcrTextBlock(it.xmin, it.ymin, it.xmax, it.ymax, it.lines, it.vertical)
                            },
                            language = OcrLanguage.JAPANESE.bcp47,
                        )
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, e) { "OcrManager: failed to cache OCR for page $pageIndex" }
                        failedPages++
                    }

                    updateStoredTask(chapterId) {
                        it.copy(currentPage = pageIndex + 1, totalPages = pageCount, status = OcrQueueStatus.PROCESSING)
                    }
                }
            } finally {
                imageProvider.close()
            }

            val cachedPageCount = countCachedPages(manga, chapter, source, pageCount)
            if (cachedPageCount == pageCount) {
                chapterRepository.update(ChapterUpdate(id = chapterId, isOcrReady = true))
                logcat { "OcrManager: completed chapter ${chapter.name}" }
                return OcrTaskResult.SUCCESS
            }

            chapterRepository.update(ChapterUpdate(id = chapterId, isOcrReady = false))
            updateStoredTask(chapterId) { it.copy(status = OcrQueueStatus.ERROR) }
            logcat(LogPriority.WARN) {
                "OcrManager: incomplete chapter ${chapter.name} cachedPages=$cachedPageCount failedPages=$failedPages totalPages=$pageCount"
            }
            return OcrTaskResult.ERROR
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OcrManager: failed chapter ${chapter.name}" }
            updateStoredTask(chapterId) { it.copy(status = OcrQueueStatus.ERROR) }
            chapterRepository.update(ChapterUpdate(id = chapterId, isOcrReady = false))
            return OcrTaskResult.ERROR
        }
    }

    private suspend fun countCachedPages(
        manga: Manga,
        chapter: Chapter,
        source: Source,
        pageCount: Int,
    ): Int {
        var cachedPages = 0
        for (pageIndex in 0 until pageCount) {
            if (ocrCacheManager.loadOcrBlocks(manga, chapter, source, pageIndex) != null) {
                cachedPages++
            }
        }
        return cachedPages
    }

    private fun getChapterImageProvider(
        manga: Manga,
        chapter: Chapter,
        source: Source,
    ): ChapterImageProvider? {
        val chapterDir = downloadProvider.findChapterDir(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            manga.title,
            source,
        )

        return when {
            chapterDir == null -> null
            chapterDir.isDirectory -> DirectoryImageProvider(chapterDir)
            chapterDir.name?.endsWith(".cbz") == true -> CbzImageProvider(context, chapterDir)
            else -> null
        }
    }

    data class OcrTextBlock(
        val xmin: Float,
        val ymin: Float,
        val xmax: Float,
        val ymax: Float,
        val lines: List<String>,
        val vertical: Boolean = false,
    )
}

interface ChapterImageProvider : AutoCloseable {
    val pageCount: Int
    fun getPageBytes(pageIndex: Int): ByteArray?
}

class DirectoryImageProvider(private val chapterDir: UniFile) : ChapterImageProvider {
    private val imageFiles: List<UniFile> = chapterDir.listFiles()
        ?.filter { it.isFile && ImageUtil.isImage(it.name) { it.openInputStream() } }
        ?.sortedBy { it.name }
        ?: emptyList()

    override val pageCount: Int = imageFiles.size

    override fun getPageBytes(pageIndex: Int): ByteArray? {
        if (pageIndex !in imageFiles.indices) return null
        return try {
            imageFiles[pageIndex].openInputStream().use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {}
}

class CbzImageProvider(
    private val context: Context,
    private val cbzFile: UniFile,
) : ChapterImageProvider {
    private val archiveReader = cbzFile.archiveReader(context)
    private val imageEntries: List<String>

    init {
        imageEntries = archiveReader.useEntries { entries ->
            entries
                .filter { it.isFile && ImageUtil.isImage(it.name) { archiveReader.getInputStream(it.name)!! } }
                .sortedBy { it.name }
                .map { it.name }
                .toList()
        }
    }

    override val pageCount: Int = imageEntries.size

    override fun getPageBytes(pageIndex: Int): ByteArray? {
        if (pageIndex !in imageEntries.indices) return null
        return try {
            archiveReader.getInputStream(imageEntries[pageIndex])?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        archiveReader.close()
    }
}

private enum class OcrTaskResult {
    SUCCESS,
    ERROR,
    CANCELLED,
    STOPPED,
}

data class OcrQueueItem(
    val manga: Manga,
    val chapter: Chapter,
    val progress: Float,
    val currentPage: Int,
    val totalPages: Int,
    val status: OcrQueueStatus = OcrQueueStatus.PENDING,
)

enum class OcrQueueStatus {
    PENDING,
    WAITING_DOWNLOAD,
    PROCESSING,
    COMPLETED,
    ERROR,
    CANCELLED,
}

suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelayMs: Long = 1000,
    maxDelayMs: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T,
): T {
    var currentDelay = initialDelayMs
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            // Retry failed, will retry shortly
        }
        kotlinx.coroutines.delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
    }
    return block()
}
