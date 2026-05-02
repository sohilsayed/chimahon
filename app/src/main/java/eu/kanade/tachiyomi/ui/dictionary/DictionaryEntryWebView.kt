package eu.kanade.tachiyomi.ui.dictionary

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.JsonPrimitive
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import chimahon.DictionaryStyle
import chimahon.LookupResult
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import chimahon.audio.WordAudioService
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.colorscheme.*
import com.materialkolor.PaletteStyle
import tachiyomi.presentation.core.util.collectAsState

private const val ANKI_SCHEME = "anki"
private const val ANKI_PATH_ADD = "add"
private const val ANKI_PATH_OPEN = "open"

private const val HOSHI_SCHEME = "hoshi"
private const val HOSHI_HOST_LOOKUP = "lookup"
private const val HOSHI_HOST_TAB = "tab"
private const val HOSHI_HOST_BACK = "back"

/** Represents one entry in the scrollable lookup-history tab bar shown inside the WebView. */
data class TabInfo(val label: String, val active: Boolean)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DictionaryEntryWebView(
    results: List<LookupResult>,
    styles: List<DictionaryStyle>,
    mediaDataUris: Map<String, String>,
    placeholder: String,
    headerText: String = "",
    fontSize: Int = 16,
    showFrequencyHarmonic: Boolean = false,
    groupTerms: Boolean = true,
    activeProfile: chimahon.anki.AnkiProfile,
    existingExpressions: Set<String> = emptySet(),
    tabs: List<TabInfo> = emptyList(),
    showPitchDiagram: Boolean = true,
    showPitchNumber: Boolean = true,
    showPitchText: Boolean = true,
    recursiveNavMode: String = "tabs",
    wordAudioEnabled: Boolean = true,
    customCss: String = "",
    modifier: Modifier = Modifier,
    webViewProvider: ((Context) -> WebView)? = null,
    onAnkiLookup: ((Int, Int?, String?, String?, Boolean) -> Unit)? = null,
    onRecursiveLookup: ((String) -> Unit)? = null,
    onTabSelect: ((Int) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    forceDefaultTheme: Boolean = false,
    isLoading: Boolean = false,
) {
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val amoled by dictionaryPreferences.themeDarkAmoled().collectAsState()
    val customColor by dictionaryPreferences.customColor().collectAsState()

    val context = LocalContext.current
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val seedColor = if (customColor == 0 || forceDefaultTheme) uiPreferences.colorTheme().get() else customColor

    val systemIsDark = isSystemInDarkTheme()
    val isDark = remember(seedColor, customColor, systemIsDark, forceDefaultTheme) {
        if (customColor != 0 && !forceDefaultTheme) Color(seedColor).luminance() < 0.5f else systemIsDark
    }
    val colorScheme = remember(isDark, amoled, seedColor) {
        getDictionaryColorScheme(isDark, amoled, seedColor)
    }
    val BgColor = remember(isDark, amoled, seedColor, colorScheme) {
        if (amoled && isDark) Color.Black else colorScheme.surface
    }

    val payloadObject = remember(context, results, styles, mediaDataUris, placeholder, isDark, showFrequencyHarmonic, groupTerms, showPitchDiagram, showPitchNumber, showPitchText, activeProfile, existingExpressions, tabs, recursiveNavMode, wordAudioEnabled) {
        val buildStart = SystemClock.elapsedRealtime()
        val prefs = Injekt.get<DictionaryPreferences>()
        val result = buildRenderPayload(
            context, results, styles, mediaDataUris, placeholder, isDark,
            showFrequencyHarmonic, groupTerms, showPitchDiagram, showPitchNumber, showPitchText,
            prefs.wordAudioAutoplay().get(), activeProfile, existingExpressions, tabs, recursiveNavMode,
            wordAudioEnabled = wordAudioEnabled,
        )
        Log.i(
            "DictionaryRender",
            "payload_build_ms=${SystemClock.elapsedRealtime() - buildStart} results=${results.size} tabs=${tabs.size}",
        )
        result
    }
    val bootstrapHtml = remember(context, isDark, amoled, seedColor, colorScheme) {
        getDictionaryBootstrapHtml(
            context = context,
            colorScheme = colorScheme,
            isDark = isDark,
            isAmoled = amoled,
            seedColor = seedColor
        )
    }
    
    val payloadString = remember(payloadObject) { payloadObject.toString() }
    
    var isPageReady by remember { mutableStateOf(false) }
    var loadedHtml by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.background(BgColor)) {
        AndroidView<WebView>(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx: Context ->
            val webView = webViewProvider?.invoke(ctx) ?: WebView(ctx)

            val isAlreadyWarm = webView.url == "https://hoshi.local/popup/"
            
            if (webView.tag == null) {
                val state = DictionaryWebViewState(ctx, webViewProvider = { webView })
                webView.apply {
                    // Only hide if we actually need to load something
                    alpha = if (isAlreadyWarm) 1f else 0f
                    
                    setBackgroundColor(BgColor.toArgb())
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.setSupportZoom(true)
                    settings.displayZoomControls = false
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        settings.forceDark = WebSettings.FORCE_DARK_OFF
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        settings.isAlgorithmicDarkeningAllowed = false
                    }

                    disableSafeBrowsingForDictionary(this)

                    // Payload bridge for efficient data transfer
                    addJavascriptInterface(state.bridge, "PayloadBridge")
                    addJavascriptInterface(state.wordAudioBridge, "WordAudioBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val s = view?.tag as? DictionaryWebViewState ?: return
                            s.pageReady = true
                            isPageReady = true
                            val enableRecursive = s.onRecursiveLookup != null
                            view.evaluateJavascript("window.DictionaryRenderer && window.DictionaryRenderer.setRecursiveLookupEnabled($enableRecursive);", null)
                            s.injectCustomCss(view)
                            s.flush(view)
                            view.alpha = 1f
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url ?: return false
                            val s = view?.tag as? DictionaryWebViewState

                            // ── hoshi:// — recursive lookup, tab navigation, back ──
                            if (url.scheme == HOSHI_SCHEME) {
                                when (url.host) {
                                    HOSHI_HOST_LOOKUP -> {
                                        val q = url.getQueryParameter("q") ?: return true
                                        if (q.isNotBlank()) s?.onRecursiveLookup?.invoke(q)
                                        return true
                                    }
                                    HOSHI_HOST_TAB -> {
                                        val idx = url.getQueryParameter("index")?.toIntOrNull()
                                        if (idx != null) s?.onTabSelect?.invoke(idx)
                                        return true
                                    }
                                    HOSHI_HOST_BACK -> {
                                        s?.onBack?.invoke()
                                        return true
                                    }
                                }
                                return true // consume any unknown hoshi:// URLs
                            }

                            // ── anki://add or anki://open ──
                            if (url.scheme == ANKI_SCHEME) {
                                val host = url.host ?: ""
                                val isAdd = host.equals(ANKI_PATH_ADD, ignoreCase = true)
                                val isOpen = host.equals(ANKI_PATH_OPEN, ignoreCase = true)

                                if (isAdd || isOpen) {
                                    val index = url.getQueryParameter("index")?.toIntOrNull()
                                    val glossary = url.getQueryParameter("glossary")?.toIntOrNull()
                                    val selectedDict = url.getQueryParameter("selected_dict")
                                    val popupSelection = url.getQueryParameter("popup_selection")
                                    
                                    android.util.Log.d("DictionaryEntryWebView", "onAnkiLookup: host=$host, index=$index, isOpen=$isOpen")
                                    
                                    if (index != null && index >= 0) {
                                        s?.onAnkiLookup?.invoke(index, glossary, selectedDict, popupSelection, isOpen)
                                    }
                                    return true // Consumed
                                }
                            }
                            return false
                        }
                    }

                    tag = state

                    // Only load if not already at the correct shell URL
                    if (!isAlreadyWarm) {
                        loadDataWithBaseURL(
                            "https://hoshi.local/popup/",
                            bootstrapHtml,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    } else {
                        // Already warm, manually trigger readiness
                        state.pageReady = true
                        isPageReady = true
                    }
                }
            } else if (isAlreadyWarm) {
                webView.alpha = 1f
                val s = (webView.tag as? DictionaryWebViewState)
                if (s != null) {
                    s.pageReady = true
                    isPageReady = true
                    // If we have a pending payload, push it now that we're tagging it
                    s.flush(webView)
                }
            }

            // Ensure focus and long-click are enabled even for retained WebViews
            webView.isLongClickable = true
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true
            webView.requestFocus()
            webView.setOnLongClickListener { false }

            webView.setOnKeyListener { v, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                            (v as? WebView)?.evaluateJavascript("window.DictionaryRenderer && window.DictionaryRenderer.navigate(-1);", null)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            (v as? WebView)?.evaluateJavascript("window.DictionaryRenderer && window.DictionaryRenderer.navigate(1);", null)
                            true
                        }
                        else -> false
                    }
                } else false
            }

            webView
        },
        update = { webView: WebView ->
            val state = webView.tag as? DictionaryWebViewState ?: return@AndroidView
            state.onAnkiLookup = onAnkiLookup
            state.onRecursiveLookup = onRecursiveLookup
            state.onTabSelect = onTabSelect
            state.onBack = onBack
            state.customCss = customCss
            state.fontSize = fontSize

            webView.setBackgroundColor(BgColor.toArgb())
            
            // Efficiently push new data to existing page
            if (state.pageReady) {
                val enableRecursive = onRecursiveLookup != null
                webView.evaluateJavascript("window.DictionaryRenderer && window.DictionaryRenderer.setRecursiveLookupEnabled($enableRecursive);", null)
                state.injectCustomCss(webView)
                if (isLoading) {
                    state.clear(webView)
                } else {
                    state.flush(webView, results, existingExpressions, payloadString)
                }
            } else {
                state.pendingPayload = payloadString
            }
        },
        onRelease = { webView ->
            val state = webView.tag as? DictionaryWebViewState
            state?.clear(webView)
            state?.lastPayload = null
        },
    )
    }
}

