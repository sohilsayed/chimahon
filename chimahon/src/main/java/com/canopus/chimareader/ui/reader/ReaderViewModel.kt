package com.canopus.chimareader.ui.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.FileNames
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.Bookmark
import com.canopus.chimareader.data.NovelReaderSettings
import com.canopus.chimareader.data.Theme
import com.canopus.chimareader.data.FontManager
import com.canopus.chimareader.data.epub.EpubBook
import com.canopus.chimareader.data.epub.SpineItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.canopus.chimareader.data.Statistics

// ─── Commands ─────────────────────────────────────────────────────────────────

sealed interface WebViewCommand {
    data class LoadChapter(val url: String, val progress: Double) : WebViewCommand
    data class JumpToFragment(val fragment: String) : WebViewCommand
    data class ApplySasayakiCues(val cuesJson: String) : WebViewCommand
    data class HighlightSasayakiCue(
        val cueId: String,
        val reveal: Boolean,
        val onProgress: ((Double) -> Unit)? = null
    ) : WebViewCommand
    data object ClearSasayakiCue : WebViewCommand
    data class UpdateTextColor(val hex: String?) : WebViewCommand
    data class ChangeMode(val continuous: Boolean) : WebViewCommand
    data class ApplySettings(val settings: ReaderSettings) : WebViewCommand
    data class ChangeFocusMode(val focusMode: Boolean) : WebViewCommand
}

data class ReaderSettings(
    val fontSize: Int = 18,
    val lineHeight: Double = 1.6,
    val characterSpacing: Double = 0.0,
    val horizontalPadding: Int = 10,
    val verticalPadding: Int = 10,
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
    val continuousMode: Boolean = false
)

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
}

// ─── Loader ───────────────────────────────────────────────────────────────────

