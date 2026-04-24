package com.canopus.chimareader.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.File

@Composable
fun ReaderWebView(
    modifier: Modifier = Modifier,
    bridge: WebViewBridge,
    continuousMode: Boolean = false,
    isImageOnly: Boolean = false,
    readerSettings: ReaderSettings = ReaderSettings(),
    focusMode: Boolean = false,
    onNextChapter: () -> Boolean,
    onPreviousChapter: () -> Boolean,
    onProgressChanged: (Double) -> Unit,
    onLoadFailed: (String) -> Unit,
    onTap: () -> Unit = {},
    onTapTop: () -> Unit = {},
    onTapBottom: () -> Unit = {},
    swipeThreshold: Int = 96,
    tapZonePx: Int = 100,
    isPopupActive: Boolean = false,
    onTextSelected: (word: String, sentence: String, x: Float, y: Float) -> Unit = { _, _, _, _ -> },
) {
    val pendingCommands = remember(bridge) { bridge.pendingCommands }

    LaunchedEffect(bridge.chapterUrl) {
        val chapterUrl = bridge.chapterUrl ?: return@LaunchedEffect
        if (pendingCommands.isEmpty()) {
            bridge.send(WebViewCommand.LoadChapter(chapterUrl, bridge.progress))
        }
    }

    val isFirstContinuous = remember { mutableStateOf(true) }
    LaunchedEffect(continuousMode) {
        if (isFirstContinuous.value) { isFirstContinuous.value = false; return@LaunchedEffect }
        bridge.chapterUrl?.let { url ->
            bridge.send(WebViewCommand.LoadChapter(url, bridge.progress))
        }
    }

    LaunchedEffect(focusMode) {
        bridge.send(WebViewCommand.ChangeFocusMode(focusMode))
    }

    val isFirstComposition = remember { mutableStateOf(true) }
    LaunchedEffect(readerSettings) {
        if (isFirstComposition.value) {
            isFirstComposition.value = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(100)
        bridge.send(WebViewCommand.ApplySettings(readerSettings))
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ReaderAndroidWebView(
                context = context,
                readerJs = loadAssetText(context, "novel/reader.js"),
                continuousMode = continuousMode,
                isImageOnly = isImageOnly,
                readerSettings = readerSettings,
                focusMode = focusMode,
                onNextChapter = onNextChapter,
                onPreviousChapter = onPreviousChapter,
                onProgressChanged = onProgressChanged,
                onLoadFailed = onLoadFailed,
                onTap = onTap,
                onTapTop = { if (!isPopupActive) onTapTop() },
                onTapBottom = { if (!isPopupActive) onTapBottom() },
                isPopupActive = isPopupActive,
                swipeThreshold = swipeThreshold,
                tapZonePx = tapZonePx,
                onTextSelectedCallback = onTextSelected,
            ).apply {
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d("HoshiReader", "${message.message()} [line ${message.lineNumber()}]")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        Log.d("ReaderWebView", "onPageStarted: url=$url")
                        alpha = 0f
                        visibility = View.VISIBLE
                        lastLoadedUrl = null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d("ReaderWebView", "onPageFinished: url=$url, size=${view?.width}x${view?.height}")
                        injectReader()
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: android.webkit.RenderProcessGoneDetail?,
                    ): Boolean {
                        val reason = if (detail?.didCrash() == true) "WebView crashed"
                                     else "WebView killed by system (OOM)"
                        Log.e("ReaderWebView", "onRenderProcessGone: $reason")
                        post {
                            onLoadFailed("Renderer died ($reason). Try disabling hardware acceleration or 'Avoid page breaks'.")
                        }
                        return true
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        Log.e("ReaderWebView", "onReceivedError: url=${request?.url}, error=$error")
                        if (request?.isForMainFrame == true) {
                            onLoadFailed(error?.description?.toString() ?: "Failed to load chapter")
                        }
                    }
                }
            }
        },
        update = { androidWebView ->
            val v = androidWebView as ReaderAndroidWebView
            v.isImageOnly = isImageOnly
            v.continuousMode = continuousMode
            v.readerSettings = readerSettings
            v.focusMode = focusMode
            v.setBackgroundColor(readerSettings.backgroundColor)

            if (pendingCommands.isEmpty()) return@AndroidView

            val commands = pendingCommands.toList()
            pendingCommands.clear()

            val deduped = buildList {
                var lastLoadChapter: WebViewCommand.LoadChapter? = null
                for (cmd in commands) {
                    if (cmd is WebViewCommand.LoadChapter) {
                        lastLoadChapter = cmd
                    } else {
                        lastLoadChapter?.let { add(it) }
                        lastLoadChapter = null
                        add(cmd)
                    }
                }
                lastLoadChapter?.let { add(it) }
            }

            deduped.forEach { command ->
                when (command) {
                    is WebViewCommand.LoadChapter -> {
                        v.pendingProgress = command.progress
                        v.currentUrl = command.url
                        v.loadChapter(command.url)
                    }
                    is WebViewCommand.ChangeMode -> {
                        if (v.currentUrl != null) {
                            v.continuousMode = command.continuous
                            v.loadChapter(v.currentUrl!!)
                        }
                    }
                    is WebViewCommand.ApplySettings -> {
                        val old = v.lastAppliedSettings
                        val new = command.settings
                        v.readerSettings = new
                        v.lastAppliedSettings = new

                        if (old.avoidPageBreak != new.avoidPageBreak ||
                            old.verticalWriting != new.verticalWriting
                        ) {
                            v.injectReader()
                        } else {
                            v.applySettings(new)
                        }
                    }
                    is WebViewCommand.ChangeFocusMode -> {
                        v.focusMode = command.focusMode
                    }
                    is WebViewCommand.Paginate -> {
                        v.paginate(command.forward)
                    }
                    else -> {}
                }
            }
        },
    )
}

