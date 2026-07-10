package com.canopus.chimareader.ui.reader

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.Bookmark
import com.canopus.chimareader.data.CustomReaderTheme
import com.canopus.chimareader.data.FileNames
import com.canopus.chimareader.data.FontManager
import com.canopus.chimareader.data.NovelReaderSettings
import com.canopus.chimareader.data.Statistics
import com.canopus.chimareader.data.Theme
import com.canopus.chimareader.data.epub.EpubBook
import com.canopus.chimareader.data.epub.SpineItemType
import com.canopus.chimareader.ttusync.SyncDirection
import com.canopus.chimareader.ttusync.SyncResult
import com.canopus.chimareader.ttusync.TtuSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ─── Commands ─────────────────────────────────────────────────────────────────

sealed interface WebViewCommand {
    data class LoadChapter(val url: String, val progress: Double) : WebViewCommand
    data class JumpToFragment(val fragment: String) : WebViewCommand
    data class ApplySasayakiCues(val cuesJson: String) : WebViewCommand
    data class HighlightSasayakiCue(
        val cueId: String,
        val reveal: Boolean,
        val onProgress: ((Double) -> Unit)? = null,
    ) : WebViewCommand
    data object ClearSasayakiCue : WebViewCommand
    data class UpdateTextColor(val hex: String?) : WebViewCommand
    data class ChangeMode(val continuous: Boolean) : WebViewCommand
    data class ApplySettings(val settings: ReaderSettings) : WebViewCommand
    data class ChangeFocusMode(val focusMode: Boolean) : WebViewCommand
    data class Paginate(val forward: Boolean) : WebViewCommand
    data object ClearSelection : WebViewCommand
    data class HighlightSelection(val charCount: Int) : WebViewCommand
    data class GetSelectionRects(val charCount: Int, val startOffset: Int = 0) : WebViewCommand
}

data class ReaderSettings(
    val fontSize: Double = 18.0,
    val lineHeight: Double = 1.6,
    val characterSpacing: Double = 0.0,
    val paragraphSpacing: Double = 0.0,
    val horizontalPadding: Double = 10.0,
    val verticalPadding: Double = 10.0,
    val selectedFont: String = "System Serif",
    val fontUrl: String? = null, // Custom font file URL for @font-face
    val theme: String = "system", // "light", "dark", "sepia", "system"
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val textColor: Int = 0xFF000000.toInt(),
    val verticalWriting: Boolean = true,
    val justifyText: Boolean = false,
    val avoidPageBreak: Boolean = true,
    val hideFurigana: Boolean = false,
    val layoutAdvanced: Boolean = false,
    val tapZonePercent: Int = 20,
    val continuousMode: Boolean = false,
)

enum class EinkRefreshColor {
    BLACK,
    WHITE,
    WHITE_BLACK,
}

// ─── Bridge ───────────────────────────────────────────────────────────────────

class WebViewBridge {
    var chapterUrl: String? by mutableStateOf(null)
        private set
    var chapterTitle: String? by mutableStateOf(null)
        private set
    var progress: Double by mutableDoubleStateOf(0.0)
        private set
    val pendingCommands = mutableStateListOf<WebViewCommand>()

    fun send(command: WebViewCommand) {
        pendingCommands += command
    }

    fun updateState(url: String, progress: Double, title: String? = null) {
        chapterUrl = url
        chapterTitle = title
        this.progress = progress
    }

    fun updateProgress(progress: Double) {
        this.progress = progress
    }

    fun highlightSasayakiCue(id: String, reveal: Boolean) {
        send(WebViewCommand.HighlightSasayakiCue(id, reveal))
    }

    fun clearSasayakiCue() {
        send(WebViewCommand.ClearSasayakiCue)
    }

    fun paginate(forward: Boolean) {
        send(WebViewCommand.Paginate(forward))
    }
}

// ─── Loader ───────────────────────────────────────────────────────────────────

class ReaderLoaderViewModel(
    context: Context,
    book: BookMetadata,
) {
    var document: EpubBook? = null
        private set

    val rootUrl: File? =
        book.folder?.let { BookStorage.getBookDirectory(context, it) }

    init {
        loadBook(book, context)
    }

    private fun loadBook(book: BookMetadata, context: Context) {
        val root = rootUrl ?: return
        val doc = BookStorage.loadEpub(root)
        val bookCopy = book.copy(lastAccess = System.currentTimeMillis())
        BookStorage.save(bookCopy, root, FileNames.metadata)
        document = doc
    }
}

