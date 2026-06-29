package eu.kanade.tachiyomi.ui.youtube

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        var progress by remember { mutableIntStateOf(0) }
        var isLoading by remember { mutableStateOf(true) }
        var title by remember { mutableStateOf("YouTube") }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
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
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = settings.userAgentString
                                    ?.replace("wv", "")
                                    ?.replace("; wv", "")

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                        isLoading = newProgress < 100
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
                                        val url = request?.url?.toString() ?: return false
                                        if (isYouTubeVideoUrl(url)) {
                                            val intent = PlayerActivity.newStandaloneIntent(context, Uri.parse(url), "YouTube")
                                            intent.putExtra("youtube_page_url", url)
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        injectInterceptScript(view)
                                    }
                                }

                                addJavascriptInterface(
                                    object {
                                        @JavascriptInterface
                                        fun onVideoClicked(url: String) {
                                            val intent = PlayerActivity.newStandaloneIntent(context, Uri.parse(url), "YouTube")
                                            intent.putExtra("youtube_page_url", url)
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        }
                                    },
                                    "Android",
                                )

                                loadUrl("https://m.youtube.com")
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private fun isYouTubeVideoUrl(url: String): Boolean {
        return Regex("(?:v=|youtu\\.be/|youtube\\.com/(?:shorts|live)/)([a-zA-Z0-9_-]{11})")
            .containsMatchIn(url)
    }

    private fun injectInterceptScript(view: WebView?) {
        val script = """
(function() {
    document.addEventListener('click', function(e) {
        var link = e.target.closest('a');
        if (link && link.href) {
            if (/(?:v=|youtu\.be\/|youtube\.com\/(?:shorts|live)\/)([a-zA-Z0-9_-]{11})/.test(link.href)) {
                e.preventDefault();
                e.stopPropagation();
                Android.onVideoClicked(link.href);
            }
        }
    }, true);
    var pushState = history.pushState;
    history.pushState = function() {
        var ret = pushState.apply(this, arguments);
        window.dispatchEvent(new Event('locationchange'));
        return ret;
    };
    var replaceState = history.replaceState;
    history.replaceState = function() {
        var ret = replaceState.apply(this, arguments);
        window.dispatchEvent(new Event('locationchange'));
        return ret;
    };
    window.addEventListener('locationchange', function() {
        var m = window.location.href.match(/(?:v=|youtu\.be\/|youtube\.com\/(?:shorts|live)\/)([a-zA-Z0-9_-]{11})/);
        if (m) Android.onVideoClicked(window.location.href);
    });
})();
""".trimIndent()
        view?.evaluateJavascript(script, null)
    }
}
