package eu.kanade.tachiyomi.ui.youtube

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.tachiyomi.ui.player.PlayerActivity

class YouTubeBrowserScreen : Screen {

    private companion object {
        private const val PLAYER_LAUNCH_DEBOUNCE_MS = 1_500L
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        var progress by remember { mutableIntStateOf(0) }
        var isLoading by remember { mutableStateOf(true) }
        var title by remember { mutableStateOf("YouTube") }
        var webView by remember { mutableStateOf<WebView?>(null) }

        fun goBack() {
            val view = webView
            if (view?.canGoBack() == true) {
                view.goBack()
            } else {
                navigator.pop()
            }
        }

        BackHandler(onBack = ::goBack)

        DisposableEffect(Unit) {
            onDispose {
                webView?.stopLoading()
                webView?.webChromeClient = null
                webView?.webViewClient = WebViewClient()
                webView?.removeJavascriptInterface("Android")
                webView?.destroy()
                webView = null
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        IconButton(onClick = ::goBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            val mainHandler = Handler(Looper.getMainLooper())
                            var lastPlayerLaunchVideoId = ""
                            var lastPlayerLaunchAt = 0L

                            fun openInPlayer(url: String) {
                                val videoId = getDirectYouTubeVideoId(url) ?: return
                                val now = SystemClock.elapsedRealtime()
                                if (
                                    videoId == lastPlayerLaunchVideoId &&
                                    now - lastPlayerLaunchAt < PLAYER_LAUNCH_DEBOUNCE_MS
                                ) {
                                    return
                                }
                                lastPlayerLaunchVideoId = videoId
                                lastPlayerLaunchAt = now

                                stopYoutubeWebViewPlayback(webView)
                                val intent = PlayerActivity.newStandaloneIntent(context, Uri.parse(url), "YouTube")
                                intent.putExtra("youtube_page_url", url)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }

                            CookieManager.getInstance().setAcceptCookie(true)
                            WebView(ctx).apply {
                                webView = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                }

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.loadsImagesAutomatically = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.setSupportMultipleWindows(false)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    settings.safeBrowsingEnabled = true
                                }
                                settings.userAgentString = settings.userAgentString
                                    ?.replace("wv", "")
                                    ?.replace("; wv", "")

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                        isLoading = newProgress < 100
                                        if (newProgress >= 25) injectInterceptScript(view)
                                        if (newProgress >= 100) {
                                            view?.url?.let { openInPlayer(it) }
                                        }
                                    }

                                    override fun onReceivedTitle(view: WebView?, t: String?) {
                                        title = t ?: "YouTube"
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        if (request?.isForMainFrame == false) return false
                                        val url = request?.url?.toString() ?: return false
                                        if (getDirectYouTubeVideoId(url) != null) {
                                            openInPlayer(url)
                                            return true
                                        }
                                        return false
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        val requestUrl = url ?: return false
                                        if (getDirectYouTubeVideoId(requestUrl) != null) {
                                            openInPlayer(requestUrl)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun doUpdateVisitedHistory(
                                        view: WebView?,
                                        url: String?,
                                        isReload: Boolean,
                                    ) {
                                        val requestUrl = url
                                        if (requestUrl != null && getDirectYouTubeVideoId(requestUrl) != null) {
                                            openInPlayer(requestUrl)
                                            view?.stopLoading()
                                            return
                                        }
                                        injectInterceptScript(view)
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        val requestUrl = url
                                        if (requestUrl != null && getDirectYouTubeVideoId(requestUrl) != null) {
                                            openInPlayer(requestUrl)
                                            view?.stopLoading()
                                            return
                                        }
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        CookieManager.getInstance().flush()
                                        injectInterceptScript(view)
                                    }
                                }

                                addJavascriptInterface(
                                    object {
                                        @JavascriptInterface
                                        fun onVideoClicked(url: String) {
                                            mainHandler.post { openInPlayer(url) }
                                        }
                                    },
                                    "Android",
                                )

                                loadUrl("https://m.youtube.com/")
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun getDirectYouTubeVideoId(url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase().removePrefix("www.")
        val pathSegments = uri.pathSegments.orEmpty()

        val videoId = when {
            host.contains("youtu.be") -> pathSegments.firstOrNull()
            host.contains("youtube.com") || host.contains("youtube-nocookie.com") -> {
                when (pathSegments.firstOrNull()) {
                    "watch" -> uri.getQueryParameter("v")
                    "shorts", "live", "embed", "v" -> pathSegments.getOrNull(1)
                    else -> null
                }
            }
            else -> null
        }

        return videoId?.takeIf { it.isYouTubeVideoId() }
    }

    private fun String.isYouTubeVideoId(): Boolean {
        return length == 11 && all { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    private fun injectInterceptScript(view: WebView?) {
        val script = """
(function() {
    if (window.__mihonYoutubeBridgeInstalled) return;
    window.__mihonYoutubeBridgeInstalled = true;

    function isPlayableUrl(rawUrl) {
        try {
            var url = new URL(rawUrl, window.location.href);
            var host = url.hostname.toLowerCase();
            var path = url.pathname || '';
            return (
                (host.indexOf('youtu.be') !== -1 && /^\/[a-zA-Z0-9_-]{11}$/.test(path)) ||
                ((host.indexOf('youtube.com') !== -1 || host.indexOf('youtube-nocookie.com') !== -1) &&
                    ((url.pathname === '/watch' && /^[a-zA-Z0-9_-]{11}$/.test(url.searchParams.get('v') || '')) ||
                    (/^\/(?:shorts|live|embed|v)\/[a-zA-Z0-9_-]{11}$/.test(path))))
            );
        } catch (e) {
            return false;
        }
    }

    function openInApp(rawUrl) {
        if (!isPlayableUrl(rawUrl) || !window.Android) return false;
        Android.onVideoClicked(new URL(rawUrl, window.location.href).href);
        return true;
    }

    document.addEventListener('click', function(e) {
        var link = e.target && e.target.closest ? e.target.closest('a') : null;
        if (link && link.href && openInApp(link.href)) {
            e.preventDefault();
            e.stopPropagation();
            e.stopImmediatePropagation();
        }
    }, true);
    var pushState = history.pushState;
    history.pushState = function() {
        var ret = pushState.apply(this, arguments);
        setTimeout(function() { openInApp(window.location.href); }, 0);
        return ret;
    };
    var replaceState = history.replaceState;
    history.replaceState = function() {
        var ret = replaceState.apply(this, arguments);
        setTimeout(function() { openInApp(window.location.href); }, 0);
        return ret;
    };
    window.addEventListener('popstate', function() {
        setTimeout(function() { openInApp(window.location.href); }, 0);
    });
    openInApp(window.location.href);
})();
""".trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun stopYoutubeWebViewPlayback(view: WebView?) {
        view?.stopLoading()
        view?.evaluateJavascript(
            """
(function() {
    document.querySelectorAll('video, audio').forEach(function(media) {
        try {
            media.pause();
            media.removeAttribute('src');
            media.load();
        } catch (e) {}
    });
})();
""".trimIndent(),
            null,
        )
    }
}