// ─── Reader ───────────────────────────────────────────────────────────────────

class ReaderViewModel(
    val document: EpubBook,
    val rootUrl: File,
    val settings: NovelReaderSettings,
    private val scope: CoroutineScope,
) {
    var index by mutableIntStateOf(0)
    var currentProgress by mutableDoubleStateOf(0.0)

    // Settings state
    var theme by mutableStateOf(Theme.SYSTEM)
    var fontSize by mutableDoubleStateOf(18.0)
    var lineHeight by mutableDoubleStateOf(1.6)
    var horizontalPadding by mutableDoubleStateOf(10.0)
    var verticalPadding by mutableDoubleStateOf(10.0)
    var selectedFont by mutableStateOf("System")
    var continuousMode by mutableStateOf(false)
    var customBackgroundColor by mutableIntStateOf(0xFFF2E2C9.toInt())
    var customTextColor by mutableIntStateOf(0xFF000000.toInt())
    var customThemes by mutableStateOf<List<CustomReaderTheme>>(emptyList())
    var sasayakiPlayer: SasayakiPlayer? by mutableStateOf(null)
    var verticalWriting by mutableStateOf(true)
    var characterSpacing by mutableDoubleStateOf(0.0)
    var paragraphSpacing by mutableDoubleStateOf(0.0)
    var justifyText by mutableStateOf(false)
    var avoidPageBreak by mutableStateOf(true)
    var hideFurigana by mutableStateOf(false)
    var layoutAdvanced by mutableStateOf(false)
    var tapZonePercent by mutableIntStateOf(20)
    var keepScreenOn by mutableStateOf(false)
    var systemLightSepia by mutableStateOf(false)
    var einkRefreshOnPageTurn by mutableStateOf(false)
    var einkRefreshDurationMillis by mutableIntStateOf(100)
    var einkRefreshPageInterval by mutableIntStateOf(1)
    var einkRefreshColor by mutableStateOf("BLACK")

    private val ttuSyncManager: TtuSyncManager? by lazy {
        try { Injekt.get<TtuSyncManager>() } catch (_: Exception) { null }
    }
    private var syncExportJob: kotlinx.coroutines.Job? = null
    var isSyncing: Boolean = false
    var inactiveSinceMillis: Long? = null

    // Tracks statistics for current reading session
    var totalExploredCharCount by mutableIntStateOf(0)

    val accumulatedCharCounts = androidx.compose.runtime.mutableStateMapOf<Int, Int>()

    val totalCharacters: Int
        get() = accumulatedCharCounts[document.linearSpineItems.size] ?: 0

    val currentCharacter: Int
        get() = totalExploredCharCount

    val currentChapterEndCharacter: Int
        get() = accumulatedCharCounts.getOrDefault(index + 1, 0)

    var fullStatistics = mutableStateListOf<Statistics>()
    private var lastPersistTimeMs = System.currentTimeMillis()
    private var lastSavedChapterIndex = 0
    private var lastSavedProgress = 0.0
    private var lastSavedCharacterCount = 0

    lateinit var statisticsTracker: ReaderStatisticsTracker
    private var trackingLocked = false
    private var appBackgrounded = false

    val bridge = WebViewBridge()
    val chapterCount = document.spine().items.size

    /**
     * Returns true if the current chapter is an image-only page
     */
    val isCurrentChapterImageOnly: Boolean
        get() {
            val spineItem = document.linearSpineItems.getOrNull(index) ?: return false
            return spineItem.type == SpineItemType.IMAGE_ONLY
        }

    /**
     * Gets the image URL for the current chapter if it's image-only
     */
    val currentImageUrl: String?
        get() = document.getImageUrl(index)

    init {
        // Initialize state from settings (blocking first value for initial render)
        runBlocking(Dispatchers.IO) {
            theme = settings.theme.first()
            fontSize = settings.fontSize.first()
            lineHeight = settings.lineHeight.first()
            horizontalPadding = settings.horizontalPadding.first()
            verticalPadding = settings.verticalPadding.first()
            selectedFont = settings.selectedFont.first()
            continuousMode = settings.continuousMode.first()
            customBackgroundColor = settings.customBackgroundColor.first()
            customTextColor = settings.customTextColor.first()
            customThemes = settings.customThemes.first()
            verticalWriting = settings.verticalWriting.first()
            paragraphSpacing = settings.paragraphSpacing.first()
            avoidPageBreak = settings.avoidPageBreak.first()
            hideFurigana = settings.readerHideFurigana.first()
            tapZonePercent = settings.chapterTapZones.first()
            keepScreenOn = settings.keepScreenOn.first()
            systemLightSepia = settings.systemLightSepia.first()
            einkRefreshOnPageTurn = settings.einkRefreshOnPageTurn.first()
            einkRefreshDurationMillis = settings.einkRefreshDurationMillis.first()
            einkRefreshPageInterval = settings.einkRefreshPageInterval.first()
            einkRefreshColor = settings.einkRefreshColor.first()
        }

        val bookmark = BookStorage.loadBookmark(rootUrl)
        index = bookmark?.chapterIndex ?: 0
        currentProgress = bookmark?.progress ?: 0.0
        totalExploredCharCount = calculateExploredCharCount(currentProgress)
        lastSavedChapterIndex = index
        lastSavedProgress = currentProgress
        lastSavedCharacterCount = totalExploredCharCount

        val stats = BookStorage.loadStatistics(rootUrl)
        if (stats != null) {
            fullStatistics.addAll(stats)

            // Migration: Heuristic to detect milliseconds stored as seconds
            val migrated = fullStatistics.map {
                val speed = if (it.readingTime > 0) (it.charactersRead / it.readingTime * 3600) else 10000.0
                if (speed < 500.0 && it.readingTime > 0) {
                    Log.i("ReaderViewModel", "TTSU-STATS: Migrating entry '${it.dateKey}' from MS to Seconds (Speed: $speed)")
                    it.copy(readingTime = it.readingTime / 1000.0)
                } else {
                    it
                }
            }
            fullStatistics.clear()
            // Deduplicate: keep the entry with the latest lastStatisticModified per dateKey
            val deduplicated = migrated.groupBy { it.dateKey }.mapValues { (_, entries) ->
                entries.maxBy { it.lastStatisticModified }
            }.values.toList()
            fullStatistics.addAll(deduplicated)
        }

        statisticsTracker = ReaderStatisticsTracker(
            title = document.title ?: "Unknown",
            initialStatistics = fullStatistics,
            enabled = true,
        )

        scope.launch {
            while (true) {
                delay(1000)
                if (!trackingLocked && !appBackgrounded) {
                    statisticsTracker.update(totalExploredCharCount)
                }
                if (System.currentTimeMillis() - lastPersistTimeMs >= 60000L) {
                    persistToDisk()
                    lastPersistTimeMs = System.currentTimeMillis()
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            val sync = ttuSyncManager
            while (sync != null) {
                val intervalMins = sync.autoSyncIntervalMins
                delay(intervalMins * 60 * 1000L)
                if (sync.isEnabled && sync.autoSyncPeriodic && !trackingLocked && !appBackgrounded) {
                    val metadata = BookStorage.loadMetadata(rootUrl)
                    if (metadata != null) {
                        sync.syncBook(metadata, SyncDirection.AUTO)
                    }
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            var runningTotal = 0
            for (i in 0 until document.linearSpineItems.size) {
                accumulatedCharCounts[i] = runningTotal
                runningTotal += document.getChapterCharacters(i)
            }
            accumulatedCharCounts[document.linearSpineItems.size] = runningTotal

            val toc = getFlattenedToc()
            val chapterStartsList = if (toc.isNotEmpty()) {
                toc.map { entry ->
                    val spineIndex = entry.href?.let { getSpineIndexForHref(it) } ?: 0
                    accumulatedCharCounts[spineIndex] ?: 0
                }
            } else {
                document.linearSpineItems.indices.map { index ->
                    accumulatedCharCounts[index] ?: 0
                }
            }
            val chapterStartsListWithEnd = chapterStartsList + runningTotal

            val metadata = BookStorage.loadMetadata(rootUrl)
            if (metadata != null && metadata.chapterStarts != chapterStartsListWithEnd) {
                BookStorage.saveMetadata(metadata.copy(chapterStarts = chapterStartsListWithEnd), rootUrl)
            }
        }

        getCurrentChapter()?.let { file ->
            val fileUrl = "file://${file.absolutePath.replace("\\", "/")}"
            val chapterTitle = getCurrentChapterTitle()
            bridge.updateState(fileUrl, currentProgress, chapterTitle)
            bridge.send(WebViewCommand.LoadChapter(fileUrl, currentProgress))
        }

        // Start collecting updates from settings flow in the background
        scope.launch {
            settings.theme.collect { theme = it }
        }
        scope.launch {
            settings.fontSize.collect { fontSize = it }
        }
        scope.launch {
            settings.lineHeight.collect { lineHeight = it }
        }
        scope.launch {
            settings.horizontalPadding.collect { horizontalPadding = it }
        }
        scope.launch {
            settings.verticalPadding.collect { verticalPadding = it }
        }
        scope.launch {
            settings.selectedFont.collect { selectedFont = it }
        }
        scope.launch {
            settings.continuousMode.collect { continuousMode = it }
        }
        scope.launch {
            settings.customBackgroundColor.collect { customBackgroundColor = it }
        }
        scope.launch {
            settings.customTextColor.collect { customTextColor = it }
        }
        scope.launch {
            settings.customThemes.collect { customThemes = it }
        }
        scope.launch {
            settings.verticalWriting.collect { verticalWriting = it }
        }
        scope.launch {
            settings.characterSpacing.collect { characterSpacing = it }
        }
        scope.launch {
            settings.paragraphSpacing.collect { paragraphSpacing = it }
        }
        scope.launch {
            settings.avoidPageBreak.collect { avoidPageBreak = it }
        }
        scope.launch {
            settings.readerHideFurigana.collect { hideFurigana = it }
        }
        scope.launch {
            settings.chapterTapZones.collect { tapZonePercent = it }
        }
        scope.launch {
            settings.justifyText.collect { justifyText = it }
        }
        scope.launch {
            settings.layoutAdvanced.collect { layoutAdvanced = it }
        }
        scope.launch {
            settings.keepScreenOn.collect { keepScreenOn = it }
        }
        scope.launch {
            settings.systemLightSepia.collect { systemLightSepia = it }
        }
        scope.launch {
            settings.einkRefreshOnPageTurn.collect { einkRefreshOnPageTurn = it }
        }
        scope.launch {
            settings.einkRefreshDurationMillis.collect { einkRefreshDurationMillis = it }
        }
        scope.launch {
            settings.einkRefreshPageInterval.collect { einkRefreshPageInterval = it }
        }
        scope.launch {
            settings.einkRefreshColor.collect { einkRefreshColor = it }
        }

        syncOnOpen()
    }

    fun updateTheme(value: Theme) = scope.launch { settings.setTheme(value) }
    fun updateFontSize(value: Double) = scope.launch { settings.setFontSize(value) }
    fun updateLineHeight(value: Double) = scope.launch { settings.setLineHeight(value) }
    fun updateHorizontalPadding(value: Double) = scope.launch { settings.setHorizontalPadding(value) }
    fun updateVerticalPadding(value: Double) = scope.launch { settings.setVerticalPadding(value) }
    fun updateSelectedFont(value: String) = scope.launch { settings.setSelectedFont(value) }
    fun updateContinuousMode(value: Boolean) = scope.launch { settings.setContinuousMode(value) }
    fun updateCustomBackgroundColor(value: Int) = scope.launch { settings.setCustomBackgroundColor(value) }
    fun updateCustomTextColor(value: Int) = scope.launch { settings.setCustomTextColor(value) }
    fun applyCustomTheme(value: CustomReaderTheme) = scope.launch { settings.setCustomTheme(value) }
    fun addCustomTheme(value: CustomReaderTheme) = scope.launch { settings.addCustomTheme(value) }
    fun deleteCustomTheme(value: CustomReaderTheme) = scope.launch { settings.deleteCustomTheme(value) }
    fun renameCustomTheme(value: CustomReaderTheme, newName: String) = scope.launch { settings.renameCustomTheme(value, newName) }
    fun updateVerticalWriting(value: Boolean) = scope.launch { settings.setVerticalWriting(value) }
    fun updateJustifyText(value: Boolean) = scope.launch { settings.setJustifyText(value) }
    fun updateAvoidPageBreak(value: Boolean) = scope.launch { settings.setAvoidPageBreak(value) }
    fun updateHideFurigana(value: Boolean) = scope.launch { settings.setReaderHideFurigana(value) }
    fun updateCharacterSpacing(value: Double) = scope.launch { settings.setCharacterSpacing(value) }
    fun updateParagraphSpacing(value: Double) = scope.launch { settings.setParagraphSpacing(value) }
    fun updateLayoutAdvanced(value: Boolean) = scope.launch { settings.setLayoutAdvanced(value) }
    fun updateTapZonePercent(value: Int) = scope.launch { settings.setChapterTapZones(value) }
    fun updateKeepScreenOn(value: Boolean) = scope.launch { settings.setKeepScreenOn(value) }
    fun updateSystemLightSepia(value: Boolean) = scope.launch { settings.setSystemLightSepia(value) }
    fun updateEinkRefreshOnPageTurn(value: Boolean) = scope.launch { settings.setEinkRefreshOnPageTurn(value) }
    fun updateEinkRefreshDurationMillis(value: Int) = scope.launch { settings.setEinkRefreshDurationMillis(value) }
    fun updateEinkRefreshPageInterval(value: Int) = scope.launch { settings.setEinkRefreshPageInterval(value) }
    fun updateEinkRefreshColor(value: String) = scope.launch { settings.setEinkRefreshColor(value) }

    fun getReaderSettings(context: Context): ReaderSettings {
        val fontUrl = if (FontManager.isCustomFont(context, selectedFont)) {
            FontManager.getFontUri(context, selectedFont)
        } else {
            null
        }

        val (bg, txt) = when (theme) {
            Theme.LIGHT -> 0xFFFFFFFF.toInt() to 0xFF000000.toInt()
            Theme.DARK -> 0xFF121212.toInt() to 0xFFE0E0E0.toInt()
            Theme.SEPIA -> 0xFFF2E2C9.toInt() to 0xFF3C2C1C.toInt()
            Theme.PURE_BLACK -> 0xFF000000.toInt() to 0xFFE0E0E0.toInt()
            Theme.CUSTOM -> customBackgroundColor to customTextColor
            Theme.SYSTEM -> {
                val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (isDark) {
                    if (systemLightSepia) {
                        0xFF1C140C.toInt() to 0xFFF2E2C9.toInt() // Inverted Sepia
                    } else {
                        0xFF121212.toInt() to 0xFFE0E0E0.toInt()
                    }
                } else {
                    if (systemLightSepia) {
                        0xFFF2E2C9.toInt() to 0xFF3C2C1C.toInt() // Sepia
                    } else {
                        0xFFFFFFFF.toInt() to 0xFF000000.toInt()
                    }
                }
            }
        }

        return ReaderSettings(
            fontSize = fontSize,
            lineHeight = lineHeight,
            characterSpacing = characterSpacing,
            paragraphSpacing = paragraphSpacing,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            selectedFont = selectedFont,
            fontUrl = fontUrl?.toString(),
            theme = theme.name.lowercase(),
            backgroundColor = bg,
            textColor = txt,
            verticalWriting = verticalWriting,
            justifyText = justifyText,
            avoidPageBreak = avoidPageBreak,
            hideFurigana = hideFurigana,
            layoutAdvanced = layoutAdvanced,
            tapZonePercent = tapZonePercent,
            continuousMode = continuousMode,
        )
    }

    fun getCurrentChapter(): File? {
        val absolutePath = document.chapterAbsolutePath(index.toUInt()) ?: return null
        return File(absolutePath)
    }

    fun getCurrentChapterTitle(): String? {
        return getChapterTitle(index)
    }

    fun getChapterTitle(chapterIndex: Int): String? {
        val href = document.getChapterHref(chapterIndex) ?: return null
        // Try TOC lookup first
        val tocLabel = findTocLabel(document.tableOfContents, href)
        if (tocLabel != null) return tocLabel
        // Fallback to file name without path
        return href.substringAfterLast("/").substringBefore(".")
    }

    private fun findTocLabel(toc: List<com.canopus.chimareader.data.epub.TocEntry>, href: String): String? {
        val fileName = href.substringAfterLast("/")
        for (entry in toc) {
            val entryHref = entry.href ?: continue
            // Check if the href ends with the same file name
            if (entryHref.endsWith(fileName) || entryHref.contains(fileName.substringBefore("."))) {
                return entry.label
            }
            val found = findTocLabel(entry.children, href)
            if (found != null) return found
        }
        return null
    }

    fun getFlattenedToc(): List<com.canopus.chimareader.data.epub.TocEntry> {
        val flat = mutableListOf<com.canopus.chimareader.data.epub.TocEntry>()
        fun flatten(entries: List<com.canopus.chimareader.data.epub.TocEntry>, depth: Int = 0) {
            for (e in entries) {
                // Add depth indentation to label if it's nested
                val indent = "  ".repeat(depth)
                flat.add(e.copy(label = "$indent${e.label}"))
                flatten(e.children, depth + 1)
            }
        }
        flatten(document.tableOfContents)
        return flat
    }

    fun getSpineIndexForHref(href: String): Int? {
        val decodedHref = java.net.URLDecoder.decode(href.substringBefore('#').substringBefore('?'), "UTF-8")
        val fileName = decodedHref.substringAfterLast("/")

        for (i in 0 until document.linearSpineItems.size) {
            val chapterHref = document.getChapterHref(i) ?: continue
            val chapterFileName = chapterHref.substringAfterLast("/")

            if (chapterHref.endsWith(decodedHref) || chapterFileName == fileName) {
                return i
            }
        }
        return null
    }

    fun saveBookmark(progress: Double, updateTracker: Boolean = true, force: Boolean = false) {
        currentProgress = progress
        bridge.updateProgress(progress)
        persistBookmark(progress, force)
        if (updateTracker && !trackingLocked && !appBackgrounded) {
            statisticsTracker.update(totalExploredCharCount)
        }
        persistToDisk()
        scheduleSyncExport()
    }

    fun flushReaderState() {
        persistBookmark(currentProgress, force = true)
        if (!trackingLocked && !appBackgrounded) {
            statisticsTracker.update(totalExploredCharCount)
        }
        persistToDisk()
    }

    fun syncOnOpen() {
        // Handled synchronously in ReaderScreen if enabled
    }

    fun syncAfterForeground() {
        if (isSyncing) return
        val sync = ttuSyncManager?.takeIf { it.isEnabled && it.autoSyncEnabled } ?: return
        isSyncing = true
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val metadata = BookStorage.loadMetadata(rootUrl) ?: return@launch
                val result = sync.syncBook(metadata, importOnly = true)
                if (result is SyncResult.Imported) {
                    reloadAfterImport()
                    getCurrentChapter()?.let { file ->
                        val fileUrl = "file://${file.absolutePath.replace("\\", "/")}"
                        val chapterTitle = getCurrentChapterTitle()
                        bridge.updateState(fileUrl, currentProgress, chapterTitle)
                        bridge.send(WebViewCommand.LoadChapter(fileUrl, currentProgress))
                    }
                }
            } finally {
                isSyncing = false
            }
        }
    }

    fun scheduleSyncExport() {
        // Debounced sync on page turns is disabled. Sync is handled periodic and on book close.
    }

    fun flushSyncExport() {
        val sync = ttuSyncManager ?: return
        if (!sync.isEnabled || !sync.autoSyncOnClose) return
        syncExportJob?.cancel()
        syncExportJob = scope.launch(Dispatchers.IO) {
            val metadata = BookStorage.loadMetadata(rootUrl) ?: return@launch
            sync.syncBook(metadata, SyncDirection.AUTO)
        }
    }

    private fun reloadAfterImport() {
        val bookmark = BookStorage.loadBookmark(rootUrl)
        if (bookmark != null) {
            index = bookmark.chapterIndex
            currentProgress = bookmark.progress
        }
        fullStatistics.clear()
        val stats = BookStorage.loadStatistics(rootUrl)
        if (stats != null) {
            fullStatistics.addAll(stats)
        }
    }

    fun nextChapter(): Boolean {
        if (index >= chapterCount - 1) return false
        saveBookmark(1.0)
        loadChapter(index + 1, 0.0)
        return true
    }

    fun previousChapter(): Boolean {
        if (index <= 0) return false
        saveBookmark(1.0)
        loadChapter(index - 1, 1.0)
        return true
    }

    fun jumpToChapter(spineIndex: Int, fragment: String? = null) {
        if (spineIndex != index) {
            saveBookmark(1.0)
        }
        loadChapter(spineIndex, 0.0)
        if (!fragment.isNullOrEmpty()) {
            bridge.send(WebViewCommand.JumpToFragment(fragment))
        }
    }

    /**
     * Resolves a raw `file://` URL (possibly carrying a #fragment) that came from an in-page
     * link click inside the WebView, finds the matching spine index, saves the current progress
     * bookmark, and then delegates to [jumpToChapter].
     *
     * If the URL cannot be matched to any spine item (e.g. external http link) the call is
     * silently ignored – the WebView already blocked the navigation via shouldOverrideUrlLoading.
     */
    fun jumpToUrl(url: String) {
        val fragment = url.substringAfter("#", missingDelimiterValue = "")
        val targetPath = url.substringBefore("#")
            .removePrefix("file://")
            .replace("\\", "/")

        val spineCount = document.linearSpineItems.size
        for (i in 0 until spineCount) {
            val chapterPath = document.chapterAbsolutePath(i.toUInt())
                ?.replace("\\", "/") ?: continue
            if (chapterPath == targetPath) {
                // Save progress before jumping so stats are consistent
                saveBookmark(currentProgress)
                jumpToChapter(i, fragment.ifEmpty { null })
                return
            }
        }
        android.util.Log.w("ReaderViewModel", "jumpToUrl: no spine match for $url")
    }

    private fun loadChapter(newIndex: Int, progress: Double) {
        // Flush any accumulated session delta to persistent statistics BEFORE we
        // change the index. persistBookmark() calls calculateExploredCharCount()
        // which uses the current index — if we change it first the delta is lost.
        persistBookmark(currentProgress)
        if (!trackingLocked && !appBackgrounded) {
            statisticsTracker.update(totalExploredCharCount)
        }

        index = newIndex
        // Reset tracker baseline to the new position so neither the timer loop
        // nor the saveBookmark call below register a false delta from the jump.
        statisticsTracker.resetBaseline(calculateExploredCharCount(progress))
        saveBookmark(progress, updateTracker = false, force = true)
        getCurrentChapter()?.let { file ->
            // Create proper file URL with encoded path
            val fileUrl = "file://${file.absolutePath.replace("\\", "/")}"
            val chapterTitle = getCurrentChapterTitle()
            bridge.updateState(fileUrl, progress, chapterTitle)
            bridge.send(WebViewCommand.LoadChapter(fileUrl, progress))
        }
    }

    private fun calculateExploredCharCount(progress: Double): Int {
        var count = 0
        for (i in 0 until index) {
            count += document.getChapterCharacters(i)
        }
        val currentChapterChars = document.getChapterCharacters(index)
        count += (currentChapterChars * progress).toInt()
        return count
    }

    private fun persistBookmark(progress: Double, force: Boolean = false) {
        val characterCount = calculateExploredCharCount(progress)
        totalExploredCharCount = characterCount

        val changed = force ||
            index != lastSavedChapterIndex ||
            characterCount != lastSavedCharacterCount ||
            abs(progress - lastSavedProgress) > BOOKMARK_PROGRESS_EPSILON

        if (!changed) return

        BookStorage.save(
            Bookmark(
                chapterIndex = index,
                progress = progress,
                characterCount = characterCount,
                lastModified = System.currentTimeMillis(),
            ),
            rootUrl,
            FileNames.bookmark,
        )
        lastSavedChapterIndex = index
        lastSavedProgress = progress
        lastSavedCharacterCount = characterCount
        scheduleSyncExport()
    }

    fun setTrackingLocked(locked: Boolean) {
        if (locked) {
            if (statisticsTracker.state.isTracking) {
                statisticsTracker.update(totalExploredCharCount)
            }
            trackingLocked = true
        } else {
            statisticsTracker.resetBaseline(totalExploredCharCount)
            trackingLocked = false
        }
    }

    fun togglePause() {
        statisticsTracker.togglePause(totalExploredCharCount)
    }

    fun onAppBackgrounded() {
        flushReaderState()
        appBackgrounded = true
    }

    fun onAppForegrounded() {
        statisticsTracker.resetBaseline(totalExploredCharCount)
        appBackgrounded = false
    }

    private fun persistToDisk() {
        val stats = statisticsTracker.statisticsForPersistence()
        BookStorage.saveStatistics(stats, rootUrl)
        scheduleSyncExport()
    }

    companion object {
        private const val BOOKMARK_PROGRESS_EPSILON = 0.0001
    }
}