// JavaScript bridge for zero-overhead payload transfer
private class PayloadBridge {
    @Volatile
    private var _json: String = ""

    fun setJson(value: String) {
        _json = value
    }

    @JavascriptInterface
    fun getJson(): String = _json
}

private class WordAudioBridge(
    private val context: Context,
    private val webViewProvider: () -> WebView?
) {
    private val wordAudioService: WordAudioService by Injekt.injectLazy()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())
    private var mediaPlayer: android.media.MediaPlayer? = null

    @JavascriptInterface
    fun fetchAudio(term: String, reading: String, callbackId: String) {
        scope.launch {
            val results = wordAudioService.findWordAudio(term, reading)
            val jsonArray = org.json.JSONArray().apply {
                results.forEach { result ->
                    put(org.json.JSONObject().apply {
                        put("name", result.name)
                        put("url", result.url)
                    })
                }
            }.toString()
            
            webViewProvider()?.evaluateJavascript(
                "window.DictionaryRenderer && window.DictionaryRenderer.onAudioResults('$callbackId', $jsonArray);",
                null
            )
        }
    }
    
    @JavascriptInterface
    fun playAudio(url: String) {
        scope.launch {
            try {
                stopAudio()
                
                val player = android.media.MediaPlayer()
                mediaPlayer = player
                
                if (url.startsWith("chimahon-local://")) {
                    // Extract term and file from local URL: chimahon-local://sourceId/file
                    val uri = Uri.parse(url)
                    val sourceId = uri.host ?: return@launch
                    val filePath = uri.path?.substring(1) ?: return@launch // remove leading slash
                    
                    val data = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        wordAudioService.getAudioData(filePath, sourceId)
                    } ?: return@launch
                    
                    val extension = "." + (filePath.substringAfterLast('.', "mp3"))
                    val tempFile = File.createTempFile("word_audio", extension, context.cacheDir)
                    tempFile.writeBytes(data)
                    
                    player.setDataSource(tempFile.absolutePath)
                    tempFile.deleteOnExit()
                } else {
                    player.setDataSource(url)
                }
                
                player.prepareAsync()
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener { 
                    it.release()
                    if (mediaPlayer == it) mediaPlayer = null
                }
            } catch (e: Exception) {
                Log.e("WordAudioBridge", "Error playing audio: $url", e)
            }
        }
    }

    fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

