package eu.kanade.tachiyomi.ui.library.novels

import android.os.Bundle
import com.canopus.chimareader.data.BookStorage
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import chimahon.DictionaryRepository
import com.canopus.chimareader.ui.reader.NovelReaderActivity
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import eu.kanade.tachiyomi.ui.dictionary.prepareDictionaryWebViewShell
import eu.kanade.tachiyomi.ui.reader.viewer.OcrLookupPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    private val novelReaderSettings by lazy { com.canopus.chimareader.data.NovelReaderSettings(this, getSettingsNamespace()) }

    private var cachedActiveProfile: chimahon.anki.AnkiProfile? = null
    private var cachedTermPaths: List<String>? = null

    private fun getOrRefreshLookupPaths(): Pair<chimahon.anki.AnkiProfile, List<String>> {
        val prefs = Injekt.get<DictionaryPreferences>()
        val novelId = bookMetadata?.id ?: ""
        val novelLang = bookMetadata?.lang ?: ""
        val profile = cachedActiveProfile ?: prefs.profileResolver.resolve(
            novelId = novelId,
            sourceLang = novelLang,
        ).also { cachedActiveProfile = it }
        val paths = cachedTermPaths ?: getDictionaryPaths(this, profile).also { cachedTermPaths = it }
        return profile to paths
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = Injekt.get<DictionaryPreferences>()
        val path = intent.getStringExtra("extra_book_dir")
        if (!path.isNullOrEmpty()) {
            val root = java.io.File(path)
            if (root.exists() && root.isDirectory) {
                    val metadata = BookStorage.loadMetadata(root)
                if (metadata != null) {
                    val profile = prefs.profileResolver.resolve(
                        novelId = metadata.id ?: "",
                        sourceLang = metadata.lang ?: "",
                    )
                    cachedActiveProfile = profile
                    cachedTermPaths = getDictionaryPaths(this, profile)
                }
            }
        }

        super.onCreate(savedInstanceState)
        window.decorView.post {
            if (!isDestroyed) ensurePopupWebView()
        }

        val activeProfile = cachedActiveProfile ?: getOrRefreshLookupPaths().first
        val warmPaths = cachedTermPaths ?: getDictionaryPaths(this, activeProfile)
        cachedActiveProfile = activeProfile
        cachedTermPaths = warmPaths

        lifecycleScope.launch(Dispatchers.Default) {
            Injekt.get<DictionaryRepository>().warmUp(warmPaths, activeProfile.id)
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                novelReaderSettings.verticalWriting.collect {
                    isVerticalWriting = it
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                prefs.rawActiveProfileId().changes().collect {
                    cachedActiveProfile = null
                    cachedTermPaths = null
                }
            }
        }
    }

    override fun getSettingsNamespace(): String? {
        val profile = cachedActiveProfile ?: getOrRefreshLookupPaths().first
        return profile.id.takeIf { it.isNotEmpty() }
    }

    override fun getJitenApiKey(): String {
        return Injekt.get<DictionaryPreferences>().jitenApiKey().get()
    }

    override fun getJitenApiEndpoint(): String {
        return Injekt.get<DictionaryPreferences>().jitenApiEndpoint().get()
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun ensurePopupWebView(): WebView {
        val webView = popupWebView ?: WebView(this).also {
            popupWebView = it
            val profileLang = getOrRefreshLookupPaths().first.languageCode
            prepareDictionaryWebViewShell(this, it, languageCode = profileLang)
        }
        if (!isPopupActive) {
            attachPopupWebViewForWarmup(webView)
        }
        return webView
    }

    private fun attachPopupWebViewForWarmup(webView: WebView) {
        if (webView.parent != null) return
        val root = window.decorView as? ViewGroup ?: return
        webView.alpha = 0f
        webView.isClickable = false
        webView.isFocusable = false
        root.addView(
            webView,
            FrameLayout.LayoutParams(1, 1),
        )
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
        val activeProfile: chimahon.anki.AnkiProfile,
    )

    private var lookupDeferred: kotlinx.coroutines.Deferred<chimahon.DictionaryRepository.LookupResult2>? = null

    /** Called by [NovelReaderActivity] whenever the user selects text in the WebView. */
    override fun onLookupRequested(word: String, sentence: String, x: Float, y: Float, w: Float, h: Float) {
        val (profile, termPaths) = getOrRefreshLookupPaths()
        cancelActiveLookup()
        lookupDeferred = lifecycleScope.async(Dispatchers.Default) {
            Injekt.get<DictionaryRepository>().lookup(word.trim(), termPaths, profile.languageCode)
        }
        lookupState = LookupState(word, sentence, x, y, w, h, isVerticalWriting, profile)
        isPopupActive = true
    }

    /** Legacy bridge hook; current reader JS sends the sentence with onLookupRequested. */
    override fun onSentenceReady(sentence: String) {
        val current = lookupState ?: return
        if (current.sentence.isBlank() && sentence.isNotBlank()) {
            lookupState = current.copy(sentence = sentence)
        }
    }

    override fun onDismissPopupRequested() {
        super.onDismissPopupRequested()
        lookupState = null
        cancelActiveLookup()
        isPopupActive = false
    }

    private fun cancelActiveLookup() {
        lookupDeferred?.cancel()
        lookupDeferred = null
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
            cancelActiveLookup()
            isPopupActive = false
        }

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
                    cancelActiveLookup()
                    isPopupActive = false
                },
                webView = webView,
                repository = repo,
                anchorX = state.anchorX,
                anchorY = state.anchorY,
                anchorWidth = state.anchorWidth,
                anchorHeight = state.anchorHeight,
                isVertical = state.isVertical,
                activeProfile = getOrRefreshLookupPaths().first,
                type = "novel",
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
        cancelActiveLookup()
        popupWebView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        popupWebView = null
        // The retained WebView must be destroyed with the Activity to avoid leaks
        lookupState = null
    }
}
