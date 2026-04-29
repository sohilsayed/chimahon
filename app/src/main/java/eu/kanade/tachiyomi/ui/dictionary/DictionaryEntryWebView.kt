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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
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
import chimahon.audio.WordAudioResult
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences

private const val ANKI_SCHEME = "anki"
private const val ANKI_PATH_ADD = "add"

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
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.surface.luminance() < 0.5f
    val context = LocalContext.current
    val accentHex = "#%06X".format(0xFFFFFF and colorScheme.primary.toArgb())
    val onAccentHex = "#%06X".format(0xFFFFFF and colorScheme.onPrimary.toArgb())
    val fgHex = "#%06X".format(0xFFFFFF and colorScheme.onSurface.toArgb())
    val bgHex = "#%06X".format(0xFFFFFF and colorScheme.surface.toArgb())
    val borderHex = "#%06X".format(0xFFFFFF and colorScheme.outline.toArgb())

    val payload = remember(context, results, styles, mediaDataUris, placeholder, isDark, showFrequencyHarmonic, groupTerms, showPitchDiagram, showPitchNumber, showPitchText, activeProfile, existingExpressions, tabs, recursiveNavMode, wordAudioEnabled) {
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

    AndroidView<WebView>(
        factory = { ctx: Context ->
            val webView = webViewProvider?.invoke(ctx) ?: WebView(ctx)

            if (webView.tag == null) {
                val state = DictionaryWebViewState(ctx, webViewProvider = { webView })
                webView.apply {
                    setBackgroundColor(0x00000000)
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

                            // ── anki://add — Anki card creation ──
                            if (url.scheme == ANKI_SCHEME && url.host == ANKI_PATH_ADD) {
                                val index = url.getQueryParameter("index")?.toIntOrNull()
                                val glossary = url.getQueryParameter("glossary")?.toIntOrNull()
                                val selectedDict = url.getQueryParameter("selected_dict")
                                val popupSelection = url.getQueryParameter("popup_selection")
                                if (index != null && index >= 0) {
                                    android.util.Log.d("DictionaryEntryWebView", "onAnkiLookup: index=$index, glossary=$glossary, selectedDict=$selectedDict, popupSelection=$popupSelection")
                                    s?.onAnkiLookup?.invoke(index, glossary, selectedDict, popupSelection)
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
                            s.flush(view)
                            s.injectCustomCss(view)
                        }
                    }

                    tag = state

                    loadDataWithBaseURL(
                        "https://dictionary.local/",
                        getDictionaryBootstrapHtml(ctx),
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

            val theme = if (isDark) "dark" else "light"
            val secondaryHex = "#%06X".format(0xFFFFFF and colorScheme.onSurfaceVariant.toArgb())
            val hoverHex = "#%06X".format(0xFFFFFF and colorScheme.surfaceVariant.toArgb())

            webView.evaluateJavascript(
                "(function(r) {" +
                    "r.dataset.theme = '$theme';" +
                    "r.dataset.pageType = 'popup';" +
                    "r.style.setProperty('--accent', '$accentHex');" +
                    "r.style.setProperty('--on-accent', '$onAccentHex');" +
                    "r.style.setProperty('--fg-dynamic', '$fgHex');" +
                    "r.style.setProperty('--bg-dynamic', '$bgHex');" +
                    "r.style.setProperty('--secondary-dynamic', '$secondaryHex');" +
                    "r.style.setProperty('--border-dynamic', '$borderHex');" +
                    "r.style.setProperty('--hover-bg-dynamic', '$hoverHex');" +
                    "})(document.documentElement);",
                null,
            )

            // Set base font size
            webView.evaluateJavascript(
                "(function(v) {" +
                    "v = v + 'px';" +
                    "document.documentElement.style.fontSize = v;" +
                    "document.body.style.fontSize = v;" +
                    "document.documentElement.style.transform = 'none';" +
                    "document.documentElement.style.transformOrigin = 'top left';" +
                    "})('$fontSize');",
                null,
            )

            if (headerText.isNotEmpty()) {
                val escapedHeader = org.json.JSONObject.quote(headerText.take(20))
                webView.evaluateJavascript(
                    "window.DictionaryRenderer && window.DictionaryRenderer.renderHeader($escapedHeader);",
                    null,
                )
            }
            
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
        modifier = modifier,
    )
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
    var onAnkiLookup: ((Int, Int?, String?, String?) -> Unit)? = null
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
private var dictionaryBootstrapHtmlCache: String? = null
private val dictionaryBootstrapHtmlLock = Any()

internal fun getDictionaryBootstrapHtml(context: Context): String {
    dictionaryBootstrapHtmlCache?.let { return it }

    synchronized(dictionaryBootstrapHtmlLock) {
        dictionaryBootstrapHtmlCache?.let { return it }

        val css = readTextAsset(context, "dictionary/base.css")
        val js = readTextAsset(context, "dictionary/renderer.js")
            .replace("</script", "<\\/script")

        val html = """
            <!doctype html>
            <html data-theme="light">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1.0">
              <style>$css</style>
              <style id="dictionary-styles"></style>
              <style id="hoshi-custom-css"></style>
            </head>
            <body>
              <main id="entries" class="entries"></main>
              <script>$js</script>
            </body>
            </html>
        """.trimIndent()

        dictionaryBootstrapHtmlCache = html
        return html
    }
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
