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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import chimahon.DictionaryStyle
import chimahon.LookupResult
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.StringWriter
import java.nio.charset.StandardCharsets

private const val ANKI_SCHEME = "anki"
private const val ANKI_PATH_ADD = "add"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DictionaryEntryWebView(
    results: List<LookupResult>,
    styles: List<DictionaryStyle>,
    mediaDataUris: Map<String, String>,
    placeholder: String,
    headerText: String = "",
    popupScale: Int = 100,
    showFrequencyHarmonic: Boolean = false,
    groupTerms: Boolean = true,
    existingExpressions: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
    webViewProvider: ((Context) -> WebView)? = null,
    onAnkiLookup: ((Int, Int?) -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = MaterialTheme.colorScheme
    val accentHex = "#%06X".format(0xFFFFFF and colorScheme.primary.toArgb())
    val onAccentHex = "#%06X".format(0xFFFFFF and colorScheme.onPrimary.toArgb())
    val fgHex = "#%06X".format(0xFFFFFF and colorScheme.onSurface.toArgb())
    val bgHex = "#%06X".format(0xFFFFFF and colorScheme.surface.toArgb())
    val borderHex = "#%06X".format(0xFFFFFF and colorScheme.outline.toArgb())

    val payload = remember(context, results, styles, mediaDataUris, placeholder, isDark, showFrequencyHarmonic, groupTerms, existingExpressions) {
        val buildStart = SystemClock.elapsedRealtime()
        val result = buildRenderPayload(context, results, styles, mediaDataUris, placeholder, isDark, showFrequencyHarmonic, groupTerms, existingExpressions)
        Log.i(
            "DictionaryRender",
            "payload_build_ms=${SystemClock.elapsedRealtime() - buildStart} results=${results.size}",
        )
        result
    }

    AndroidView<WebView>(
        factory = { ctx: Context ->
            val webView = webViewProvider?.invoke(ctx) ?: WebView(ctx)

            if (webView.tag == null) {
                val state = DictionaryWebViewState()
                webView.apply {
                    setBackgroundColor(0x00000000)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.blockNetworkLoads = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false

                    disableSafeBrowsingForDictionary(this)

                    // Payload bridge for efficient data transfer
                    addJavascriptInterface(state.bridge, "PayloadBridge")

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val url = request?.url ?: return false
                            // Intercept anki://add?index=0&glossary=0 URLs
                            if (url.scheme == ANKI_SCHEME && url.host == ANKI_PATH_ADD) {
                                val index = url.getQueryParameter("index")?.toIntOrNull()
                                val glossary = url.getQueryParameter("glossary")?.toIntOrNull()
                                val s = view?.tag as? DictionaryWebViewState
                                if (index != null && index >= 0) {
                                    s?.onAnkiLookup?.invoke(index, glossary)
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
                                    addToAnki: function(index, glossary) {
                                        var url = "anki://add?index=" + index;
                                        if (glossary !== undefined && glossary !== null) {
                                            url += "&glossary=" + glossary;
                                        }
                                        window.location.href = url;
                                    }
                                };
                                """.trimIndent(),
                                null,
                            )
                            s.flush(view)
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

            webView
        },
        update = { webView: WebView ->
            val state = webView.tag as? DictionaryWebViewState ?: return@AndroidView
            state.onAnkiLookup = onAnkiLookup
            state.pendingPayload = payload

            val theme = if (isDark) "dark" else "light"
            webView.evaluateJavascript(
                "const r = document.documentElement;" +
                    "r.dataset.theme = '$theme';" +
                    "r.style.setProperty('--accent', '$accentHex');" +
                    "r.style.setProperty('--on-accent', '$onAccentHex');" +
                    "r.style.setProperty('--fg-dynamic', '$fgHex');" +
                    "r.style.setProperty('--bg-dynamic', '$bgHex');" +
                    "r.style.setProperty('--border-dynamic', '$borderHex');",
                null,
            )

            val scale = popupScale / 100f
            webView.evaluateJavascript(
                "document.documentElement.style.transform = 'scale($scale)';" +
                    "document.documentElement.style.transformOrigin = 'top left';",
                null,
            )

            if (headerText.isNotEmpty()) {
                val escapedHeader = headerText.take(20).replace("'", "\\'").replace("\"", "\\\"")
                webView.evaluateJavascript(
                    "window.DictionaryRenderer && window.DictionaryRenderer.renderHeader('$escapedHeader');",
                    null,
                )
            }

            if (state.pageReady) {
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

private class DictionaryWebViewState(
    val bridge: PayloadBridge = PayloadBridge(),
) {
    var pageReady: Boolean = false
    var onAnkiLookup: ((Int, Int?) -> Unit)? = null
    var lastPayload: String? = null
    var pendingPayload: String? = null

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
    existingExpressions: Set<String> = emptySet(),
): String {
    val buffer = StringWriter(4096)
    JsonWriter(buffer).use { w ->
        w.beginObject()

        // Dictionary Priority Order (Titles)
        val dictionaryPreferences = Injekt.get<DictionaryPreferences>()
        val currentOrder = dictionaryPreferences.dictionaryOrder().get()
        val orderedTitles = currentOrder.split(",")
            .filter { it.isNotBlank() }
            .map { getDictionaryTitle(context, it) }

        w.name("dictionaryOrder").beginArray()
        for (title in orderedTitles) {
            w.value(title)
        }
        w.endArray()

        w.name("ankiEnabled").value(dictionaryPreferences.ankiEnabled().get())

        w.name("placeholder").value(placeholder)
        w.name("isDark").value(isDark)
        w.name("showFrequencyHarmonic").value(showFrequencyHarmonic)
        w.name("groupTerms").value(groupTerms)
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

private fun getDictionaryTitle(context: Context, dirName: String): String {
    val dictionariesDir = File(context.getExternalFilesDir(null), "dictionaries")
    val dictDir = File(dictionariesDir, dirName)
    val indexFile = File(dictDir, "index.json")
    if (!indexFile.exists()) return dirName

    return try {
        val json = indexFile.readText()
        org.json.JSONObject(json).optString("title", dirName)
    } catch (e: Exception) {
        dirName
    }
}