private class ReaderAndroidWebView(
    context: Context,
    private val readerJs: String,
    var continuousMode: Boolean = false,
    var isImageOnly: Boolean = false,
    var readerSettings: ReaderSettings = ReaderSettings(),
    var focusMode: Boolean = false,
    private val onNextChapter: () -> Boolean,
    private val onPreviousChapter: () -> Boolean,
    private val onProgressChanged: (Double) -> Unit,
    private val onLoadFailed: (String) -> Unit,
    private val onTap: () -> Unit = {},
    private val onTapTop: () -> Unit = {},
    private val onTapBottom: () -> Unit = {},
    private val swipeThreshold: Int = 96,
    private val tapZonePx: Int = 100,
    var isPopupActive: Boolean = false,
    private val onTextSelectedCallback: (word: String, sentence: String, x: Float, y: Float) -> Unit = { _, _, _, _ -> },
) : WebView(context) {

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var totalMovement = 0f

    var currentUrl: String? = null
    var pendingProgress: Double = 0.0
    var lastAppliedSettings: ReaderSettings = readerSettings

    internal var lastLoadedUrl: String? = null
    internal var lastLoadedWidth: Int = -1
    internal var lastLoadedHeight: Int = -1
    internal var lastLoadedVerticalWriting: Boolean? = null

    private var lastProgressReportTime = 0L
    private val reportProgressRunnable = Runnable {
        if (continuousMode && !isImageOnly) {
            evaluateJavascript("(function() { return window.hoshiReader.calculateProgress(); })()") { p ->
                p?.trim()?.trim('"')?.toDoubleOrNull()?.let {
                    pendingProgress = it
                    onProgressChanged(it)
                }
            }
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (continuousMode && !isImageOnly) {
            val now = System.currentTimeMillis()
            if (now - lastProgressReportTime > 1000L) {
                lastProgressReportTime = now
                removeCallbacks(reportProgressRunnable)
                post(reportProgressRunnable)
            } else {
                removeCallbacks(reportProgressRunnable)
                postDelayed(reportProgressRunnable, 1000L)
            }
        }
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                val start = e1 ?: return false
                val deltaX = e2.x - start.x
                val deltaY = e2.y - start.y
                val isVerticalSwipe = kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)
                val expectsVerticalSwipe = continuousMode && !readerSettings.verticalWriting
                if (isVerticalSwipe != expectsVerticalSwipe) return false

                val primaryDelta = if (isVerticalSwipe) deltaY else deltaX
                val primaryVelocity = if (isVerticalSwipe) velocityY else velocityX
                if (kotlin.math.abs(primaryDelta) < swipeThreshold.toFloat()) return false
                if (kotlin.math.abs(primaryVelocity) < 400f) return false

                return if (expectsVerticalSwipe) {
                    handleSwipe(forward = deltaY < 0f)
                } else if (readerSettings.verticalWriting) {
                    // Vertical RTL: swipe right → forward
                    handleSwipe(forward = deltaX > 0f)
                } else {
                    // Horizontal LTR: swipe left → forward
                    handleSwipe(forward = deltaX < 0f)
                }
            }
        },
    )

    private val jsBridge = ReaderJavascriptBridge(
        onRestoreCompleted = {
            post {
                alpha = 0f
                visibility = View.VISIBLE
                animate().cancel()
                animate().alpha(1f).setDuration(160).start()
            }
        },
        onTextSelectedCallback = { word, sentence, x, y ->
            if (!isPopupActive) {
                post {
                    val density = context.resources.displayMetrics.density
                    onTextSelectedCallback(word, sentence, x * density, y * density)
                }
            }
        },
        onBackgroundTap = { x, y ->
            if (!isPopupActive) {
                post {
                    val density = context.resources.displayMetrics.density
                    val eventX = x * density
                    val eventY = y * density
                    when {
                        eventY < tapZonePx || eventY > height - tapZonePx -> onTapTop()
                        eventX < width * (readerSettings.tapZonePercent / 100f) ->
                            handleSwipe(forward = readerSettings.verticalWriting)
                        eventX > width * (1f - readerSettings.tapZonePercent / 100f) ->
                            handleSwipe(forward = !readerSettings.verticalWriting)
                    }
                }
            }
        },
    )

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        addJavascriptInterface(jsBridge, "HoshiAndroid")
        setBackgroundColor(readerSettings.backgroundColor)
    }

    fun loadChapter(url: String) {
        Log.d("ReaderWebView", "loadChapter: url=$url size=${width}x${height}")
        currentUrl = url

        if (width <= 0 || height <= 0) {
            postDelayed({ loadChapter(url) }, 100L)
            return
        }

        val vw = readerSettings.verticalWriting
        if (url == lastLoadedUrl &&
            width == lastLoadedWidth &&
            height == lastLoadedHeight &&
            vw == lastLoadedVerticalWriting
        ) {
            Log.d("ReaderWebView", "loadChapter skipped: duplicate")
            return
        }
        lastLoadedUrl = url
        lastLoadedWidth = width
        lastLoadedHeight = height
        lastLoadedVerticalWriting = vw

        visibility = View.INVISIBLE

        try {
            if (url.startsWith("file://") || !url.contains("://")) {
                val file = File(url.removePrefix("file://"))
                if (file.exists()) loadUrl(url)
                else {
                    Log.e("ReaderWebView", "File not found: $url")
                    onLoadFailed("File not found: $url")
                }
            } else {
                loadUrl(url)
            }
        } catch (e: Exception) {
            Log.e("ReaderWebView", "loadChapter error", e)
            onLoadFailed(e.message ?: "Failed to load chapter")
        }
    }

    private fun buildBaseCSS(): String = buildString {
        val vw = readerSettings.verticalWriting

        if (vw) {
            appendLine("html, body { writing-mode: vertical-rl !important; }")
        } else {
            appendLine("html, body { writing-mode: horizontal-tb !important; }")
        }

        appendLine("p { margin-top: 0 !important; margin-bottom: 0 !important; }")

        appendLine("img { max-width: 100%; height: auto; }")
        appendLine("svg { max-width: 100%; height: auto; }")

        appendLine("body * { font-family: inherit !important; }")
    }

    fun injectReader() {
        Log.d("ReaderWebView", "injectReader: ${width}x${height} continuous=$continuousMode imageOnly=$isImageOnly")

        if (height <= 0 || width <= 0) {
            post { injectReader() }
            return
        }

        val script = when {
            isImageOnly -> buildImageOnlyScript()
            continuousMode -> buildContinuousScript()
            else -> buildPagedScript()
        }
        evaluateJavascript(script, null)
    }

    private fun buildImageOnlyScript(): String {
        val bg = readerSettings.resolvedBgHex()
        return """
            (function() {
                var vp = document.querySelector('meta[name="viewport"]');
                if (vp) vp.remove();
                var nvp = document.createElement('meta');
                nvp.name = 'viewport';
                nvp.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                document.head.appendChild(nvp);

                window.hoshiReader = {
                    handleTap: function(clientX, clientY) {
                        if (window.HoshiAndroid && window.HoshiAndroid.onBackgroundTap)
                            window.HoshiAndroid.onBackgroundTap(clientX, clientY);
                        return false;
                    },
                    paginate: function(direction) {
                        return 'limit';
                    },
                    calculateProgress: function() {
                        return 0;
                    }
                };

                var w = window.innerWidth;
                var h = window.innerHeight;

                document.documentElement.style.cssText =
                    'margin:0!important;padding:0!important;' +
                    'width:' + w + 'px!important;height:' + h + 'px!important;' +
                    'overflow:hidden!important;background:$bg!important;';

                if (!document.body) {
                    if (window.HoshiAndroid) window.HoshiAndroid.restoreCompleted();
                    return;
                }

                var target = document.querySelector('img, svg');
                if (!target) {
                    if (window.HoshiAndroid) window.HoshiAndroid.restoreCompleted();
                    return;
                }

                Array.from(document.body.children).forEach(function(child) {
                    if (!child.contains(target)) child.style.display = 'none';
                });

                document.body.style.cssText =
                    'margin:0!important;padding:0!important;' +
                    'width:' + w + 'px!important;height:' + h + 'px!important;' +
                    'display:flex!important;align-items:center!important;justify-content:center!important;' +
                    'background:$bg!important;touch-action:none!important;overflow:hidden!important;';

                var curr = target.parentElement;
                while (curr && curr !== document.body) {
                    curr.style.cssText =
                        'display:flex!important;align-items:center!important;justify-content:center!important;' +
                        'margin:0!important;padding:0!important;border:none!important;' +
                        'width:' + w + 'px!important;height:' + h + 'px!important;overflow:hidden!important;';
                    curr = curr.parentElement;
                }

                var imgStyle =
                    'width:' + w + 'px!important;height:' + h + 'px!important;' +
                    'max-width:' + w + 'px!important;max-height:' + h + 'px!important;' +
                    'object-fit:contain!important;display:block!important;margin:auto!important;padding:0!important;';

                if (target.tagName.toLowerCase() === 'svg') {
                    target.setAttribute('preserveAspectRatio', 'xMidYMid meet');
                    target.style.cssText = imgStyle;
                    if (window.HoshiAndroid) window.HoshiAndroid.restoreCompleted();
                } else {
                    target.style.cssText = imgStyle;
                    if (target.complete && target.naturalWidth > 0) {
                        if (window.HoshiAndroid) window.HoshiAndroid.restoreCompleted();
                    } else {
                        target.onload  = function() { if (window.HoshiAndroid) window.HoshiAndroid.restoreCompleted(); };
                        target.onerror = function() { if (window.HoshiAndroid) window.HoshiAndroid.restoreCompleted(); };
                    }
                }
            })();
        """.trimIndent()
    }

    private fun buildContinuousScript(): String {
        val vw = readerSettings.verticalWriting
        val css = buildBaseCSS()
        val bg = readerSettings.resolvedBgHex()
        val tc = readerSettings.resolvedTextHex()

        return """
            (function() {
                window.webkit = window.webkit || {};
                window.webkit.messageHandlers = window.webkit.messageHandlers || {};
                window.webkit.messageHandlers.restoreCompleted = {
                    postMessage: function(_) {
                        if (window.HoshiAndroid) window.HoshiAndroid.restoreCompleted();
                    }
                };

                var vp = document.querySelector('meta[name="viewport"]');
                if (vp) vp.remove();
                var nvp = document.createElement('meta');
                nvp.name = 'viewport';
                nvp.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                document.head.appendChild(nvp);

                var ih = window.innerHeight;
                var iw = window.innerWidth;

                var s = document.getElementById('hoshi-style');
                if (s) s.remove();
                s = document.createElement('style');
                s.id = 'hoshi-style';
                s.textContent = ${jsString(css)};
                document.head.appendChild(s);

                $readerJs

                var b = document.body;
                if (!b) { window.hoshiReader.notifyRestoreComplete(); return; }

                // Ensure content wrapper existence for consistent styling.
                var wrapper = document.getElementById('hoshi-content-wrapper');
                if (!wrapper) {
                    wrapper = document.createElement('div');
                    wrapper.id = 'hoshi-content-wrapper';
                    while (b.firstChild) wrapper.appendChild(b.firstChild);
                    b.appendChild(wrapper);
                }

                var hPad = Math.round(iw * ${readerSettings.horizontalPadding} / 100);
                var vPad = Math.round(ih * ${readerSettings.verticalPadding} / 100);
                wrapper.style.setProperty('padding', vPad + 'px ' + hPad + 'px', 'important');
                wrapper.style.setProperty('-webkit-box-decoration-break', 'clone', 'important');
                wrapper.style.setProperty('box-decoration-break', 'clone', 'important');
                b.style.setProperty('padding', '0', 'important');

                wrapper.style.setProperty('font-size', '${readerSettings.fontSize}px', 'important');
                ${if (readerSettings.layoutAdvanced) """
                wrapper.style.setProperty('line-height', '${readerSettings.lineHeight}', 'important');
                wrapper.style.setProperty('letter-spacing', '${readerSettings.characterSpacing}em', 'important');
                """ else ""}
                wrapper.style.setProperty('text-align', ${if (readerSettings.justifyText) "'justify'" else "'left'"}, 'important');

                ${fontJS(readerSettings, "wrapper")}
                ${themeJS(bg, tc)}
                ${furiganaJS(readerSettings)}

                var vw = ${if (vw) "true" else "false"};
                if (vw) {
                    b.style.setProperty('touch-action', 'pan-x', 'important');
                    document.documentElement.style.setProperty('overflow-x', 'auto', 'important');
                    document.documentElement.style.setProperty('overflow-y', 'hidden', 'important');
                } else {
                    b.style.setProperty('touch-action', 'pan-y', 'important');
                    document.documentElement.style.setProperty('overflow-x', 'hidden', 'important');
                    document.documentElement.style.setProperty('overflow-y', 'auto', 'important');
                }
                b.style.setProperty('box-sizing', 'border-box', 'important');
                b.style.setProperty('width', iw + 'px', 'important');
                b.style.setProperty('min-height', ih + 'px', 'important');
                document.documentElement.style.setProperty('height', '100%', 'important');

                window.hoshiReader.registerCopyText();
                window.hoshiReader.continuousMode = true;
                window.hoshiReader.restoreProgress($pendingProgress, ${if (vw) "true" else "false"});
            })();
        """.trimIndent()
    }

    private fun buildPagedScript(): String {
        val vw = readerSettings.verticalWriting
        val css = buildBaseCSS()
        val bg = readerSettings.resolvedBgHex()
        val tc = readerSettings.resolvedTextHex()

        return """
            (function() {
                window.webkit = window.webkit || {};
                window.webkit.messageHandlers = window.webkit.messageHandlers || {};
                window.webkit.messageHandlers.restoreCompleted = {
                    postMessage: function(_) {
                        if (window.HoshiAndroid) window.HoshiAndroid.restoreCompleted();
                    }
                };

                var vp = document.querySelector('meta[name="viewport"]');
                if (vp) vp.remove();
                var nvp = document.createElement('meta');
                nvp.name = 'viewport';
                nvp.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                document.head.appendChild(nvp);

                var ih = window.innerHeight;
                var iw = window.innerWidth;

                var s = document.getElementById('hoshi-style');
                if (s) s.remove();
                s = document.createElement('style');
                s.id = 'hoshi-style';
                var imgMaxH = Math.round(ih * 0.85);
                var imgMaxW = Math.round(iw * 0.90);
                s.textContent = ${jsString(css)} +
                    'img, svg { max-width: ' + imgMaxW + 'px !important; max-height: ' + imgMaxH + 'px !important; object-fit: contain !important; }';
                document.head.appendChild(s);

                $readerJs

                var b = document.body;
                if (!b) { window.hoshiReader.notifyRestoreComplete(); return; }

                // Ensure content wrapper existence for consistent styling.
                var wrapper = document.getElementById('hoshi-content-wrapper');
                if (!wrapper) {
                    wrapper = document.createElement('div');
                    wrapper.id = 'hoshi-content-wrapper';
                    while (b.firstChild) wrapper.appendChild(b.firstChild);
                    b.appendChild(wrapper);
                }

                var hPad = Math.round(iw * ${readerSettings.horizontalPadding} / 100);
                var vPad = Math.round(ih * ${readerSettings.verticalPadding} / 100);
                wrapper.style.setProperty('padding', vPad + 'px ' + hPad + 'px', 'important');
                wrapper.style.setProperty('-webkit-box-decoration-break', 'clone', 'important');
                wrapper.style.setProperty('box-decoration-break', 'clone', 'important');
                b.style.setProperty('padding', '0', 'important');

                wrapper.style.setProperty('font-size', '${readerSettings.fontSize}px', 'important');
                ${if (readerSettings.layoutAdvanced) """
                wrapper.style.setProperty('line-height', '${readerSettings.lineHeight}', 'important');
                wrapper.style.setProperty('letter-spacing', '${readerSettings.characterSpacing}em', 'important');
                """ else ""}
                wrapper.style.setProperty('text-align', ${if (readerSettings.justifyText) "'justify'" else "'left'"}, 'important');

                ${if (readerSettings.avoidPageBreak) """
                var abStyle = document.createElement('style');
                abStyle.textContent = 'img, svg, figure, table, tr, td, th { break-inside: avoid !important; -webkit-column-break-inside: avoid !important; page-break-inside: avoid !important; }';
                document.head.appendChild(abStyle);
                """ else ""}

                ${fontJS(readerSettings, "wrapper")}
                ${themeJS(bg, tc)}
                ${furiganaJS(readerSettings)}

                var overrideStyle = document.getElementById('hoshi-override-style');
                if (!overrideStyle) {
                    overrideStyle = document.createElement('style');
                    overrideStyle.id = 'hoshi-override-style';
                    document.head.appendChild(overrideStyle);
                }
                overrideStyle.textContent = [
                    '[class*="pt"] { margin-top: 0 !important; }',
                    '[class*="pb"] { margin-bottom: 0 !important; }',
                    'span.img.fpage, span.img.fblk { padding-bottom: 0 !important; }',
                    '@page { margin: 0 !important; }'
                ].join(' ');

                document.documentElement.style.setProperty('height', ih + 'px', 'important');
                var vw = ${if (vw) "true" else "false"};
                if (vw) {
                    b.style.setProperty('column-width', ih + 'px', 'important');
                    b.style.setProperty('min-height', ih + 'px', 'important');
                } else {
                    b.style.setProperty('column-width', iw + 'px', 'important');
                    b.style.setProperty('height', ih + 'px', 'important');
                }
                b.style.setProperty('box-sizing', 'border-box', 'important');
                b.style.setProperty('width', iw + 'px', 'important');
                b.style.setProperty('column-fill', 'auto', 'important');
                b.style.setProperty('column-gap', '0px', 'important');
                b.style.setProperty('touch-action', 'none', 'important');
                document.documentElement.style.setProperty('overflow', 'hidden', 'important');

                window.hoshiReader.registerCopyText();
                window.hoshiReader.restoreProgress($pendingProgress, ${if (vw) "true" else "false"});
            })();
        """.trimIndent()
    }

    fun applySettings(settings: ReaderSettings) {
        if (settings.continuousMode != continuousMode) {
            continuousMode = settings.continuousMode
            currentUrl?.let { url ->
                evaluateJavascript("window.hoshiReader.calculateProgress()") { p ->
                    pendingProgress = p?.toDoubleOrNull() ?: 0.0
                    loadChapter(url)
                }
            }
            return
        }

        readerSettings = settings
        val bg = settings.resolvedBgHex()
        val tc = settings.resolvedTextHex()
        setBackgroundColor(settings.backgroundColor)

        val script = """
            (function() {
                var b = document.body;
                if (!b) return;
                var wrapper = document.getElementById('hoshi-content-wrapper');
                if (!wrapper) return;

                var iw = window.innerWidth;
                var ih = window.innerHeight;
                var hPad = Math.round(iw * ${settings.horizontalPadding} / 100);
                var vPad = Math.round(ih * ${settings.verticalPadding} / 100);
                wrapper.style.setProperty('padding', vPad + 'px ' + hPad + 'px', 'important');
                wrapper.style.setProperty('-webkit-box-decoration-break', 'clone', 'important');
                wrapper.style.setProperty('box-decoration-break', 'clone', 'important');
                b.style.setProperty('padding', '0', 'important');

                wrapper.style.setProperty('font-size', '${settings.fontSize}px', 'important');
                b.style.setProperty('font-size', '${settings.fontSize}px', 'important');

                ${if (settings.layoutAdvanced) """
                wrapper.style.setProperty('line-height', '${settings.lineHeight}', 'important');
                wrapper.style.setProperty('letter-spacing', '${settings.characterSpacing}em', 'important');
                """ else ""}

                wrapper.style.setProperty('text-align', ${if (settings.justifyText) "'justify'" else "'left'"}, 'important');

                ${fontJS(settings, "wrapper")}

                b.style.setProperty('background-color', '$bg', 'important');
                wrapper.style.setProperty('color', '$tc', 'important');
                document.documentElement.style.setProperty('background-color', '$bg', 'important');

                ${furiganaJS(settings)}
            })();
        """.trimIndent()

        evaluateJavascript(script, null)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                totalMovement = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                totalMovement += kotlin.math.abs(event.x - touchStartX) + kotlin.math.abs(event.y - touchStartY)
                touchStartX = event.x
                touchStartY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (totalMovement < 20f) {
                    val cssX = event.x / resources.displayMetrics.density
                    val cssY = event.y / resources.displayMetrics.density
                    evaluateJavascript(
                        "if (window.hoshiReader && window.hoshiReader.handleTap) { window.hoshiReader.handleTap($cssX, $cssY); }",
                        null,
                    )
                }
                performClick()
            }
        }
        val gestureHandled = gestureDetector.onTouchEvent(event)
        val webViewHandled = super.onTouchEvent(event)
        return gestureHandled || webViewHandled
    }

    override fun performClick(): Boolean = super.performClick()

    private fun handleSwipe(forward: Boolean): Boolean {
        return when {
            isImageOnly -> {
                val changed = if (forward) onNextChapter() else onPreviousChapter()
                if (changed) visibility = View.INVISIBLE
                true
            }
            continuousMode -> navigateContinuous(forward)
            else -> navigate(if (forward) "forward" else "backward", if (forward) onNextChapter else onPreviousChapter)
        }
    }

    fun paginate(forward: Boolean) {
        if (isImageOnly) {
            val changed = if (forward) onNextChapter() else onPreviousChapter()
            if (changed) visibility = View.INVISIBLE
            return
        }

        val direction = if (forward) "forward" else "backward"
        evaluateJavascript("""
            (function() {
                if (!window.hoshiReader || typeof window.hoshiReader.paginate !== 'function') return 'limit';
                return window.hoshiReader.paginate('$direction');
            })()
        """.trimIndent()) { result ->
            val res = result?.trim('"')
            if (res == "scrolled") {
                updateProgressFromJs()
            } else {
                val changed = if (forward) onNextChapter() else onPreviousChapter()
                if (changed) visibility = View.INVISIBLE
            }
        }
    }

    private fun updateProgressFromJs() {
        evaluateJavascript("window.hoshiReader.calculateProgress()") { p ->
            p?.trim()?.trim('"')?.toDoubleOrNull()?.let {
                pendingProgress = it
                onProgressChanged(it)
            }
        }
    }

    private fun navigateContinuous(forward: Boolean): Boolean {
        val isVerticalScroll = !readerSettings.verticalWriting
        val script = """
            (function() {
                var el = document.scrollingElement || document.documentElement;
                var ph = window.innerHeight;
                var pw = window.innerWidth;
                var tol = 15; 
                var isV = $isVerticalScroll;

                if (isV) {
                    var y = Math.max(window.scrollY, document.documentElement.scrollTop, document.body.scrollTop);
                    var maxY = el.scrollHeight - ph;
                    if (maxY <= 5) return 'limit'; 
                    if ('$forward' === 'true')  return y >= maxY - tol ? 'limit' : 'scrolling';
                    if ('$forward' === 'false') return y <= tol        ? 'limit' : 'scrolling';
                } else {
                    var x = Math.max(Math.abs(window.scrollX), Math.abs(document.documentElement.scrollLeft), Math.abs(document.body.scrollLeft));
                    var maxX = el.scrollWidth - pw;
                    if (maxX <= 5) return 'limit';
                    if ('$forward' === 'true')  return x >= maxX - tol ? 'limit' : 'scrolling';
                    if ('$forward' === 'false') return x <= tol        ? 'limit' : 'scrolling';
                }
                return 'scrolling';
            })()
        """.trimIndent()
        
        evaluateJavascript(script) { result ->
            if (result?.trim('"') == "limit") {
                val changed = if (forward) onNextChapter() else onPreviousChapter()
                if (changed) visibility = View.INVISIBLE
            }
        }
        return true
    }

    private fun navigate(direction: String, fallback: () -> Boolean): Boolean {
        evaluateJavascript("""
            (function() {
                if (!window.hoshiReader || typeof window.hoshiReader.paginate !== 'function') return 'limit';
                return window.hoshiReader.paginate('$direction');
            })()
        """.trimIndent()) { result ->
            if (result?.trim('"') == "scrolled") {
                updateProgressFromJs()
            } else {
                val changed = fallback()
                if (changed) visibility = View.INVISIBLE
            }
        }
        return true
    }
}