class ReaderLoaderViewModel(
    context: Context,
    book: BookMetadata
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
    private val scope: CoroutineScope
) {
    var index by mutableIntStateOf(0)
    var currentProgress by mutableDoubleStateOf(0.0)

    // Settings state
    var theme by mutableStateOf(Theme.SYSTEM)
    var fontSize by mutableIntStateOf(18)
    var lineHeight by mutableDoubleStateOf(1.6)
    var horizontalPadding by mutableIntStateOf(10)
    var verticalPadding by mutableIntStateOf(10)
    var selectedFont by mutableStateOf("System")
    var continuousMode by mutableStateOf(false)
    var customBackgroundColor by mutableIntStateOf(0xFFF2E2C9.toInt())
    var customTextColor by mutableIntStateOf(0xFF000000.toInt())
    var sasayakiPlayer: SasayakiPlayer? by mutableStateOf(null)
    var verticalWriting by mutableStateOf(true)
    var characterSpacing by mutableDoubleStateOf(0.0)
    var justifyText by mutableStateOf(false)
    var avoidPageBreak by mutableStateOf(true)
    var hideFurigana by mutableStateOf(false)
    var layoutAdvanced by mutableStateOf(false)
    var tapZonePercent by mutableIntStateOf(20)
    var keepScreenOn by mutableStateOf(false)

    // Tracks statistics for current reading session
    var isTimerPaused by mutableStateOf(false)
    var sessionReadingTime by mutableDoubleStateOf(0.0) // milliseconds
    var totalExploredCharCount by mutableIntStateOf(0)
    // To calculate session characters, we remember the initial char count
    var initialCharCount by mutableIntStateOf(0)
    
    val sessionCharactersRead: Int
        get() = maxOf(0, totalExploredCharCount - initialCharCount)

    var fullStatistics = mutableStateListOf<Statistics>()
    var lastSavedExploredCharCount = 0 // Made internal for UI calculation
    var lastSavedSessionReadingTime = 0.0 // Made internal for UI calculation
    private var lastPeriodicSaveTime = System.currentTimeMillis()
    private var lastSavedBookmarkFingerprint: Pair<Int, Long>? = null

    val todayCharactersRead: Int
        get() {
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val persistent = fullStatistics.find { it.dateKey == dateKey }?.charactersRead ?: 0
            val sessionDelta = maxOf(0, totalExploredCharCount - lastSavedExploredCharCount)
            return persistent + sessionDelta
        }

    val todayReadingTime: Double
        get() {
            val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val persistent = fullStatistics.find { it.dateKey == dateKey }?.readingTime ?: 0.0
            val sessionDelta = maxOf(0.0, sessionReadingTime - lastSavedSessionReadingTime)
            return persistent + sessionDelta
        }

    val allTimeCharactersRead: Int
        get() = fullStatistics.sumOf { it.charactersRead } + maxOf(0, totalExploredCharCount - lastSavedExploredCharCount)

    val allTimeReadingTime: Double
        get() = fullStatistics.sumOf { it.readingTime } + maxOf(0.0, sessionReadingTime - lastSavedSessionReadingTime)

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
            verticalWriting = settings.verticalWriting.first()
            avoidPageBreak = settings.avoidPageBreak.first()
            hideFurigana = settings.readerHideFurigana.first()
            tapZonePercent = settings.chapterTapZones.first()
            keepScreenOn = settings.keepScreenOn.first()
        }

        val bookmark = BookStorage.loadBookmark(rootUrl)
        index = bookmark?.chapterIndex ?: 0
        currentProgress = bookmark?.progress ?: 0.0
        lastSavedBookmarkFingerprint = bookmarkFingerprint(index, currentProgress)
        
        totalExploredCharCount = calculateExploredCharCount(currentProgress)
        initialCharCount = totalExploredCharCount
        lastSavedExploredCharCount = initialCharCount

        val stats = BookStorage.loadStatistics(rootUrl)
        if (stats != null) {
            fullStatistics.addAll(stats)
        }

        // Timer for readingTime
        scope.launch {
            while (true) {
                delay(1000)
                if (!isTimerPaused) {
                    sessionReadingTime += 1.0
                }
                // Periodic save stats every 60 seconds (more frequent than progress sync)
                if (System.currentTimeMillis() - lastPeriodicSaveTime >= 60000L) {
                    savePersistentStatistics()
                    lastPeriodicSaveTime = System.currentTimeMillis()
                }
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
            settings.verticalWriting.collect { verticalWriting = it }
        }
        scope.launch {
            settings.characterSpacing.collect { characterSpacing = it }
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
    }

    fun updateTheme(value: Theme) = scope.launch { settings.setTheme(value) }
    fun updateFontSize(value: Int) = scope.launch { settings.setFontSize(value) }
    fun updateLineHeight(value: Double) = scope.launch { settings.setLineHeight(value) }
    fun updateHorizontalPadding(value: Int) = scope.launch { settings.setHorizontalPadding(value) }
    fun updateVerticalPadding(value: Int) = scope.launch { settings.setVerticalPadding(value) }
    fun updateSelectedFont(value: String) = scope.launch { settings.setSelectedFont(value) }
    fun updateContinuousMode(value: Boolean) = scope.launch { settings.setContinuousMode(value) }
    fun updateCustomBackgroundColor(value: Int) = scope.launch { settings.setCustomBackgroundColor(value) }
    fun updateCustomTextColor(value: Int) = scope.launch { settings.setCustomTextColor(value) }
    fun updateVerticalWriting(value: Boolean) = scope.launch { settings.setVerticalWriting(value) }
    fun updateJustifyText(value: Boolean) = scope.launch { settings.setJustifyText(value) }
    fun updateAvoidPageBreak(value: Boolean) = scope.launch { settings.setAvoidPageBreak(value) }
    fun updateHideFurigana(value: Boolean) = scope.launch { settings.setReaderHideFurigana(value) }
    fun updateCharacterSpacing(value: Double) = scope.launch { settings.setCharacterSpacing(value) }
    fun updateLayoutAdvanced(value: Boolean) = scope.launch { settings.setLayoutAdvanced(value) }
    fun updateTapZonePercent(value: Int) = scope.launch { settings.setChapterTapZones(value) }
    fun updateKeepScreenOn(value: Boolean) = scope.launch { settings.setKeepScreenOn(value) }

    fun getReaderSettings(context: Context): ReaderSettings {
        val fontUrl = if (FontManager.isCustomFont(context, selectedFont)) {
            FontManager.getFontUri(context, selectedFont)
        } else null
        
        val (bg, txt) = when (theme) {
            Theme.LIGHT -> 0xFFFFFFFF.toInt() to 0xFF000000.toInt()
            Theme.DARK -> 0xFF121212.toInt() to 0xFFE0E0E0.toInt()
            Theme.SEPIA -> 0xFFF2E2C9.toInt() to 0xFF3C2C1C.toInt()
            Theme.CUSTOM -> customBackgroundColor to customTextColor
            Theme.SYSTEM -> {
                val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (isDark) 0xFF121212.toInt() to 0xFFE0E0E0.toInt() else 0xFFFFFFFF.toInt() to 0xFF000000.toInt()
            }
        }

        return ReaderSettings(
            fontSize = fontSize,
            lineHeight = lineHeight,
            characterSpacing = characterSpacing,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            selectedFont = selectedFont,
            fontUrl = fontUrl,
            theme = theme.name.lowercase(),
            backgroundColor = bg,
            textColor = txt,
            verticalWriting = verticalWriting,
            justifyText = justifyText,
            avoidPageBreak = avoidPageBreak,
            hideFurigana = hideFurigana,
            layoutAdvanced = layoutAdvanced,
            tapZonePercent = tapZonePercent,
            continuousMode = continuousMode
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

    fun saveBookmark(progress: Double, forceStatisticsSave: Boolean = false) {
        val bookmarkFingerprint = bookmarkFingerprint(index, progress)
        currentProgress = progress
        bridge.updateProgress(progress)

        if (!forceStatisticsSave && lastSavedBookmarkFingerprint == bookmarkFingerprint) {
            return
        }

        persistBookmark(progress)
        savePersistentStatistics()
        lastSavedBookmarkFingerprint = bookmarkFingerprint
    }

    fun nextChapter(): Boolean {
        if (index >= chapterCount - 1) return false
        loadChapter(index + 1, 0.0)
        return true
    }

    fun previousChapter(): Boolean {
        if (index <= 0) return false
        loadChapter(index - 1, 1.0)
        return true
    }

    fun jumpToChapter(spineIndex: Int, fragment: String? = null) {
        loadChapter(spineIndex, 0.0)
        if (!fragment.isNullOrEmpty()) {
            bridge.send(WebViewCommand.JumpToFragment(fragment))
        }
    }

    private fun loadChapter(newIndex: Int, progress: Double) {
        index = newIndex
        saveBookmark(progress)
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

    private fun persistBookmark(progress: Double) {
        totalExploredCharCount = calculateExploredCharCount(progress)
        
        BookStorage.save(
            Bookmark(
                chapterIndex = index,
                progress = progress,
                characterCount = totalExploredCharCount,
                lastModified = System.currentTimeMillis()
            ),
            rootUrl,
            FileNames.bookmark
        )
    }

    private fun bookmarkFingerprint(index: Int, progress: Double): Pair<Int, Long> {
        return index to progress.toBits()
    }

    private fun savePersistentStatistics() {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        
        val deltaChars = maxOf(0, totalExploredCharCount - lastSavedExploredCharCount)
        val deltaTime = maxOf(0.0, sessionReadingTime - lastSavedSessionReadingTime)
        
        if (deltaChars == 0 && deltaTime < 1.0) return

        var dailyStats = fullStatistics.find { it.dateKey == dateKey }
        if (dailyStats == null) {
            dailyStats = Statistics(
                title = document.title ?: "Unknown",
                dateKey = dateKey,
                lastStatisticModified = System.currentTimeMillis()
            )
            fullStatistics.add(dailyStats)
        }
        
        dailyStats.charactersRead += deltaChars
        dailyStats.readingTime += deltaTime
        dailyStats.lastStatisticModified = System.currentTimeMillis()
        
        // Update speeds
        val charsPerSec = if (dailyStats.readingTime > 0) dailyStats.charactersRead / dailyStats.readingTime else 0.0
        val speedPerHour = (charsPerSec * 3600).toInt()
        
        // Ensure readingTime unit is seconds for SPEED calculation
        // speed = chars / (time_in_seconds / 3600) = chars * 3600 / time_in_seconds
        dailyStats.lastReadingSpeed = speedPerHour
        dailyStats.maxReadingSpeed = maxOf(dailyStats.maxReadingSpeed, speedPerHour)
        
        // Persistence
        BookStorage.saveStatistics(fullStatistics.toList(), rootUrl)
        
        lastSavedExploredCharCount = totalExploredCharCount
        lastSavedSessionReadingTime = sessionReadingTime
    }
}
