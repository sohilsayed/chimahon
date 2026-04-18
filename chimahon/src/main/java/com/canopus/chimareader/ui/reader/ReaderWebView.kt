package com.canopus.chimareader.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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

    // Handle continuous mode changes - Reload the chapter to ensure layout engine resets
    LaunchedEffect(continuousMode) {
        bridge.chapterUrl?.let { url ->
            bridge.send(WebViewCommand.LoadChapter(url, bridge.progress))
        }
    }

    // Handle focus mode changes
    LaunchedEffect(focusMode) {
        bridge.send(WebViewCommand.ChangeFocusMode(focusMode))
    }

    // Handle settings changes
    LaunchedEffect(readerSettings) {
        kotlinx.coroutines.delay(300)
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
                readerSettings = readerSettings, // Pass initial settings
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
                        visibility = View.INVISIBLE
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d("ReaderWebView", "onPageFinished: url=$url, WebView size=${view?.width}x${view?.height}")
                        injectReader()
                    }

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        Log.d("ReaderWebView", "shouldInterceptRequest: url=${request?.url}")
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
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
            
            Log.d("ReaderWebView", "update: isImageOnly=$isImageOnly")

            if (pendingCommands.isEmpty()) {
                return@AndroidView
            }

            val commands = pendingCommands.toList()
            pendingCommands.clear()

            commands.forEach { command ->
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
                        val oldSettings = v.readerSettings
                        val newSettings = command.settings
                        v.readerSettings = newSettings
                        
                        // Layout-critical changes require a full re-injection (reload)
                        if (oldSettings.avoidPageBreak != newSettings.avoidPageBreak || 
                            oldSettings.verticalWriting != newSettings.verticalWriting) {
                            v.injectReader()
                        } else {
                            v.applySettings(newSettings)
                        }
                    }
                    is WebViewCommand.ChangeFocusMode -> {
                        v.focusMode = command.focusMode
                    }
                    else -> { /* ignore other commands for now */ }
                }
            }
        }
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
    private var touchStartY = 0f
    private var touchStartX = 0f
    private var totalMovement = 0f
    var currentUrl: String? = null
    var pendingProgress: Double = 0.0

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val start = e1 ?: return false
                val deltaX = e2.x - start.x
                val deltaY = e2.y - start.y
                
                val isVerticalSwipe = kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)
                
                // Continuous horizontal text overflows vertically, requiring vertical swipes
                val expectsVerticalSwipe = continuousMode && !readerSettings.verticalWriting
                
                if (isVerticalSwipe != expectsVerticalSwipe) {
                    return false
                }
                
                val primaryDelta = if (isVerticalSwipe) deltaY else deltaX
                val primaryVelocity = if (isVerticalSwipe) velocityY else velocityX
                
                if (kotlin.math.abs(primaryDelta) < swipeThreshold.toFloat()) {
                    return false
                }
                if (kotlin.math.abs(primaryVelocity) < 600f) {
                    return false
                }

                return if (expectsVerticalSwipe) {
                    // Up/Down continuous: Swiping up (deltaY < 0) goes Forward
                    handleSwipe(forward = deltaY < 0f)
                } else if (readerSettings.verticalWriting) {
                    // Vertical (Japanese RTL): Swiping Left (deltaX < 0) goes Backward, Swiping Right goes Forward
                    handleSwipe(forward = deltaX > 0f)
                } else {
                    // Horizontal Paged (Western LTR): Swiping Left (deltaX < 0) goes Forward
                    handleSwipe(forward = deltaX < 0f)
                }
            }
        }
    )

    private val jsBridge = ReaderJavascriptBridge(
        onRestoreCompleted = {
            post {
                alpha = 0f
                visibility = View.VISIBLE
                animate().cancel()
                animate()
                    .alpha(1f)
                    .setDuration(160)
                    .start()
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
                    
                    // 1. HUD Toggle on top/bottom tapZonePx (e.g. 64dp)
                    if (eventY < tapZonePx || eventY > height - tapZonePx) {
                        onTapTop()
                    } 
                    // 2. Navigation on side tap zones
                    else if (eventX < width * (readerSettings.tapZonePercent / 100f)) {
                        // Left Tap: RTL Forward, LTR Backward
                        handleSwipe(forward = readerSettings.verticalWriting)
                    } else if (eventX > width * (1f - readerSettings.tapZonePercent / 100f)) {
                        // Right Tap: RTL Backward, LTR Forward
                        handleSwipe(forward = !readerSettings.verticalWriting)
                    }
                }
            }
        }
    )

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        addJavascriptInterface(jsBridge, "HoshiAndroid")
        setBackgroundColor(readerSettings.backgroundColor)
    }

    fun loadChapter(url: String) {
        Log.d("ReaderWebView", "loadChapter called: url=$url, width=$width, height=$height")
        currentUrl = url
        
        if (width <= 0 || height <= 0) {
            postDelayed(Runnable { loadChapter(url) }, 100L)
            return
        }
        
        try {
            visibility = View.INVISIBLE
            
            // Handle file URLs - use loadUrl for proper resource loading
            if (url.startsWith("file://") || !url.contains("://")) {
                val filePath = url.removePrefix("file://")
                val file = File(filePath)
                
                if (file.exists()) {
                    // Use loadUrl to properly load external resources (CSS, images)
                    loadUrl(url)
                } else {
                    Log.e("ReaderWebView", "File does not exist: $filePath")
                    onLoadFailed("File not found: $filePath")
                }
            } else {
                loadUrl(url)
            }
        } catch (error: Exception) {
            Log.e("ReaderWebView", "Failed to load chapter", error)
            onLoadFailed(error.message ?: "Failed to load chapter")
        }
    }

    fun applySettings(settings: ReaderSettings) {
        Log.d("ReaderWebView", "applySettings: fontSize=${settings.fontSize}, theme=${settings.theme}, padding=${settings.horizontalPadding}x${settings.verticalPadding}")
        
        // Trigger full reload on mode switch to prevent layout artifacts
        if (settings.continuousMode != continuousMode) {
            continuousMode = settings.continuousMode
            val url = currentUrl
            if (url != null) {
                // Save current progress before reload
                evaluateJavascript("window.hoshiReader.calculateProgress()") { p ->
                    pendingProgress = p?.toDoubleOrNull() ?: 0.0
                    loadChapter(url)
                }
                return
            }
        }
        
        readerSettings = settings
        val bgColor = String.format("#%06X", 0xFFFFFF and settings.backgroundColor)
        val textColor = String.format("#%06X", 0xFFFFFF and settings.textColor)
        
        setBackgroundColor(settings.backgroundColor)
        
        val script = """
            (function() {
                var b = document.body;
                if (!b) return;
                
                // Font size
                b.style.setProperty('font-size', '${settings.fontSize}px', 'important');
                
                // Line height
                b.style.setProperty('line-height', '${settings.lineHeight}', 'important');
                
                // Apply to wrapper div instead of body
                var wrapper = document.getElementById('hoshi-content-wrapper');
                if (!wrapper) return;
                
                var iw = window.innerWidth;
                var ih = window.innerHeight;
                var hPad = Math.round(iw * ${settings.horizontalPadding} / 100);
                var vPad = Math.round(ih * ${settings.verticalPadding} / 100);
                wrapper.style.setProperty('padding', vPad + 'px ' + hPad + 'px', 'important');
                wrapper.style.setProperty('font-size', '${settings.fontSize}px', 'important');
                
                // Advanced layout settings (line-height, letter-spacing) - only when layoutAdvanced is enabled
                var adv = ${if (settings.layoutAdvanced) "true" else "false"};
                if (adv) {
                    wrapper.style.setProperty('line-height', '${settings.lineHeight}', 'important');
                    wrapper.style.setProperty('letter-spacing', '${settings.characterSpacing}em', 'important');
                }
                
                // Text alignment
                var justify = ${if (settings.justifyText) "true" else "false"};
                wrapper.style.setProperty('text-align', justify ? 'justify' : 'left', 'important');
                
                // Avoid page break inside elements
                var avoidBreak = ${if (settings.avoidPageBreak) "true" else "false"};
                if (avoidBreak) {
                    var style = document.createElement('style');
                    style.textContent = 'img, svg, figure, table, tr, td, th { break-inside: avoid !important; -webkit-column-break-inside: avoid !important; page-break-inside: avoid !important; }';
                    document.head.appendChild(style);
                }
                
                // HideFurigna
                var hideFurigana = ${if (settings.hideFurigana) "true" else "false"};
                var furiganaStyle = document.getElementById('hoshi-furigana-style');
                if (hideFurigana) {
                    if (!furiganaStyle) {
                        furiganaStyle = document.createElement('style');
                        furiganaStyle.id = 'hoshi-furigana-style';
                        furiganaStyle.textContent = 'rt { display: none !important; }';
                        document.head.appendChild(furiganaStyle);
                    }
                } else if (furiganaStyle) {
                    furiganaStyle.remove();
                }

                // Vertical writing mode
                var vw = ${if (settings.verticalWriting) "true" else "false"};
                if (vw) {
                    document.body.style.setProperty('writing-mode', 'vertical-rl', 'important');
                } else {
                    document.body.style.setProperty('writing-mode', 'horizontal-tb', 'important');
                }
                
                // Add @font-face for custom fonts
                var fontUrl = '${settings.fontUrl}';
                if (fontUrl && fontUrl.length > 0) {
                    var fontFace = document.createElement('style');
                    fontFace.textContent = "@font-face { font-family: 'HoshiCustomFont'; src: url('" + fontUrl + "'); }";
                    document.head.appendChild(fontFace);
                    
                    document.fonts.ready.then(function() {
                        wrapper.style.setProperty('font-family', 'HoshiCustomFont', 'important');
                    });
                } else {
                    var fontFamily = '${settings.selectedFont}';
                    if (fontFamily === 'System Serif') fontFamily = 'serif';
                    else if (fontFamily === 'System Sans-Serif') fontFamily = 'sans-serif';
                    wrapper.style.setProperty('font-family', fontFamily, 'important');
                }
                wrapper.style.setProperty('color', tc, 'important');
                
                // Theme / colors - preset themes override custom colors
                var bg, tc;
                if ('${settings.theme}' === 'dark') {
                    bg = '#1a1a1a';
                    tc = '#ffffff';
                } else if ('${settings.theme}' === 'sepia') {
                    bg = '#f4ecd8';
                    tc = '#5b4636';
                } else if ('${settings.theme}' === 'light') {
                    bg = '#ffffff';
                    tc = '#000000';
                } else {
                    bg = '${bgColor}';
                    tc = '${textColor}';
                }
                
                b.style.setProperty('background-color', bg, 'important');
                wrapper.style.setProperty('color', tc, 'important');
                
                document.documentElement.style.setProperty('background-color', bg, 'important');
            })();
        """.trimIndent()
        
        evaluateJavascript(script, null)
    }

    fun injectReader() {
        Log.d("ReaderWebView", "injectReader called: width=$width, height=$height, continuousMode=$continuousMode, isImageOnly=$isImageOnly")
        
        if (height <= 0 || width <= 0) {
            post { injectReader() }
            return
        }

        val css = """
            html { margin: 0 !important; padding: 0 !important; }
            html::-webkit-scrollbar { display: none !important; }
            
            img { max-width: 100%; height: auto; }
            svg { max-width: 100%; height: auto; }
            p { break-inside: avoid; }
        """.trimIndent()

        val script = when {
            isImageOnly -> buildImageOnlyScript(css)
            continuousMode -> buildContinuousScript(css)
            else -> buildPagedScript(css)
        }

        evaluateJavascript(script, null)
    }

    /**
     * Image-only chapter: a single full-viewport image, no column pagination.
     * Any swipe immediately triggers a chapter change.
     */
    private fun buildImageOnlyScript(css: String): String {
        val bg = when (readerSettings.theme) {
            "dark" -> "#1a1a1a"
            "sepia" -> "#f4ecd8"
            "light" -> "#ffffff"
            else -> "#${String.format("%06X", 0xFFFFFF and readerSettings.backgroundColor)}"
        }
        return """
            (function() {
                console.log('[hoshi] buildImageOnlyScript started');

                var vp = document.querySelector('meta[name="viewport"]');
                if (vp) vp.remove();
                var nvp = document.createElement('meta');
                nvp.name = 'viewport';
                nvp.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
                document.head.appendChild(nvp);

                var w = window.innerWidth || screen.availWidth || screen.width;
                var h = window.innerHeight || screen.availHeight || screen.height;

                // Lock html/body using absolute CSS pixel bounds
                document.documentElement.style.cssText =
                    'margin:0!important;padding:0!important;' +
                    'width:' + w + 'px!important;height:' + h + 'px!important;' +
                    'overflow:hidden!important;background:${bg}!important;';
                document.body.style.cssText =
                    'margin:0!important;padding:0!important;' +
                    'width:' + w + 'px!important;height:' + h + 'px!important;' +
                    'display:flex!important;align-items:center!important;justify-content:center!important;' +
                    'background:${bg}!important;touch-action:none!important;' +
                    'overflow:hidden!important;';

                function notifyReady() {
                    if (window.HoshiAndroid) window.HoshiAndroid.restoreCompleted();
                }

                // Find the target image or SVG
                var target = document.querySelector('img, svg');
                console.log('[hoshi] Target: ' + (target ? (target.tagName + ' src=' + (target.src || target.getAttribute('xlink:href'))) : 'NONE'));

                if (target) {
                    // Ensure all parents span the full viewport
                    var curr = target.parentElement;
                    while (curr && curr !== document.body) {
                        curr.style.setProperty('display', 'block', 'important');
                        curr.style.setProperty('margin', '0', 'important');
                        curr.style.setProperty('padding', '0', 'important');
                        curr.style.setProperty('width', w + 'px', 'important');
                        curr.style.setProperty('height', h + 'px', 'important');
                        curr.style.setProperty('overflow', 'hidden', 'important');
                        curr = curr.parentElement;
                    }

                    if (target.tagName.toLowerCase() === 'svg') {
                        // SVG: set preserveAspectRatio so it scales to fit, not fill.
                        // object-fit has no effect on inline SVG; this attribute does.
                        target.setAttribute('preserveAspectRatio', 'xMidYMid meet');
                        target.style.cssText =
                            'width:' + w + 'px!important;height:' + h + 'px!important;' +
                            'max-width:' + w + 'px!important;max-height:' + h + 'px!important;' +
                            'display:block!important;margin:auto!important;padding:0!important;';
                        // Also fix any inner <image> element that may have hardcoded dimensions
                        var innerImg = target.querySelector('image');
                        if (innerImg) {
                            innerImg.setAttribute('width', '100%');
                            innerImg.setAttribute('height', '100%');
                            innerImg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
                        }
                        // SVGs render synchronously — notify immediately
                        notifyReady();
                    } else {
                        // <img>: object-fit:contain keeps proportions
                        target.style.cssText =
                            'width:' + w + 'px!important;height:' + h + 'px!important;' +
                            'max-width:' + w + 'px!important;max-height:' + h + 'px!important;' +
                            'object-fit:contain!important;display:block!important;' +
                            'margin:auto!important;padding:0!important;';

                        if (target.complete && target.naturalWidth > 0) {
                            // Already decoded (cached) — notify now
                            notifyReady();
                        } else {
                            // Wait for the image to fully load before revealing the WebView
                            target.onload  = function() { notifyReady(); };
                            target.onerror = function() { notifyReady(); }; // show even on error
                        }
                    }

                    var bounds = target.getBoundingClientRect();
                    console.log('[hoshi] Target bounds: ' + bounds.width + 'x' + bounds.height + ' tagName=' + target.tagName);
                } else {
                    console.log('[hoshi] Target not found');
                    notifyReady(); // no image — reveal immediately
                }
            })();
        """.trimIndent()
    }

    private fun buildContinuousScript(css: String): String {
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
                document.documentElement.style.setProperty('height', ih + 'px', 'important');

                var s = document.getElementById('hoshi-style');
                if (s) s.remove();
                s = document.createElement('style');
                s.id = 'hoshi-style';
                s.textContent = ${jsString(css)};
                document.head.appendChild(s);

                $readerJs

                var b = document.body;
                if (!b) { window.hoshiReader.notifyRestoreComplete(); return; }

                var ih = window.innerHeight;
                var iw = window.innerWidth;
                var wrapper = document.createElement('div');
                wrapper.id = 'hoshi-content-wrapper';
                while (b.firstChild) { wrapper.appendChild(b.firstChild); }
                b.appendChild(wrapper);

                var hPad = Math.round(iw * ${readerSettings.horizontalPadding} / 100);
                var vPad = Math.round(ih * ${readerSettings.verticalPadding} / 100);
                wrapper.style.setProperty('padding', vPad + 'px ' + hPad + 'px', 'important');
                wrapper.style.setProperty('font-size', '${readerSettings.fontSize}px', 'important');


                var adv = ${if (readerSettings.layoutAdvanced) "true" else "false"};
                if (adv) {
                    wrapper.style.setProperty('line-height', '${readerSettings.lineHeight}', 'important');
                    wrapper.style.setProperty('letter-spacing', '${readerSettings.characterSpacing}em', 'important');
                }
                
                // Text alignment
                var justify = ${if (readerSettings.justifyText) "true" else "false"};
                wrapper.style.setProperty('text-align', justify ? 'justify' : 'left', 'important');
                
                // Avoid page break inside elements
                var avoidBreak = ${if (readerSettings.avoidPageBreak) "true" else "false"};
                if (avoidBreak) {
                    var abStyle = document.createElement('style');
                    abStyle.textContent = 'img, svg, figure, table, tr, td, th { break-inside: avoid !important; -webkit-column-break-inside: avoid !important; page-break-inside: avoid !important; }';
                    document.head.appendChild(abStyle);
                }
                
                // Add @font-face for custom fonts before setting font-family
                var fontUrl = '${readerSettings.fontUrl}';
                console.log('[hoshi] Font loading - url: ' + fontUrl + ', font: ${readerSettings.selectedFont}');
                if (fontUrl && fontUrl.length > 0) {
                    var fontFace = document.createElement('style');
                    fontFace.textContent = "@font-face { font-family: 'HoshiCustomFont'; src: url('" + fontUrl + "'); }";
                    document.head.appendChild(fontFace);
                    console.log('[hoshi] Font-face rule added');
                    
                    document.fonts.ready.then(function() {
                        wrapper.style.setProperty('font-family', 'HoshiCustomFont', 'important');
                    });
                } else {
                    var fontFamily = '${readerSettings.selectedFont}';
                    if (fontFamily === 'System Serif') fontFamily = 'serif';
                    else if (fontFamily === 'System Sans-Serif') fontFamily = 'sans-serif';
                    wrapper.style.setProperty('font-family', fontFamily, 'important');
                }
                
                // Theme colors - preset themes override custom colors
                var bg, tc;
                if ('${readerSettings.theme}' === 'dark') { 
                    bg = '#1a1a1a'; tc = '#ffffff'; 
                } else if ('${readerSettings.theme}' === 'sepia') { 
                    bg = '#f4ecd8'; tc = '#5b4636'; 
                } else if ('${readerSettings.theme}' === 'light') { 
                    bg = '#ffffff'; tc = '#000000'; 
                } else {
                    // System or custom - use custom colors
                    bg = '#${String.format("%06X", 0xFFFFFF and readerSettings.backgroundColor)}';
                    tc = '#${String.format("%06X", 0xFFFFFF and readerSettings.textColor)}';
                }
                b.style.setProperty('background-color', bg, 'important');
                wrapper.style.setProperty('color', tc, 'important');
                document.documentElement.style.setProperty('background-color', bg, 'important');
                
                // Furigana hiding
                var hideFurigana = ${if (readerSettings.hideFurigana) "true" else "false"};
                if (hideFurigana) {
                    var fStyle = document.createElement('style');
                    fStyle.id = 'hoshi-furigana-style';
                    fStyle.textContent = 'rt { display: none !important; }';
                    document.head.appendChild(fStyle);
                }
                
                // Continuous mode: free scroll based on writing mode
                var vw = ${if (readerSettings.verticalWriting) "true" else "false"};
                if (vw) {
                    b.style.setProperty('writing-mode', 'vertical-rl', 'important');
                    b.style.setProperty('touch-action', 'pan-x', 'important');
                    document.documentElement.style.setProperty('overflow-x', 'auto', 'important');
                    document.documentElement.style.setProperty('overflow-y', 'hidden', 'important');
                } else {
                    b.style.setProperty('writing-mode', 'horizontal-tb', 'important');
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
                window.hoshiReader.restoreProgress($pendingProgress);
            })();
            """.trimIndent()
    }

    private fun buildPagedScript(css: String): String {
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
                document.documentElement.style.setProperty('height', ih + 'px', 'important');

                var s = document.getElementById('hoshi-style');
                if (s) s.remove();
                s = document.createElement('style');
                s.id = 'hoshi-style';
                s.textContent = ${jsString(css)};
                document.head.appendChild(s);

                $readerJs

                var b = document.body;
                if (!b) { window.hoshiReader.notifyRestoreComplete(); return; }

                var ih = window.innerHeight;
                var iw = window.innerWidth;
                var wrapper = document.createElement('div');
                wrapper.id = 'hoshi-content-wrapper';
                while (b.firstChild) { wrapper.appendChild(b.firstChild); }
                b.appendChild(wrapper);

                var hPad = Math.round(iw * ${readerSettings.horizontalPadding} / 100);
                var vPad = Math.round(ih * ${readerSettings.verticalPadding} / 100);
                wrapper.style.setProperty('padding', vPad + 'px ' + hPad + 'px', 'important');
                wrapper.style.setProperty('font-size', '${readerSettings.fontSize}px', 'important');

                // Advanced layout settings
                var adv = ${if (readerSettings.layoutAdvanced) "true" else "false"};
                if (adv) {
                    wrapper.style.setProperty('line-height', '${readerSettings.lineHeight}', 'important');
                    wrapper.style.setProperty('letter-spacing', '${readerSettings.characterSpacing}em', 'important');
                }

                // Text alignment
                var justify = ${if (readerSettings.justifyText) "true" else "false"};
                wrapper.style.setProperty('text-align', justify ? 'justify' : 'left', 'important');
                
                // Avoid page break inside elements
                var avoidBreak = ${if (readerSettings.avoidPageBreak) "true" else "false"};
                if (avoidBreak) {
                    var abStyle = document.createElement('style');
                    abStyle.textContent = 'img, svg, figure, table, tr, td, th { break-inside: avoid !important; -webkit-column-break-inside: avoid !important; page-break-inside: avoid !important; }';
                    document.head.appendChild(abStyle);
                }
                
                // Furigana hiding
                var hideFurigana = ${if (readerSettings.hideFurigana) "true" else "false"};
                if (hideFurigana) {
                    var fStyle = document.createElement('style');
                    fStyle.id = 'hoshi-furigana-style';
                    fStyle.textContent = 'rt { display: none !important; }';
                    document.head.appendChild(fStyle);
                }

                var fontUrl = '${readerSettings.fontUrl}';
                if (fontUrl && fontUrl.length > 0) {
                    var fontFace = document.createElement('style');
                    fontFace.textContent = "@font-face { font-family: 'HoshiCustomFont'; src: url('" + fontUrl + "'); }";
                    document.head.appendChild(fontFace);
                    document.fonts.ready.then(function() {
                        wrapper.style.setProperty('font-family', 'HoshiCustomFont', 'important');
                    });
                } else {
                    var ff = '${readerSettings.selectedFont}';
                    if (ff === 'System Serif') ff = 'serif';
                    else if (ff === 'System Sans-Serif') ff = 'sans-serif';
                    wrapper.style.setProperty('font-family', ff, 'important');
                }

                var bg, tc;
                if ('${readerSettings.theme}' === 'dark') { bg = '#1a1a1a'; tc = '#ffffff'; }
                else if ('${readerSettings.theme}' === 'sepia') { bg = '#f4ecd8'; tc = '#5b4636'; }
                else if ('${readerSettings.theme}' === 'light') { bg = '#ffffff'; tc = '#000000'; }
                else {
                    bg = '#${String.format("%06X", 0xFFFFFF and readerSettings.backgroundColor)}';
                    tc = '#${String.format("%06X", 0xFFFFFF and readerSettings.textColor)}';
                }
                b.style.setProperty('background-color', bg, 'important');
                wrapper.style.setProperty('color', tc, 'important');
                document.documentElement.style.setProperty('background-color', bg, 'important');

                var vw = ${if (readerSettings.verticalWriting) "true" else "false"};
                if (vw) {
                    b.style.setProperty('writing-mode', 'vertical-rl', 'important');
                    b.style.setProperty('column-width', ih + 'px', 'important');
                } else {
                    b.style.setProperty('writing-mode', 'horizontal-tb', 'important');
                    b.style.setProperty('column-width', iw + 'px', 'important');
                }
                b.style.setProperty('box-sizing', 'border-box', 'important');
                b.style.setProperty('width', iw + 'px', 'important');
                b.style.setProperty('min-height', ih + 'px', 'important');
                b.style.setProperty('column-fill', 'auto', 'important');
                b.style.setProperty('column-gap', '0px', 'important');
                b.style.setProperty('touch-action', 'none', 'important');
                document.documentElement.style.setProperty('overflow', 'hidden', 'important');

                window.hoshiReader.registerCopyText();
                window.hoshiReader.restoreProgress($pendingProgress, vw);
            })();
        """.trimIndent()
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle tap logic first, before gesture detector
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
                    evaluateJavascript("if (window.hoshiReader && window.hoshiReader.handleTap) { window.hoshiReader.handleTap($cssX, $cssY); }", null)
                }
                performClick()
            }
        }
        
        val gestureHandled = gestureDetector.onTouchEvent(event)
        val webViewHandled = super.onTouchEvent(event)
        return gestureHandled || webViewHandled
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return super.onGenericMotionEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun handleSwipe(forward: Boolean): Boolean {
        return when {
            isImageOnly -> {
                // Image-only chapter = single page, any swipe changes chapter
                val changed = if (forward) onNextChapter() else onPreviousChapter()
                if (changed) visibility = View.INVISIBLE
                true
            }
            continuousMode -> navigateContinuous(forward)
            else -> if (forward) navigate("forward", onNextChapter)
                     else navigate("backward", onPreviousChapter)
        }
    }

    /**
     * For continuous mode: check via JS whether we are at the scroll boundary.
     * If yes, call the chapter callback; if not, let the WebView handle the scroll.
     */
    private fun navigateContinuous(forward: Boolean): Boolean {
        val script = """
            (function() {
                var el = document.scrollingElement || document.documentElement;
                var ph = window.innerHeight;
                var pw = window.innerWidth;
                var vOver = el.scrollHeight - ph > 1;
                var hOver = el.scrollWidth  - pw > 1;
                if (vOver) {
                    var y = Math.round(window.scrollY);
                    var maxY = el.scrollHeight - ph;
                    if ('$forward' === 'true')  return y >= maxY - 2 ? 'limit' : 'scrolling';
                    if ('$forward' === 'false') return y <= 2       ? 'limit' : 'scrolling';
                }
                if (hOver) {
                    var x = window.scrollX;
                    var maxX = el.scrollWidth - pw;
                    var absX = Math.abs(x);
                    if ('$forward' === 'true')  return absX >= maxX - 2 ? 'limit' : 'scrolling';
                    if ('$forward' === 'false') return absX <= 2        ? 'limit' : 'scrolling';
                }
                return 'limit';
            })()
        """.trimIndent()

        evaluateJavascript(script) { result ->
            if (result?.trim('"') == "limit") {
                val changed = if (forward) onNextChapter() else onPreviousChapter()
                if (changed) visibility = View.INVISIBLE
            }
            // else: still content to scroll — the WebView's own fling handles it
        }
        return true
    }

    private fun navigate(direction: String, fallback: () -> Boolean): Boolean {
        val script = """
            (function() {
                if (!window.hoshiReader || typeof window.hoshiReader.paginate !== 'function') {
                    return "limit";
                }
                return window.hoshiReader.paginate('$direction');
            })()
        """.trimIndent()

        evaluateJavascript(script) { result ->
            if (result?.trim('"') == "scrolled") {
                evaluateJavascript(
                    "(function() { return window.hoshiReader.calculateProgress(); })()"
                ) { progressResult ->
                    progressResult
                        ?.trim()
                        ?.trim('"')
                        ?.toDoubleOrNull()
                        ?.let {
                        pendingProgress = it
                        onProgressChanged(it)
                    }
                }
            } else {
                val chapterChanged = fallback()
                if (chapterChanged) {
                    visibility = View.INVISIBLE
                }
            }
        }
        return true
    }
}

private class ReaderJavascriptBridge(
    private val onRestoreCompleted: () -> Unit,
    private val onTextSelectedCallback: (word: String, sentence: String, x: Float, y: Float) -> Unit = { _, _, _, _ -> },
    private val onBackgroundTap: (x: Float, y: Float) -> Unit = { _, _ -> },
) {
    @JavascriptInterface
    fun restoreCompleted() {
        onRestoreCompleted()
    }

    @JavascriptInterface
    fun onTextSelected(word: String, sentence: String, x: Float, y: Float) {
        if (word.isNotBlank()) onTextSelectedCallback.invoke(word, sentence, x, y)
    }

    @JavascriptInterface
    fun onBackgroundTap(x: Float, y: Float) {
        onBackgroundTap.invoke(x, y)
    }
}

private fun loadAssetText(context: Context, path: String): String {
    return context.assets.open(path).use { input ->
        BufferedReader(input.reader()).readText()
    }
}

private fun jsString(value: String): String {
    return buildString {
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
}