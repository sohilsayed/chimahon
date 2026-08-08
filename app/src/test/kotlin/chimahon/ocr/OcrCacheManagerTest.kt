package chimahon.ocr

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import java.io.File

class OcrCacheManagerTest {

    @TempDir
    lateinit var tempDir: File

    private val json = Json { ignoreUnknownKeys = true }

    /** Stands in for [DownloadCache.changes]; tests emit to simulate downloads-index mutations. */
    private val downloadChanges = MutableSharedFlow<Unit>()

    private val manga = mockk<Manga>(relaxed = true) {
        every { id } returns 2L
        every { ogTitle } returns "Manga"
    }
    private val source = mockk<Source>(relaxed = true) {
        every { id } returns 1L
    }

    private fun chapter(chapterId: Long) = mockk<Chapter>(relaxed = true) {
        every { id } returns chapterId
        every { name } returns "Chapter $chapterId"
        every { scanlator } returns null
        every { url } returns "/chapter/$chapterId"
    }

    private fun newManager(
        downloadProvider: DownloadProvider = mockk {
            every { findChapterDir(any(), any(), any(), any(), any()) } returns null
        },
        downloadManager: DownloadManager = mockk {
            every { isChapterDownloaded(any(), any(), any(), any(), any(), any()) } returns false
        },
    ): OcrCacheManager {
        val context = mockk<Context> {
            every { filesDir } returns tempDir
        }
        val downloadCache = mockk<DownloadCache> {
            every { changes } returns downloadChanges
        }
        return OcrCacheManager(
            context = context,
            json = json,
            downloadManager = downloadManager,
            downloadProvider = downloadProvider,
            downloadCache = downloadCache,
            // Unconfined so downloadChanges.emit() runs the invalidation collector
            // inline, keeping the tests deterministic.
            invalidationScope = CoroutineScope(Dispatchers.Unconfined),
        )
    }

    private fun block(text: String) = OcrTextBlock(
        xmin = 0.1f,
        ymin = 0.2f,
        xmax = 0.3f,
        ymax = 0.4f,
        lines = listOf(text),
        vertical = true,
    )

    private fun internalCacheFile(chapterId: Long): File {
        return File(tempDir, "ocr_cache/1/2/$chapterId.json")
    }

    // ========== Internal storage (app-private, single-writer) ==========