private class DictionaryWebViewState(
    val context: Context,
    val bridge: PayloadBridge = PayloadBridge(),
    webViewProvider: () -> WebView?
) {
    val wordAudioBridge: WordAudioBridge = WordAudioBridge(context, webViewProvider)
    var pageReady: Boolean = false
    var fontSize: Int = 16
    var onAnkiLookup: ((Int, Int?, String?, String?, Boolean) -> Unit)? = null
    var onRecursiveLookup: ((String) -> Unit)? = null
    var onTabSelect: ((Int) -> Unit)? = null
    var onBack: (() -> Unit)? = null
    var lastPayload: String? = null
    var lastResults: List<LookupResult>? = null
    var lastExistingExpressions: Set<String>? = null
    var pendingPayload: String? = null
    var customCss: String = ""

    fun injectCustomCss(webView: WebView) {
        if (customCss.isEmpty()) {
            webView.evaluateJavascript(
                "var el = document.getElementById('hoshi-custom-css'); if (el) el.textContent = '';",
                null
            )
            return
        }
        webView.evaluateJavascript(
            "(function(v) {" +
                "var el = document.getElementById('hoshi-custom-css');" +
                "if (el) el.textContent = v;" +
                "})(decodeURIComponent('${java.net.URLEncoder.encode(customCss, "UTF-8").replace("+", "%20")}'));",
            null,
        )
    }

    fun clear(webView: WebView) {
        webView.evaluateJavascript("window.DictionaryRenderer && window.DictionaryRenderer.clear();", null)
    }

    fun flush(webView: WebView, results: List<LookupResult>? = null, existingExpressions: Set<String>? = null, payload: String? = null) {
        val p = payload ?: pendingPayload ?: return

        if (p == lastPayload) {
            pendingPayload = null
            return
        }

        if (lastResults == results && lastExistingExpressions != existingExpressions && existingExpressions != null) {
            // Optimized path: Only Anki status changed
            val json = org.json.JSONArray(existingExpressions).toString()
            webView.evaluateJavascript("DictionaryRenderer.updateAnkiStatus('$json')", null)
            lastPayload = p
            lastExistingExpressions = existingExpressions
            pendingPayload = null
            return
        }

        lastPayload = p
        lastResults = results
        lastExistingExpressions = existingExpressions
        pendingPayload = null

        val renderStart = SystemClock.elapsedRealtime()

        // Update bridge payload and trigger JS render
        bridge.setJson(p)
        webView.evaluateJavascript(
            "window.DictionaryRenderer && window.DictionaryRenderer.renderFromBridge();",
            null,
        )

        Log.i(
            "DictionaryRender",
            "webview_dispatch_ms=${SystemClock.elapsedRealtime() - renderStart}",
        )
    }
}

