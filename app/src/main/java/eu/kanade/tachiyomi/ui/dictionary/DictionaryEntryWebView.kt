package eu.kanade.tachiyomi.ui.dictionary

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.JsonWriter
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
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
import androidx.compose.runtime.remember
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
import java.io.StringWriter
import java.nio.charset.StandardCharsets
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
    onAnkiLookup: ((Int, Int?, String?, String?) -> Unit)? = null,
    onRecursiveLookup: ((String) -> Unit)? = null,
    onTabSelect: ((Int) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    forceDefaultTheme: Boolean = false,
) {
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val amoled by dictionaryPreferences.themeDarkAmoled().collectAsState()
    val customColor by dictionaryPreferences.customColor().collectAsState()

    val context = LocalContext.current
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val seedColor = if (customColor == 0 || forceDefaultTheme) uiPreferences.colorTheme().get() else customColor

    val systemIsDark = isSystemInDarkTheme()
    val isDark = remember(seedColor, customColor, systemIsDark) {
        if (customColor != 0) Color(seedColor).luminance() < 0.5f else systemIsDark
    }
    val colorScheme = remember(isDark, amoled, seedColor) {
        getDictionaryColorScheme(isDark, amoled, seedColor)
    }
    val BgColor = remember(isDark, amoled, seedColor, colorScheme) {
        if (amoled && isDark) Color.Black else colorScheme.surface
    }

    val payload = remember(context, results, styles, mediaDataUris, placeholder, isDark, showFrequencyHarmonic, groupTerms, showPitchDiagram, showPitchNumber, showPitchText, activeProfile, existingExpressions, tabs, recursiveNavMode, wordAudioEnabled) {
        val buildStart = SystemClock.elapsedRealtime()
        val prefs = Injekt.get<DictionaryPreferences>()
        val result = buildRenderPayload(
            context, results, styles, mediaDataUris, placeholder, isDark,
            showFrequencyHarmonic, groupTerms, showPitchDiagram, showPitchNumber, showPitchText,
            prefs.wordAudioAutoplay().get(), activeProfile, existingExpressions, tabs, recursiveNavMode,
            onAnkiLookup = { index, glossary, selectedDict, popupSelection, forceOpen ->
                state.onAnkiLookup?.invoke(index, glossary, selectedDict, popupSelection, forceOpen)
            },
            wordAudioEnabled = wordAudioEnabled,
        )
        Log.i(
            "DictionaryRender",
            "payload_build_ms=${SystemClock.elapsedRealtime() - buildStart} results=${results.size} tabs=${tabs.size}",
        )
        result
    }

    Box(modifier = modifier.background(BgColor)) {
        AndroidView<WebView>(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx: Context ->
            val webView = webViewProvider?.invoke(ctx) ?: WebView(ctx)

            if (webView.tag == null) {
                val state = DictionaryWebViewState(ctx, webViewProvider = { webView })
                webView.apply {
                    alpha = 0f
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
                            if (url.scheme == ANKI_SCHEME && (url.host == ANKI_PATH_ADD || url.host == ANKI_PATH_OPEN)) {
                                val forceOpen = url.host == ANKI_PATH_OPEN
                                val index = url.getQueryParameter("index")?.toIntOrNull()
                                val glossary = url.getQueryParameter("glossary")?.toIntOrNull()
                                val selectedDict = url.getQueryParameter("selected_dict")
                                val popupSelection = url.getQueryParameter("popup_selection")
                                if (index != null && index >= 0) {
                                    android.util.Log.d("DictionaryEntryWebView", "onAnkiLookup: index=$index, forceOpen=$forceOpen, glossary=$glossary, selectedDict=$selectedDict, popupSelection=$popupSelection")
                                    s?.onAnkiLookup?.invoke(index, glossary, selectedDict, popupSelection, forceOpen)
                                }
                                return true // Consumed
                            }
                            return false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val s = view?.tag as? DictionaryWebViewState ?: return
                            s.pageReady = true
                            // Inject Anki bridge function that accepts two parameters
                            view.evaluateJavascript(
                                """
                                window.AnkiBridge = {
                                    addToAnki: function(index, glossary, selectedDict, popupSelection) {
                                        var url = "anki://add?index=" + index;
                                        if (glossary !== undefined && glossary !== null) {
                                            url += "&glossary=" + glossary;
                                        }
                                        if (selectedDict) {
                                            url += "&selected_dict=" + encodeURIComponent(selectedDict);
                                        }
                                        if (popupSelection) {
                                            url += "&popup_selection=" + encodeURIComponent(popupSelection);
                                        }
                                        window.location.href = url;
                                    },
                                    openInAnki: function(index, glossary, selectedDict, popupSelection) {
                                        var url = "anki://open?index=" + index;
                                        if (glossary !== undefined && glossary !== null) {
                                            url += "&glossary=" + glossary;
                                        }
                                        if (selectedDict) {
                                            url += "&selected_dict=" + encodeURIComponent(selectedDict);
                                        }
                                        if (popupSelection) {
                                            url += "&popup_selection=" + encodeURIComponent(popupSelection);
                                        }
                                        window.location.href = url;
                                    }
                                };
                                window.DictionaryRenderer && window.DictionaryRenderer.setRecursiveLookupEnabled(true);
                                (function(v) {
                                    v = v + 'px';
                                    document.documentElement.style.fontSize = v;
                                    document.body.style.fontSize = v;
                                    document.documentElement.style.transform = 'none';
                                    document.documentElement.style.transformOrigin = 'top left';
                                })('${s.fontSize}');
                                """.trimIndent(),
                                null,
                            )
                            view.alpha = 1f
                            s.flush(view)
                            s.injectCustomCss(view)
                        }
                    }

                    tag = state

                    loadDataWithBaseURL(
                        "https://dictionary.local/",
                        getDictionaryBootstrapHtml(ctx, colorScheme, isDark, seedColor, amoled),
                        "text/html",
                        "utf-8",
                        null,
                    )
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
            state.fontSize = fontSize
            state.pendingPayload = payload

            // Theme is completely static to the WebView based on data-theme attribute.
            
            webView.setBackgroundColor(BgColor.toArgb())

            // Set base font size
            webView.evaluateJavascript(
                "(function(v) {" +
                    "v = v + 'px';" +
                    "document.documentElement.style.fontSize = v;" +
                    "document.body.style.fontSize = v;" +
                    "document.documentElement.style.transform = 'none';" +
                    "document.documentElement.style.transformOrigin = 'top left';" +
                    "document.documentElement.dataset.pageType = 'popup';" +
                    "document.documentElement.dataset.theme = '${if (isDark) "dark" else "light"}';" +
                    "})('$fontSize');",
                null,
            )

            state.customCss = customCss
            if (state.pageReady) {
                state.injectCustomCss(webView)
                state.flush(webView)
            }
        },
        onRelease = { webView ->
            val state = webView.tag as? DictionaryWebViewState
            state?.pendingPayload = null
            state?.lastPayload = null

            webView.evaluateJavascript("window.DictionaryRenderer?.clear();", null)
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

    fun flush(webView: WebView) {
        val payload = pendingPayload ?: return

        if (payload == lastPayload) {
            pendingPayload = null
            return
        }

        lastPayload = payload
        pendingPayload = null

        val renderStart = SystemClock.elapsedRealtime()

        // Update bridge payload and trigger JS render
        bridge.setJson(payload)
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
): String {
    val buffer = StringWriter(4096)
    JsonWriter(buffer).use { w ->
        w.beginObject()

        // Dictionary Priority Order (Titles)
        val orderedTitles = activeProfile.dictionaryOrder
            .map { getDictionaryTitle(context, it) }

        w.name("dictionaryOrder").beginArray()
        for (title in orderedTitles) {
            w.value(title)
        }
        w.endArray()

        w.name("ankiEnabled").value(activeProfile.ankiEnabled)
        w.name("ankiDupAction").value(activeProfile.ankiDupAction)

        w.name("placeholder").value(placeholder)
        w.name("isDark").value(isDark)
        w.name("showFrequencyHarmonic").value(showFrequencyHarmonic)
        w.name("groupTerms").value(groupTerms)
        w.name("showPitchDiagram").value(showPitchDiagram)
        w.name("showPitchNumber").value(showPitchNumber)
        w.name("showPitchText").value(showPitchText)
        w.name("wordAudioAutoplay").value(wordAudioAutoplay)
        w.name("wordAudioEnabled").value(wordAudioEnabled)
        w.name("recursiveNavMode").value(recursiveNavMode)

        // Tabs for recursive lookup navigation
        w.name("tabs").beginArray()
        for (tab in tabs) {
            w.beginObject()
            w.name("label").value(tab.label)
            w.name("active").value(tab.active)
            w.endObject()
        }
        w.endArray()

        w.name("existingExpressions").beginArray()
        for (expr in existingExpressions) {
            w.value(expr)
        }
        w.endArray()

        // Styles array
        w.name("styles").beginArray()
        for (style in styles) {
            w.beginObject()
            w.name("dictName").value(style.dictName)
            w.name("styles").value(style.styles)
            w.endObject()
        }
        w.endArray()

        // Media data URIs
        w.name("mediaDataUris").beginObject()
        for ((key, value) in mediaDataUris) {
            w.name(key).value(value)
        }
        w.endObject()

        // Results array
        w.name("results").beginArray()
        for ((index, result) in results.withIndex()) {
            w.beginObject()
            w.name("index").value(index)
            w.name("matched").value(result.matched)
            w.name("deinflected").value(result.deinflected)

            // Process array
            w.name("process").beginArray()
            for (p in result.process) w.value(p)
            w.endArray()

            // Term object
            w.name("term").beginObject()
            w.name("expression").value(result.term.expression)
            w.name("reading").value(result.term.reading)
            w.name("rules").value(result.term.rules)

            // Glossaries
            w.name("glossaries").beginArray()
            for (g in result.term.glossaries) {
                w.beginObject()
                w.name("dictName").value(g.dictName)
                w.name("glossary").value(g.glossary)
                w.name("definitionTags").value(g.definitionTags)
                w.name("termTags").value(g.termTags)
                w.endObject()
            }
            w.endArray()

            // Frequencies
            w.name("frequencies").beginArray()
            for (group in result.term.frequencies) {
                w.beginObject()
                w.name("dictName").value(group.dictName)
                w.name("frequencies").beginArray()
                for (item in group.frequencies) {
                    w.beginObject()
                    w.name("value").value(item.value)
                    w.name("displayValue").value(item.displayValue)
                    w.endObject()
                }
                w.endArray()
                w.endObject()
            }
            w.endArray()

            // Pitches
            w.name("pitches").beginArray()
            for (group in result.term.pitches) {
                w.beginObject()
                w.name("dictName").value(group.dictName)
                w.name("pitchPositions").beginArray()
                for (pos in group.pitchPositions) w.value(pos)
                w.endArray()
                w.endObject()
            }
            w.endArray()

            w.endObject() // term
            w.endObject() // result
        }
        w.endArray()

        w.endObject()
    }
    return buffer.toString()
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

@Volatile
private var dictionaryCssCache: String? = null
@Volatile
private var dictionaryJsCache: String? = null
private val dictionaryAssetLock = Any()

internal fun getDictionaryBootstrapHtml(
    context: Context,
    colorScheme: androidx.compose.material3.ColorScheme? = null,
    isDark: Boolean? = null,
    seedColor: Int? = null,
    isAmoled: Boolean = false,
): String {
    var css = ""
    var js = ""
    
    if (!BuildConfig.DEBUG) {
        synchronized(dictionaryAssetLock) {
            if (dictionaryCssCache == null) {
                dictionaryCssCache = readDictionaryAsset(context, "base.css")
                dictionaryJsCache = readDictionaryAsset(context, "renderer.js").replace("</script", "<\\/script")
            }
            css = dictionaryCssCache!!
            js = dictionaryJsCache!!
        }
    } else {
        css = readDictionaryAsset(context, "base.css")
        js = readDictionaryAsset(context, "renderer.js").replace("</script", "<\\/script")
    }

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
          <style>${"$css"}</style>${"$dynamicThemeCss"}
          <style id="dictionary-styles"></style>
          <style id="hoshi-custom-css"></style>
        </head>
        <body>
          <main id="entries" class="entries"></main>
          <script>${"$js"}</script>
        </body>
        </html>
    """.trimIndent()
}

private fun readDictionaryAsset(context: Context, filename: String): String {
    // In debug mode, try loading from external debug directory first
    if (BuildConfig.DEBUG) {
        val debugDir = File(context.getExternalFilesDir(null), "debug/dictionary")
        val debugFile = File(debugDir, filename)
        if (debugFile.exists()) {
            try {
                Log.i("DictionaryRender", "Loaded $filename from debug directory")
                return debugFile.readText(StandardCharsets.UTF_8)
            } catch (e: Exception) {
                Log.e("DictionaryRender", "Failed to read debug $filename", e)
            }
        }
    }
    return readTextAsset(context, "dictionary/$filename")
}

private fun readTextAsset(context: Context, assetPath: String): String {
    return context.assets.open(assetPath).use { input ->
        input.readBytes().toString(StandardCharsets.UTF_8)
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