    @Test
    fun `internal-storage reads after the first are served from the memo without touching disk`() = runTest {
        val chapter = chapter(3L)

        // Build the on-disk fixture with a separate manager so the manager under
        // test starts with a cold memo.
        newManager().apply {
            saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("page0")), language = "ja")
            saveOcrBlocks(manga, chapter, source, pageIndex = 1, blocks = listOf(block("page1")), language = "ja")
        }

        val downloadProvider = mockk<DownloadProvider> {
            every { findChapterDir(any(), any(), any(), any(), any()) } returns null
        }
        val manager = newManager(downloadProvider)

        assertEquals(listOf(block("page0")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // Deleting the app-private backing file proves page 1 is answered from the
        // memo, with no disk read. Internal storage has no writer other than
        // OcrCacheManager itself, so this staleness cannot occur in production;
        // the download side, which can change underneath us, is covered by the
        // invalidation tests below.
        internalCacheFile(3L).delete()
        assertEquals(listOf(block("page1")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 1))
        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 2))

        // The chapter location was resolved once for the whole chapter, not per page.
        verify(exactly = 1) { downloadProvider.findChapterDir(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `save refreshes the memo and persists to disk`() = runTest {
        val chapter = chapter(4L)
        val manager = newManager()

        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("v1")), language = "ja")
        assertEquals(listOf(block("v1")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // Overwrite the page; the memoized entry must serve the new data.
        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("v2")), language = "ja")
        assertEquals(listOf(block("v2")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // A fresh manager (cold memo) must read the same data back from disk.
        assertEquals(listOf(block("v2")), newManager().loadOcrBlocks(manga, chapter, source, pageIndex = 0))
    }

    @Test
    fun `deleteOcrForChapter invalidates the memo`() = runTest {
        val chapter = chapter(5L)
        val manager = newManager()

        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("page0")), language = "ja")
        assertEquals(listOf(block("page0")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        manager.deleteOcrForChapter(manga, chapter, source)
        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))
    }

    @Test
    fun `corrupt cache file is not clobbered by a save`() = runTest {
        val chapter = chapter(6L)
        val corruptContent = "not json {{{"
        internalCacheFile(6L).apply {
            parentFile?.mkdirs()
            writeText(corruptContent)
        }

        val manager = newManager()
        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("page0")), language = "ja")

        // The save must abort rather than overwrite the unreadable file.
        assertEquals(corruptContent, internalCacheFile(6L).readText())
        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))
    }

    @Test
    fun `memo evicts least recently used chapters`() = runTest {
        val downloadProvider = mockk<DownloadProvider> {
            every { findChapterDir(any(), any(), any(), any(), any()) } returns null
        }
        val manager = newManager(downloadProvider)

        // Load 5 chapters: one more than the memo holds, evicting the first.
        (1L..5L).forEach { manager.loadOcrBlocks(manga, chapter(it), source, pageIndex = 0) }
        verify(exactly = 5) { downloadProvider.findChapterDir(any(), any(), any(), any(), any()) }

        // Chapter 1 was evicted, so it resolves again; chapter 5 is still memoized.
        manager.loadOcrBlocks(manga, chapter(1L), source, pageIndex = 0)
        verify(exactly = 6) { downloadProvider.findChapterDir(any(), any(), any(), any(), any()) }
        manager.loadOcrBlocks(manga, chapter(5L), source, pageIndex = 0)
        verify(exactly = 6) { downloadProvider.findChapterDir(any(), any(), any(), any(), any()) }
    }

    // ========== Downloaded chapters (directory and CBZ sidecar) ==========

    private fun downloadedManager(
        findChapterDir: () -> UniFile?,
        isDownloaded: () -> Boolean,
        downloadProvider: DownloadProvider = mockk {
            every { findChapterDir(any(), any(), any(), any(), any()) } answers { findChapterDir() }
        },
    ): Pair<OcrCacheManager, DownloadProvider> {
        val downloadManager = mockk<DownloadManager> {
            every { isChapterDownloaded(any(), any(), any(), any(), any(), any()) } answers { isDownloaded() }
        }
        return newManager(downloadProvider, downloadManager) to downloadProvider
    }

    @Test
    fun `downloaded directory chapter saves to and reads from the chapter dir`() = runTest {
        val chapter = chapter(7L)
        val chapterDirFile = File(tempDir, "downloads/Manga/Chapter 7").apply { mkdirs() }
        val chapterDir = UniFile.fromFile(chapterDirFile)!!
        val (manager, downloadProvider) = downloadedManager({ chapterDir }, { true })

        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("dl0")), language = "ja")

        // The write landed in the chapter directory, not internal storage.
        assertTrue(File(chapterDirFile, ".ocr_cache.json").exists())
        assertFalse(internalCacheFile(7L).exists())

        assertEquals(listOf(block("dl0")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // A second save reuses the memoized parse as its read-modify-write base;
        // a second read is a memo hit. Resolutions: save, load, save.
        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 1, blocks = listOf(block("dl1")), language = "ja")
        assertEquals(listOf(block("dl1")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 1))
        verify(exactly = 3) { downloadProvider.findChapterDir(any(), any(), any(), any(), any()) }

        // A cold manager reads both pages back from the file itself.
        val (coldManager, _) = downloadedManager({ chapterDir }, { true })
        assertEquals(listOf(block("dl0")), coldManager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))
        assertEquals(listOf(block("dl1")), coldManager.loadOcrBlocks(manga, chapter, source, pageIndex = 1))
    }

    @Test
    fun `cbz chapter uses the sidecar file and never clobbers a corrupt sidecar`() = runTest {
        val chapter = chapter(9L)
        val mangaDirFile = File(tempDir, "downloads/Manga").apply { mkdirs() }
        File(mangaDirFile, "Chapter 9.cbz").createNewFile()
        val cbz = UniFile.fromFile(mangaDirFile)!!.findFile("Chapter 9.cbz")!!
        val (manager, _) = downloadedManager({ cbz }, { true })

        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("cbz0")), language = "ja")

        val sidecar = File(mangaDirFile, "Chapter 9.cbz.ocr.json")
        assertTrue(sidecar.exists())
        assertEquals(listOf(block("cbz0")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // Corrupt the sidecar behind the manager's back and invalidate: reads
        // fail soft and a save must abort rather than overwrite the file.
        val corruptContent = "not json {{{"
        sidecar.writeText(corruptContent)
        downloadChanges.emit(Unit)

        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))
        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 1, blocks = listOf(block("cbz1")), language = "ja")
        assertEquals(corruptContent, sidecar.readText())
    }

    @Test
    fun `download cache change invalidates the memo after chapter deletion and re-download`() = runTest {
        val chapter = chapter(10L)
        val chapterDirFile = File(tempDir, "downloads/Manga/Chapter 10")
        chapterDirFile.mkdirs()
        var currentDir: UniFile? = UniFile.fromFile(chapterDirFile)
        var downloaded = true
        val (manager, _) = downloadedManager({ currentDir }, { downloaded })

        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("page0")), language = "ja")
        assertEquals(listOf(block("page0")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // DownloadManager deletes the chapter dir (and the OCR file inside it)
        // without telling OcrCacheManager. Until the downloads index reports the
        // change the memo may still answer - that window is the accepted bound...
        chapterDirFile.deleteRecursively()
        currentDir = null
        downloaded = false
        assertEquals(listOf(block("page0")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // ...and the DownloadCache change event closes it.
        downloadChanges.emit(Unit)
        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // Re-download: fresh chapter dir with no OCR data. No ghost of the old
        // parse may survive.
        chapterDirFile.mkdirs()
        currentDir = UniFile.fromFile(chapterDirFile)
        downloaded = true
        downloadChanges.emit(Unit)
        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))
        assertEquals(emptySet<Int>(), manager.getCachedPageIndexes(manga, chapter, source))
    }

    @Test
    fun `getCachedPageIndexes bypasses the memo and reflects disk truth`() = runTest {
        val chapter = chapter(12L)
        val chapterDirFile = File(tempDir, "downloads/Manga/Chapter 12")
        chapterDirFile.mkdirs()
        var currentDir: UniFile? = UniFile.fromFile(chapterDirFile)
        var downloaded = true
        val (manager, _) = downloadedManager({ currentDir }, { downloaded })

        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("page0")), language = "ja")
        assertEquals(listOf(block("page0")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // The chapter dir vanishes with NO invalidation event (mid-window). The
        // OCR queue's completeness check must still see the truth: if it trusted
        // the memo it would skip every page and mark the chapter OCR-ready with
        // no file on disk.
        chapterDirFile.deleteRecursively()
        currentDir = null
        downloaded = false
        assertEquals(emptySet<Int>(), manager.getCachedPageIndexes(manga, chapter, source))

        // The fresh parse re-primed the memo, so the reader path agrees now too.
        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))
    }

    @Test
    fun `save discards the memoized base when the chapter location changes`() = runTest {
        val chapter = chapter(11L)
        val dirA = File(tempDir, "downloads/Manga/Chapter 11").apply { mkdirs() }
        val dirB = File(tempDir, "downloads-new/Manga/Chapter 11").apply { mkdirs() }
        var currentDir = dirA
        val (manager, _) = downloadedManager({ UniFile.fromFile(currentDir) }, { true })

        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("a0")), language = "ja")
        assertEquals(listOf(block("a0")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))

        // The chapter now resolves to a different location (e.g. storage moved).
        // The save must base itself on the new file, not the memoized parse of
        // the old one, so page 0 of the old location must not leak into it.
        currentDir = dirB
        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 1, blocks = listOf(block("b1")), language = "ja")

        val dataB = json.decodeFromString<OcrChapterData>(File(dirB, ".ocr_cache.json").readText())
        assertEquals(setOf(1), dataB.pages.keys)

        // The refreshed memo describes the new location.
        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))
        assertEquals(listOf(block("b1")), manager.loadOcrBlocks(manga, chapter, source, pageIndex = 1))
    }

    @Test
    fun `a save whose atomic write cannot start is not recorded as persisted`() = runTest {
        val chapter = chapter(8L)
        // A directory chapter whose cache file cannot get a temp sibling: the
        // atomic write reports failure instead of silently doing nothing.
        val cacheFile = mockk<UniFile> {
            every { openInputStream() } answers { "".byteInputStream() }
            every { name } returns ".ocr_cache.json"
            every { parentFile } returns mockk {
                every { createFile(any()) } returns null
            }
        }
        val chapterDir = mockk<UniFile> {
            every { isDirectory } returns true
            every { filePath } returns "/downloads/Manga/Chapter 8"
            every { findFile(".ocr_cache.json") } returns cacheFile
        }
        val (manager, _) = downloadedManager({ chapterDir }, { true })

        manager.saveOcrBlocks(manga, chapter, source, pageIndex = 0, blocks = listOf(block("page0")), language = "ja")

        // Nothing reached disk, so nothing may be served from memory either:
        // otherwise the page would read back fine until the process restarts,
        // and the OCR queue would skip it forever after.
        assertNull(manager.loadOcrBlocks(manga, chapter, source, pageIndex = 0))
        assertEquals(emptySet<Int>(), manager.getCachedPageIndexes(manga, chapter, source))
        assertFalse(internalCacheFile(8L).exists())
    }
}