// JS snippet construction helpers shared across modes

private fun fontJS(settings: ReaderSettings, wrapperVar: String): String = buildString {
    val fontUrl = settings.fontUrl
    if (!fontUrl.isNullOrBlank()) {
        appendLine("""
            var fontFace = document.createElement('style');
            fontFace.textContent = "@font-face { font-family: 'HoshiCustomFont'; src: url('${jsEscape(fontUrl)}'); }";
            document.head.appendChild(fontFace);
            document.fonts.ready.then(function() {
                $wrapperVar.style.setProperty('font-family', 'HoshiCustomFont', 'important');
            });
        """.trimIndent())
    } else {
        var ff = settings.selectedFont
        if (ff == "System Serif") ff = "serif"
        else if (ff == "System Sans-Serif") ff = "sans-serif"
        appendLine("$wrapperVar.style.setProperty('font-family', '${jsEscape(ff)}', 'important');")
    }
}

private fun themeJS(bg: String, tc: String): String = """
    b.style.setProperty('background-color', '$bg', 'important');
    wrapper.style.setProperty('color', '$tc', 'important');
    document.documentElement.style.setProperty('background-color', '$bg', 'important');
""".trimIndent()

private fun furiganaJS(settings: ReaderSettings): String = if (settings.hideFurigana) """
    var furiganaStyle = document.getElementById('hoshi-furigana-style');
    if (!furiganaStyle) {
        furiganaStyle = document.createElement('style');
        furiganaStyle.id = 'hoshi-furigana-style';
        furiganaStyle.textContent = 'rt { display: none !important; }';
        document.head.appendChild(furiganaStyle);
    }
""".trimIndent() else """
    var furiganaStyle = document.getElementById('hoshi-furigana-style');
    if (furiganaStyle) furiganaStyle.remove();
""".trimIndent()

