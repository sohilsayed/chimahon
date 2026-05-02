package eu.kanade.tachiyomi.ui.library.novels

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.Modifier
import chimahon.DictionaryRepository
import com.canopus.chimareader.ui.reader.NovelReaderActivity
import eu.kanade.tachiyomi.ui.reader.viewer.OcrLookupPopup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlin.concurrent.thread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * App-side subclass of [NovelReaderActivity] that wires text-selection events
 * from the EPUB reader WebView into the [OcrLookupPopup] — no screenshot needed,
 * no OCR bitmap involved: just a plain text selection → dictionary lookup.
 *
 * [NovelReaderActivity.activityClass] is pointed at this class from [App.onCreate]
 * so that [BookshelfScreen]'s existing `NovelReaderActivity.launch()` call
 * automatically lands here without any chimahon → app module import.
 */
class ChimaReaderActivity : NovelReaderActivity() {
    
    private val readerPreferences: eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences by uy.kohesive.injekt.injectLazy()
    private var popupWebView: WebView? = null
    private val novelReaderSettings by lazy { com.canopus.chimareader.data.NovelReaderSettings(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensurePopupWebView()
        thread(name = "DictionaryWarmup", start = true) {
            val prefs = Injekt.get<DictionaryPreferences>()
            val activeProfile = prefs.profileStore.getActiveProfile()
            val termPaths = getDictionaryPaths(this@ChimaReaderActivity, activeProfile)
            Injekt.get<DictionaryRepository>().warmUp(termPaths)
        }
        
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                novelReaderSettings.verticalWriting.collect {
                    isVerticalWriting = it
                }
            }
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun ensurePopupWebView(): WebView {
        return popupWebView ?: WebView(this).also {
            popupWebView = it
            it.settings.javaScriptEnabled = true
            it.settings.domStorageEnabled = true
            it.settings.blockNetworkLoads = true
            it.settings.loadsImagesAutomatically = true
            it.setBackgroundColor(0x00000000)
            // Pre-load bootstrap HTML to avoid startup delay on first lookup
            it.loadDataWithBaseURL(
                "https://hoshi.local/popup/",
                eu.kanade.tachiyomi.ui.dictionary.getDictionaryBootstrapHtml(this),
                "text/html",
                "utf-8",
                null,
            )
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isPopupActive) {
            popupWebView?.let { webView ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            webView.evaluateJavascript("window.DictionaryRenderer?.navigate(-1);", null)
                            return true
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            webView.evaluateJavascript("window.DictionaryRenderer?.navigate(1);", null)
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun handleVolumeKey(forward: Boolean): Boolean {
        if (!readerPreferences.readWithVolumeKeys().get()) {
            return false
        }
        
        val inverted = readerPreferences.readWithVolumeKeysInverted().get()
        val finalForward = if (inverted) !forward else forward
        return super.handleVolumeKey(finalForward)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (isPopupActive) return super.onKeyDown(keyCode, event)
        if (!readerPreferences.readWithVolumeKeys().get()) return super.onKeyDown(keyCode, event)
        
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> return true
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (isPopupActive) return super.onKeyUp(keyCode, event)
        if (!readerPreferences.readWithVolumeKeys().get()) return super.onKeyUp(keyCode, event)

        return super.onKeyUp(keyCode, event)
    }

    @Composable
    override fun AdditionalAppearanceSettings() {
        val volumeKeys by remember { readerPreferences.readWithVolumeKeys().changes() }.collectAsState(readerPreferences.readWithVolumeKeys().get())
        val volumeKeysInverted by remember { readerPreferences.readWithVolumeKeysInverted().changes() }.collectAsState(readerPreferences.readWithVolumeKeysInverted().get())

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Navigation", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use volume keys to navigate", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = volumeKeys,
                    onCheckedChange = { readerPreferences.readWithVolumeKeys().set(it) }
                )
            }

            if (volumeKeys) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Invert volume keys", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = volumeKeysInverted,
                        onCheckedChange = { readerPreferences.readWithVolumeKeysInverted().set(it) }
                    )
                }
            }
        }
    }

    /**
     * Backing state for the lookup popup. Using Activity-level `mutableStateOf`
     * (not inside a composable) means it survives re-compositions correctly and
     * can be set from the non-Composable [onLookupRequested] override.
     */
    private var lookupState by mutableStateOf<LookupState?>(null)
    private var isVerticalWriting = true

    private data class LookupState(
        val word: String,
        val sentence: String,
        val anchorX: Float,
        val anchorY: Float,
        val anchorWidth: Float,
        val anchorHeight: Float,
        val isVertical: Boolean,
    )

    private var lookupDeferred: kotlinx.coroutines.Deferred<chimahon.DictionaryRepository.LookupResult2>? = null

    /** Called by [NovelReaderActivity] whenever the user selects text in the WebView. */
    override fun onLookupRequested(word: String, sentence: String, x: Float, y: Float, w: Float, h: Float) {
        val prefs = Injekt.get<DictionaryPreferences>()
        val activeProfile = prefs.profileStore.getActiveProfile()
        val termPaths = getDictionaryPaths(this, activeProfile)

        lookupDeferred = lifecycleScope.async(Dispatchers.IO) {
            Injekt.get<DictionaryRepository>().lookup(word, termPaths)
        }
        
        lookupState = LookupState(word, sentence, x, y, w, h, isVerticalWriting)
        isPopupActive = true
    }

    override fun onDismissPopupRequested() {
        super.onDismissPopupRequested()
        lookupState = null
        lookupDeferred?.cancel()
        lookupDeferred = null
        isPopupActive = false
    }

    /**
     * Injected into the parent's `setContent {}` via the [PopupOverlay] hook.
     * Renders the lookup popup over the reader content when text is selected.
     * No screenshot / crop — those are OCR-only features.
     */
    @Composable
    override fun PopupOverlay() {
        val state = lookupState ?: return

        // Retain a single WebView + repository across re-compositions so the
        // current lookup result isn't destroyed on every keystroke / recompose.
        val repo = remember { Injekt.get<DictionaryRepository>() }
        val webView = remember { ensurePopupWebView() }

        BackHandler {
            lookupState = null
            isPopupActive = false
        }

        // Background scrim to capture and consume clicks outside the popup.
        // This prevents the click from reaching the WebView and triggering navigation.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    lookupState = null
                    isPopupActive = false
                }
        )

        val mediaInfo = readerViewModel?.let { vm ->
            chimahon.MediaInfo(
                mangaTitle = vm.document.title ?: "",
                chapterName = vm.getCurrentChapterTitle() ?: ""
            )
        }
        eu.kanade.presentation.theme.TachiyomiTheme {
            OcrLookupPopup(
                lookupString = state.word,
                fullText = state.sentence,
                charOffset = state.sentence.indexOf(state.word).coerceAtLeast(0),
                onDismiss = { 
                    lookupState = null
                    isPopupActive = false
                },
                webView = webView,
                repository = repo,
                anchorX = state.anchorX,
                anchorY = state.anchorY,
                anchorWidth = state.anchorWidth,
                anchorHeight = state.anchorHeight,
                isVertical = state.isVertical,
                // No screenshot — plain text selection only
                mediaInfo = mediaInfo,
                onRequestScreenshot = null,
                onCropTriggered = null,
                initialLookupDeferred = lookupDeferred,
                usePopup = false,
                onTermMatched = { charCount ->
                    readerViewModel?.bridge?.send(com.canopus.chimareader.ui.reader.WebViewCommand.HighlightSelection(charCount))
                },
                modifier = Modifier,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        popupWebView?.destroy()
        popupWebView = null
        // The retained WebView must be destroyed with the Activity to avoid leaks
        lookupState = null
    }
}