private fun buildRenderPayload(
    context: Context,
    results: List<LookupResult>,
    styles: List<DictionaryStyle>,
    mediaDataUris: Map<String, String>,
    placeholder: String,
    isDark: Boolean,
    showFrequencyHarmonic: Boolean,
    groupTerms: Boolean,
    showPitchDiagram: Boolean,
    showPitchNumber: Boolean,
    showPitchText: Boolean,
    wordAudioAutoplay: Boolean,
    activeProfile: chimahon.anki.AnkiProfile,
    existingExpressions: Set<String> = emptySet(),
    tabs: List<TabInfo> = emptyList(),
    recursiveNavMode: String = "tabs",
    wordAudioEnabled: Boolean = true,
): JsonObject = buildJsonObject {
    // Dictionary Priority Order (Titles)
    val orderedTitles = activeProfile.dictionaryOrder
        .map { getDictionaryTitle(context, it) }

    putJsonArray("dictionaryOrder") {
        for (title in orderedTitles) {
            add(JsonPrimitive(title))
        }
    }

    put("ankiEnabled", activeProfile.ankiEnabled)
    put("ankiDupAction", activeProfile.ankiDupAction)

    put("placeholder", placeholder)
    put("isDark", isDark)
    put("showFrequencyHarmonic", showFrequencyHarmonic)
    put("groupTerms", groupTerms)
    put("showPitchDiagram", showPitchDiagram)
    put("showPitchNumber", showPitchNumber)
    put("showPitchText", showPitchText)
    put("wordAudioAutoplay", wordAudioAutoplay)
    put("wordAudioEnabled", wordAudioEnabled)
    put("recursiveNavMode", recursiveNavMode)

    // Tabs for recursive lookup navigation
    putJsonArray("tabs") {
        for (tab in tabs) {
            add(buildJsonObject {
                put("label", tab.label)
                put("active", tab.active)
            })
        }
    }

    putJsonArray("existingExpressions") {
        for (expr in existingExpressions) {
            add(JsonPrimitive(expr))
        }
    }

    // Styles array
    putJsonArray("styles") {
        for (style in styles) {
            add(buildJsonObject {
                put("dictName", style.dictName)
                put("styles", style.styles)
            })
        }
    }

    // Media data URIs
    val mediaObj = buildJsonObject {
        for ((key, value) in mediaDataUris) {
            put(key, value)
        }
    }
    put("mediaDataUris", mediaObj)

    // Results array
    putJsonArray("results") {
        for ((index, result) in results.withIndex()) {
            add(buildJsonObject {
                put("index", index)
                put("matched", result.matched)
                put("deinflected", result.deinflected)

                // Process array
                putJsonArray("process") {
                    for (p in result.process) {
                        add(JsonPrimitive(p))
                    }
                }

                // Term object
                put("term", buildJsonObject {
                    put("expression", result.term.expression)
                    put("reading", result.term.reading)
                    put("rules", result.term.rules)

                    // Glossaries
                    putJsonArray("glossaries") {
                        for (g in result.term.glossaries) {
                            add(buildJsonObject {
                                put("dictName", g.dictName)
                                put("glossary", g.glossary)
                                put("definitionTags", g.definitionTags)
                                put("termTags", g.termTags)
                            })
                        }
                    }

                    // Frequencies
                    putJsonArray("frequencies") {
                        for (group in result.term.frequencies) {
                            add(buildJsonObject {
                                put("dictName", group.dictName)
                                putJsonArray("frequencies") {
                                    for (item in group.frequencies) {
                                        add(buildJsonObject {
                                            put("value", item.value)
                                            put("displayValue", item.displayValue)
                                        })
                                    }
                                }
                            })
                        }
                    }

                    // Pitches
                    putJsonArray("pitches") {
                        for (group in result.term.pitches) {
                            add(buildJsonObject {
                                put("dictName", group.dictName)
                                putJsonArray("pitchPositions") {
                                    for (pos in group.pitchPositions) {
                                        add(JsonPrimitive(pos))
                                    }
                                }
                            })
                        }
                    }
                })
            })
        }
    }
}