// ReaderSettings extension helpers

private fun ReaderSettings.resolvedBgHex(): String = when (theme) {
    "dark"  -> "#1a1a1a"
    "sepia" -> "#f4ecd8"
    "light" -> "#ffffff"
    else    -> "#${String.format("%06X", 0xFFFFFF and backgroundColor)}"
}

private fun ReaderSettings.resolvedTextHex(): String = when (theme) {
    "dark"  -> "#ffffff"
    "sepia" -> "#5b4636"
    "light" -> "#000000"
    else    -> "#${String.format("%06X", 0xFFFFFF and textColor)}"
}

// JavaScript Bridge implementation

private class ReaderJavascriptBridge(
    private val onRestoreCompleted: () -> Unit,
    private val onTextSelectedCallback: (word: String, sentence: String, x: Float, y: Float) -> Unit = { _, _, _, _ -> },
    private val onBackgroundTap: (x: Float, y: Float) -> Unit = { _, _ -> },
) {
    @JavascriptInterface fun restoreCompleted() = onRestoreCompleted()
    @JavascriptInterface fun onTextSelected(word: String, sentence: String, x: Float, y: Float) {
        if (word.isNotBlank()) onTextSelectedCallback(word, sentence, x, y)
    }
    @JavascriptInterface fun onBackgroundTap(x: Float, y: Float) {
        onBackgroundTap.invoke(x, y)
    }
}

// Generic utilities

private fun loadAssetText(context: Context, path: String): String =
    context.assets.open(path).use { BufferedReader(it.reader()).readText() }

/** Escapes a string for safe embedding inside a JS single-quoted string literal. */
private fun jsString(value: String): String = buildString {
    append('\'')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('\'')
}

/** Escapes a value for inline use inside an already-quoted JS string (no surrounding quotes added). */
private fun jsEscape(value: String): String = value
    .replace("\\", "\\\\")
    .replace("'", "\\'")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
