package eu.kanade.tachiyomi.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import chimahon.MediaInfo
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryBootstrapHtml
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import eu.kanade.tachiyomi.ui.player.mpv.MPVView
import eu.kanade.tachiyomi.ui.player.mpv.PlayerObserver
import eu.kanade.tachiyomi.ui.player.setting.PlayerOrientation
import eu.kanade.tachiyomi.ui.player.setting.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.OcrLookupPopup
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PlayerActivity : ComponentActivity() {

    private val viewModel by viewModels<PlayerViewModel>()
    private val playerPreferences: PlayerPreferences by lazy { Injekt.get() }

    private var mpvView: MPVView? = null
    private var playerObserver: PlayerObserver? = null

    private var popupWebView: WebView? = null

    private val dictionaryPreferences: DictionaryPreferences by lazy { Injekt.get() }
    private val dictionaryRepository: chimahon.DictionaryRepository by lazy { Injekt.get() }

    @Volatile private var cachedActiveProfile: chimahon.anki.AnkiProfile? = null
    @Volatile private var cachedTermPaths: List<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupImmersiveMode()
        setupScreenFlags()
        setupOrientation()

        lifecycleScope.launchIO {
            val profile = dictionaryPreferences.profileStore.getActiveProfile()
            val termPaths = getDictionaryPaths(this@PlayerActivity, profile)
            cachedActiveProfile = profile
            cachedTermPaths = termPaths
            dictionaryRepository.warmUp(termPaths)
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dictionaryPreferences.rawActiveProfileId().changes().collect {
                    cachedActiveProfile = null
                    cachedTermPaths = null
                }
            }
        }

        setContent {
            PlayerScreen(
                viewModel = viewModel,
                onMPVViewCreated = ::onMPVViewReady,
                onPlayPause = ::togglePlayPause,
                onSeek = ::seekToPercent,
                onSeekRelative = ::seekRelative,
                onBack = { finish() },
                onSelectSubtitle = ::selectSubtitleTrack,
                onSelectAudio = ::selectAudioTrack,
                onSubtitleWordTapped = ::onSubtitleWordTapped,
                lookupContent = {
                    val state by viewModel.state.collectAsState()
                    val ls = state.lookupState
                    if (ls != null) {
                        BackHandler { viewModel.dismissLookup() }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { viewModel.dismissLookup() },
                        )

                        val webView = remember { ensurePopupWebView() }
                        val mediaInfo = state.anime?.let { anime ->
                            MediaInfo(
                                mangaTitle = anime.title,
                                chapterName = state.episode?.name ?: "",
                            )
                        }

                        TachiyomiTheme {
                            OcrLookupPopup(
                                lookupString = ls.word,
                                fullText = ls.sentence,
                                charOffset = ls.charOffset,
                                onDismiss = { viewModel.dismissLookup() },
                                webView = webView,
                                repository = dictionaryRepository,
                                anchorX = ls.anchorX,
                                anchorY = ls.anchorY,
                                isVertical = false,
                                mediaInfo = mediaInfo,
                                onRequestScreenshot = { captureVideoFrame() },
                                onCropTriggered = null,
                                initialLookupDeferred = viewModel.lookupDeferred,
                                usePopup = false,
                                onTermMatched = { charCount ->
                                    viewModel.updateHighlightRange(
                                        ls.charOffset until (ls.charOffset + charCount),
                                    )
                                },
                                modifier = Modifier,
                            )
                        }
                    }
                },
            )
        }

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    is PlayerViewModel.Event.Error -> {
                        logcat(LogPriority.ERROR) { "Player error: ${event.message}" }
                        finish()
                    }
                    is PlayerViewModel.Event.PlaybackCompleted -> {}
                    is PlayerViewModel.Event.PauseForLookup -> mpvView?.pause()
                    is PlayerViewModel.Event.ResumeFromLookup -> mpvView?.resume()
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun onMPVViewReady(view: MPVView) {
        mpvView = view

        val configDir = filesDir.resolve("mpv").absolutePath
        val cacheDir = cacheDir.resolve("mpv").absolutePath
        logcat(LogPriority.DEBUG, tag = "Player") { "Initializing MPV" }
        view.initialize(configDir, cacheDir)
        logcat(LogPriority.DEBUG, tag = "Player") { "MPV initialized" }

        val observer = PlayerObserver(
            onPositionChanged = { pos -> viewModel.updatePosition(pos) },
            onDurationChanged = { dur -> viewModel.updateDuration(dur) },
            onPauseChanged = { paused -> viewModel.updatePlaybackState(!paused) },
            onEofReached = { viewModel.onPlaybackCompleted() },
            onSubTextChanged = { text -> viewModel.updateSubText(text) },
            onTrackListChanged = ::onTrackListChanged,
        )
        playerObserver = observer
        MPVLib.addObserver(observer)

        val animeId = intent.getLongExtra(PlayerViewModel.KEY_ANIME_ID, -1L)
        val episodeId = intent.getLongExtra(PlayerViewModel.KEY_EPISODE_ID, -1L)
        val videoUrl = resolveVideoUrl(intent)

        lifecycleScope.launchIO {
            viewModel.init(animeId, episodeId, videoUrl)

            val state = viewModel.state.value
            val episode = state.episode
            if (episode == null) {
                logcat(LogPriority.ERROR, tag = "Player") { "Episode not loaded after init" }
                return@launchIO
            }
            val url = episode.url
            logcat(LogPriority.DEBUG, tag = "Player") { "Episode URL: $url" }
            if (url.isNotBlank()) {
                val resumePos = state.currentPositionSec
                    .takeIf { it > 0 } ?: episode.lastSecondSeen
                val playUrl = resolvePlaybackUrl(url)
                logcat(LogPriority.DEBUG, tag = "Player") { "Playing: $playUrl (resume=$resumePos)" }
                withContext(Dispatchers.Main) {
                    view.playFile(playUrl, resumePos)
                    logcat(LogPriority.DEBUG, tag = "Player") { "playFile called" }
                }
            }
        }
    }

    private fun getOrRefreshLookupPaths(): Pair<chimahon.anki.AnkiProfile, List<String>> {
        val profile = cachedActiveProfile
            ?: dictionaryPreferences.profileStore.getActiveProfile().also { cachedActiveProfile = it }
        val paths = cachedTermPaths
            ?: getDictionaryPaths(this, profile).also { cachedTermPaths = it }
        return profile to paths
    }

    private fun onSubtitleWordTapped(
        word: String,
        fullText: String,
        charOffset: Int,
        anchorX: Float,
        anchorY: Float,
    ) {
        val (_, termPaths) = getOrRefreshLookupPaths()
        viewModel.startLookup(word, fullText, charOffset, anchorX, anchorY, termPaths)
    }

    private fun captureVideoFrame(): android.graphics.Bitmap? {
        return mpvView?.grabThumbnail(1280)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensurePopupWebView(): WebView {
        return popupWebView ?: WebView(this).also {
            popupWebView = it
            it.settings.javaScriptEnabled = true
            it.settings.domStorageEnabled = true
            it.settings.blockNetworkLoads = true
            it.settings.loadsImagesAutomatically = true
            it.setBackgroundColor(0x00000000)
            it.loadDataWithBaseURL(
                "https://chima.local/popup/",
                getDictionaryBootstrapHtml(this),
                "text/html",
                "utf-8",
                null,
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.state.value.lookupState != null) {
            popupWebView?.let { webView ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            webView.evaluateJavascript("window.DictionaryRenderer?.navigate(-1);", null)
                            return true
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            webView.evaluateJavascript("window.DictionaryRenderer?.navigate(1);", null)
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun onTrackListChanged() {
        val view = mpvView ?: return
        val (subs, audio) = view.loadTracks()

        val preferred = playerPreferences.preferredSubLanguage().get()
        if (preferred.isNotBlank() && subs.isNotEmpty()) {
            val match = subs.firstOrNull { it.language.equals(preferred, ignoreCase = true) }
            if (match != null && view.sid != match.id) {
                view.sid = match.id
            }
        }

        viewModel.updateTracks(subs, audio, view.sid, view.aid)
    }

    private fun selectSubtitleTrack(id: Int) {
        val view = mpvView ?: return
        view.sid = id
        viewModel.updateSelectedSubId(id)

        val track = viewModel.state.value.subtitleTracks.firstOrNull { it.id == id }
        playerPreferences.preferredSubLanguage().set(track?.language ?: "")
    }

    private fun selectAudioTrack(id: Int) {
        val view = mpvView ?: return
        view.aid = id
        viewModel.updateSelectedAudioId(id)
    }

    private fun togglePlayPause() {
        mpvView?.cyclePause()
    }

    private fun seekToPercent(percent: Float) {
        val duration = mpvView?.duration ?: return
        mpvView?.seekTo((percent * duration).toInt())
    }

    private fun seekRelative(deltaSec: Int) {
        mpvView?.seekRelative(deltaSec)
    }

    override fun onStop() {
        super.onStop()
        viewModel.saveProgress()
    }

    override fun onDestroy() {
        playerObserver?.let { MPVLib.removeObserver(it) }
        mpvView?.destroy()
        mpvView = null
        popupWebView?.destroy()
        popupWebView = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun setupImmersiveMode() {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupScreenFlags() {
        if (playerPreferences.keepScreenOn().get()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setupOrientation() {
        val orientation = PlayerOrientation.fromPreference(
            playerPreferences.defaultPlayerOrientation().get(),
        )
        requestedOrientation = orientation.flag
    }

    private fun resolvePlaybackUrl(url: String): String {
        if (url.startsWith("content://")) {
            val uri = Uri.parse(url)
            val fd = contentResolver.openFileDescriptor(uri, "r")?.detachFd()
                ?: return url
            return "fdclose://$fd"
        }
        return url
    }

    private fun resolveVideoUrl(intent: Intent): String? {
        if (intent.action == Intent.ACTION_VIEW) {
            return intent.data?.toString()
        }
        return intent.getStringExtra(EXTRA_VIDEO_URL)
    }

    companion object {
        private const val EXTRA_VIDEO_URL = "video_url"

        fun newIntent(context: Context, animeId: Long, episodeId: Long): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(PlayerViewModel.KEY_ANIME_ID, animeId)
                putExtra(PlayerViewModel.KEY_EPISODE_ID, episodeId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        fun newIntent(context: Context, videoUrl: String): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