private fun disableSafeBrowsingForDictionary(webView: WebView) {
    val settings = webView.settings

    val disabledViaAndroidX = runCatching {
        val featureClass = Class.forName("androidx.webkit.WebViewFeature")
        val featureValue = featureClass.getField("SAFE_BROWSING_ENABLE").get(null) as String
        val isSupportedMethod = featureClass.getMethod("isFeatureSupported", String::class.java)
        val supported = isSupportedMethod.invoke(null, featureValue) as? Boolean ?: false
        if (!supported) return@runCatching false

        val compatClass = Class.forName("androidx.webkit.WebSettingsCompat")
        val setter = compatClass.getMethod(
            "setSafeBrowsingEnabled",
            WebSettings::class.java,
            Boolean::class.javaPrimitiveType,
        )
        setter.invoke(null, settings, false)
        true
    }.getOrDefault(false)

    if (!disabledViaAndroidX && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = false
    }
}


internal fun getDictionaryBootstrapHtml(
    context: Context,
    colorScheme: androidx.compose.material3.ColorScheme? = null,
    isDark: Boolean? = null,
    seedColor: Int? = null,
    isAmoled: Boolean = false,
): String {
    var css = ""
    var js = ""
    
    css = readTextAsset(context, "dictionary/base.css")
    js = readTextAsset(context, "dictionary/renderer.js").replace("</script", "<\\/script")

    val dynamicThemeCss = if (colorScheme != null) {
        val accentHex = "#%06X".format(0xFFFFFF and colorScheme.primary.toArgb())
        val onAccentHex = "#%06X".format(0xFFFFFF and colorScheme.onPrimary.toArgb())
        val fgHex = "#%06X".format(0xFFFFFF and colorScheme.onSurface.toArgb())
        val bgHex = if (isAmoled && isDark == true) {
            "#000000"
        } else {
            "#%06X".format(0xFFFFFF and colorScheme.surface.toArgb())
        }
        val secondaryHex = "#%06X".format(0xFFFFFF and colorScheme.onSurfaceVariant.toArgb())
        val borderHex = "#%06X".format(0xFFFFFF and colorScheme.outlineVariant.toArgb())
        val hoverHex = "#%06X".format(0xFFFFFF and colorScheme.surfaceVariant.toArgb())
        """
          <style id="dynamic-theme">
            :root, :root[data-theme="dark"], :root[data-theme="light"] {
                --accent: $accentHex;
                --on-accent: $onAccentHex;
                --fg: $fgHex;
                --bg: $bgHex;
                --secondary: $secondaryHex;
                --border: $borderHex;
                --hover-bg: $hoverHex;
                --pronunciation-annotation-color: $fgHex;
            }
          </style>
        """
    } else ""
    
    val themeAttr = if (isDark == true) "dark" else "light"

    return """
        <!doctype html>
        <html data-theme="$themeAttr">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1.0">
          <style>$css</style>$dynamicThemeCss
          <style id="dictionary-styles"></style>
          <style id="hoshi-custom-css"></style>
          <script>
            window.AnkiBridge = {
              addToAnki: function(index, glossary, selectedDict, popupSelection) {
                var url = "anki://add?index=" + index;
                if (glossary !== undefined && glossary !== null) url += "&glossary=" + glossary;
                if (selectedDict) url += "&selected_dict=" + encodeURIComponent(selectedDict);
                if (popupSelection) url += "&popup_selection=" + encodeURIComponent(popupSelection);
                window.location.href = url;
              },
              openInAnki: function(index, glossary, selectedDict, popupSelection) {
                var url = "anki://open?index=" + index;
                if (glossary !== undefined && glossary !== null) url += "&glossary=" + glossary;
                if (selectedDict) url += "&selected_dict=" + encodeURIComponent(selectedDict);
                if (popupSelection) url += "&popup_selection=" + encodeURIComponent(popupSelection);
                window.location.href = url;
              }
            };
          </script>
        </head>
        <body>
          <main id="entries" class="entries"></main>
          <script>$js</script>
        </body>
        </html>
    """.trimIndent()
}


private fun readTextAsset(context: Context, assetPath: String): String {
    return context.assets.open(assetPath).use { input ->
        input.readBytes().toString(Charsets.UTF_8)
    }
}

private val dictionaryTitleCache = java.util.concurrent.ConcurrentHashMap<String, String>()

private fun getDictionaryTitle(context: Context, dirName: String): String {
    return dictionaryTitleCache.getOrPut(dirName) {
        val dictionariesDir = File(context.getExternalFilesDir(null), "dictionaries")
        val dictDir = File(dictionariesDir, dirName)
        val indexFile = File(dictDir, "index.json")
        if (!indexFile.exists()) return@getOrPut dirName

        try {
            val json = indexFile.readText()
            org.json.JSONObject(json).optString("title", dirName)
        } catch (e: Exception) {
            dirName
        }
    }
}

fun getDictionaryColorScheme(
    isDark: Boolean,
    isAmoled: Boolean,
    seedColor: Int,
): ColorScheme {
    val uiPreferences = Injekt.get<UiPreferences>()
    val baseScheme = CustomColorScheme(
        seed = Color(seedColor),
        style = PaletteStyle.TonalSpot // Low-key, subtle gradient
    )
    return baseScheme.getColorScheme(isDark, isAmoled, false)
}
