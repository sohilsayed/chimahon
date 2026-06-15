package eu.kanade.tachiyomi.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.media.session.MediaSession
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import eu.kanade.tachiyomi.ui.player.setting.AudioPreferences
import eu.kanade.tachiyomi.ui.player.setting.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.setting.PlayerOrientation
import eu.kanade.tachiyomi.ui.player.setting.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.setting.SubtitlePreferences
import eu.kanade.tachiyomi.ui.player.utils.TrackSelect
import eu.kanade.tachiyomi.ui.reader.viewer.OcrLookupPopup
import eu.kanade.tachiyomi.data.torrentServer.service.TorrentServerService
import eu.kanade.tachiyomi.torrentServer.TorrentServerApi
import eu.kanade.tachiyomi.torrentServer.TorrentServerUtils
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private var popupWebView: WebView? = null

    private val dictionaryPreferences: DictionaryPreferences by lazy { Injekt.get() }
    private val dictionaryRepository: chimahon.DictionaryRepository by lazy { Injekt.get() }

    private val subtitlePreferences: SubtitlePreferences by lazy { Injekt.get() }
    private val audioPreferences: AudioPreferences by lazy { Injekt.get() }
    private val decoderPreferences: DecoderPreferences by lazy { Injekt.get() }
    private val trackSelect by lazy { TrackSelect() }
    private var mediaSession: MediaSession? = null
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var restoreAudioFocus: () -> Unit = {}
    private var hideSliderJob: Job? = null
    private var aniSkipFetched = false
    private var pausedByBackgrounding = false

    private val subtitleFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            val name = uri.lastPathSegment ?: "External"
            val fd = contentResolver.openFileDescriptor(uri, "r")?.detachFd() ?: return@registerForActivityResult
            val path = "fdclose://$fd"
            mpvView?.addSubtitleTrack(path, name)
            viewModel.saveCustomSubtitle(uri.toString(), name)
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
                PIP_PLAY -> mpvView?.resume()
                PIP_PAUSE -> mpvView?.pause()
                PIP_PREVIOUS -> viewModel.loadPreviousEpisode()
                PIP_NEXT -> viewModel.loadNextEpisode()
            }
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                mpvView?.pause()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

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

        initBrightnessVolume()
        setupPlayerAudio()
        setupMediaSession()
        registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
        registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            RECEIVER_NOT_EXPORTED,
        )

        setContent {
            PlayerScreen(
                viewModel = viewModel,
                onMPVViewCreated = ::onMPVViewReady,
                onPlayPause = ::togglePlayPause,
                onSeek = ::seekToPercent,
                onSeekRelative = ::seekRelative,
                onSeekTo = ::seekToSeconds,
                onBack = ::handleBack,
                onSelectSubtitle = ::selectSubtitleTrack,
                onSelectAudio = ::selectAudioTrack,
                onSubtitleWordTapped = ::onSubtitleWordTapped,
                onBrightnessChange = ::adjustBrightness,
                onVolumeChange = ::adjustVolume,
                onSeekStart = { mpvView?.pause() },
                onSeekEnd = { mpvView?.resume() },
                onSetSpeed = ::setSpeed,
                onSetAspectRatio = { setAspectRatio(it) },
                onRotateScreen = ::rotateScreen,
                onNavigatePrevious = { viewModel.loadPreviousEpisode() },
                onNavigateNext = { viewModel.loadNextEpisode() },
                onAddSubtitleFile = { subtitleFilePicker.launch(arrayOf("*/*")) },
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

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventFlow.collect { event ->
                    when (event) {
                        is PlayerViewModel.Event.Error -> {
                            logcat(LogPriority.ERROR) { "Player error: ${event.message}" }
                            android.widget.Toast.makeText(this@PlayerActivity, event.message, android.widget.Toast.LENGTH_LONG).show()
                            finish()
                        }
                        is PlayerViewModel.Event.PlaybackCompleted -> {}
                        is PlayerViewModel.Event.PauseForLookup -> mpvView?.pause()
                        is PlayerViewModel.Event.ResumeFromLookup -> mpvView?.resume()
                        is PlayerViewModel.Event.EpisodeChanged -> playCurrentEpisode()
                        is PlayerViewModel.Event.ShowToast -> {
                            android.widget.Toast.makeText(this@PlayerActivity, event.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        is PlayerViewModel.Event.SeekTo -> {
                            mpvView?.seekTo(event.positionSec)
                        }
                        is PlayerViewModel.Event.PauseForTimer -> {
                            mpvView?.pause()
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }
        }
    }

    private fun onMPVViewReady(view: MPVView) {
        mpvView = view

        val configDir = filesDir.resolve("mpv").absolutePath
        val cacheDir = cacheDir.resolve("mpv").absolutePath
        logcat(LogPriority.DEBUG, tag = "Player") { "Initializing MPV" }
        view.initialize(configDir, cacheDir, decoderPreferences, audioPreferences, subtitlePreferences)
        logcat(LogPriority.DEBUG, tag = "Player") { "MPV initialized" }

        val advancedPrefs: eu.kanade.tachiyomi.ui.player.setting.AdvancedPlayerPreferences = Injekt.get()
        val mpvConf = advancedPrefs.mpvConf().get()
        if (mpvConf.isNotBlank()) {
            mpvConf.lines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    MPVLib.setOptionString(parts[0].trim(), parts[1].trim())
                } else {
                    MPVLib.setOptionString(parts[0].trim(), "")
                }
            }
        }

        val observer = PlayerObserver(
            onPositionChanged = { pos -> viewModel.updatePosition(pos) },
            onDurationChanged = { dur ->
                viewModel.updateDuration(dur)
                if (dur > 0 && !aniSkipFetched) {
                    aniSkipFetched = true
                    lifecycleScope.launchIO {
                        viewModel.fetchAniSkipTimestamps(dur.toInt())
                    }
                }
            },
            onPauseChanged = { paused -> viewModel.updatePlaybackState(!paused) },
            onEofReached = { viewModel.onPlaybackCompleted() },
            onSubTextChanged = { text -> viewModel.updateSubText(text) },
            onTrackListChanged = ::onTrackListChanged,
            onChapterListChanged = { viewModel.loadChapters() },
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
            val resolvedVideo = state.resolvedVideo
            val url = resolvedVideo?.videoUrl?.takeIf { it.isNotBlank() }
                ?: episode.url.takeIf { PlayerViewModel.isPlayableScheme(it) }
            logcat(LogPriority.DEBUG, tag = "Player") { "Episode URL: ${episode.url}, resolved: ${resolvedVideo?.videoUrl}" }
            if (url.isNullOrBlank()) {
                logcat(LogPriority.ERROR, tag = "Player") { "No playable video URL found for episode" }
                return@launchIO
            }
            val isTorrent = isTorrentPlaybackUrl(url)
            val title = resolvedVideo?.videoTitle ?: episode.name
            var resolvedTorrentPlayUrl: String? = null
            if (playerPreferences.alwaysUseExternalPlayer().get()) {
                val externalUrl = if (isTorrent) {
                    resolveTorrentUrl(url, title).also { resolvedTorrentPlayUrl = it }
                } else {
                    url
                }
                val extIntent = ExternalIntents.newIntent(
                    this@PlayerActivity, animeId, episodeId, externalUrl, resolvedVideo,
                )
                if (extIntent != null) {
                    withContext(Dispatchers.Main) {
                        startActivity(extIntent)
                        finish()
                    }
                    return@launchIO
                }
            }
            val resumePos = state.currentPositionSec
                .takeIf { it > 0 } ?: episode.lastSecondSeen
            val playUrl = if (isTorrent) {
                resolvedTorrentPlayUrl ?: resolveTorrentUrl(url, title)
            } else {
                resolvePlaybackUrl(url)
            }
            val headers = resolvedVideo?.headers?.let { h ->
                (0 until h.size).associate { i -> h.name(i) to h.value(i) }
            }
            logcat(LogPriority.DEBUG, tag = "Player") { "Playing: $playUrl (resume=$resumePos, headers=${headers?.keys})" }
            withContext(Dispatchers.Main) {
                view.playFile(playUrl, resumePos, headers)
                resolvedVideo?.subtitleTracks?.forEach { track ->
                    view.addSubtitleTrack(track.url, track.lang)
                }
                loadCustomSubtitles(view)
                logcat(LogPriority.DEBUG, tag = "Player") { "playFile called, ${resolvedVideo?.subtitleTracks?.size ?: 0} subtitle tracks added" }
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

    private var userSelectedSubId: Int? = null
    private var userSelectedAudioId: Int? = null

    private fun onTrackListChanged() {
        val view = mpvView ?: return
        val (subs, audio) = view.loadTracks()

        if (userSelectedSubId == null) {
            val preferredSub = trackSelect.getPreferredSubTrack(subs)
            if (preferredSub != null && view.sid != preferredSub.id) {
                view.sid = preferredSub.id
            }
        }

        if (userSelectedAudioId == null) {
            val preferredAudio = trackSelect.getPreferredAudioTrack(audio)
            if (preferredAudio != null && view.aid != preferredAudio.id) {
                view.aid = preferredAudio.id
            }
        }

        viewModel.updateTracks(subs, audio, view.sid, view.aid)
    }

    private fun selectSubtitleTrack(id: Int) {
        val view = mpvView ?: return
        userSelectedSubId = id
        view.sid = id
        viewModel.updateSelectedSubId(id)

        val track = viewModel.state.value.subtitleTracks.firstOrNull { it.id == id }
        subtitlePreferences.preferredSubLanguages().set(track?.language ?: "")
    }

    private fun loadCustomSubtitles(view: MPVView) {
        for (sub in viewModel.getCustomSubtitles()) {
            try {
                val uri = Uri.parse(sub.uri)
                val fd = contentResolver.openFileDescriptor(uri, "r")?.detachFd() ?: continue
                view.addSubtitleTrack("fdclose://$fd", sub.name)
            } catch (_: Exception) {}
        }
    }

    private fun selectAudioTrack(id: Int) {
        val view = mpvView ?: return
        userSelectedAudioId = id
        view.aid = id
        viewModel.updateSelectedAudioId(id)
    }

    private fun setupPlayerAudio() {
        audioPreferences.audioChannels().get().let { MPVLib.setPropertyString(it.property, it.value) }

        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                androidx.media.AudioAttributesCompat.Builder()
                    .setUsage(androidx.media.AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(androidx.media.AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        AudioManagerCompat.requestAudioFocus(audioManager, request).let {
            if (it == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return@let
            audioFocusRequest = request
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
        when (it) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                val oldRestore = restoreAudioFocus
                val wasPlayerPaused = mpvView?.paused ?: true
                mpvView?.pause()
                restoreAudioFocus = {
                    oldRestore()
                    if (!wasPlayerPaused) mpvView?.resume()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", "0.5"))
                restoreAudioFocus = {
                    MPVLib.command(arrayOf("multiply", "volume", "2"))
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                restoreAudioFocus()
                restoreAudioFocus = {}
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "ChimahonPlayer").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { mpvView?.resume() }
                override fun onPause() { mpvView?.pause() }
                override fun onSkipToNext() { viewModel.loadNextEpisode() }
                override fun onSkipToPrevious() { viewModel.loadPreviousEpisode() }
                override fun onStop() {
                    mpvView?.pause()
                    isActive = false
                }
                override fun onSeekTo(pos: Long) { mpvView?.seekTo((pos / 1000).toInt()) }
            })
            isActive = true
        }
        lifecycleScope.launch {
            viewModel.state
                .map { it.isPlaying }
                .distinctUntilChanged()
                .collect { isPlaying ->
                    updateMediaSessionState(isPlaying, viewModel.state.value.currentPositionSec)
                }
        }
    }

    private fun updateMediaSessionState(isPlaying: Boolean, positionSec: Long) {
        val session = mediaSession ?: return
        val pbState = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(pbState, positionSec * 1000, 1f)
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_STOP or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SKIP_TO_NEXT,
                )
                .build(),
        )
    }

    private fun handleBack() {
        if (playerPreferences.pipOnExit().get() && !isInPictureInPictureMode) {
            enterPipMode()
        } else {
            finish()
        }
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

    private fun seekToSeconds(positionSec: Int) {
        mpvView?.seekTo(positionSec)
    }

    private fun initBrightnessVolume() {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        var currentBrightness = window.attributes.screenBrightness.let {
            if (it < 0) 0.5f else it
        }

        if (playerPreferences.rememberPlayerBrightness().get()) {
            val saved = playerPreferences.playerBrightnessValue().get()
            if (saved >= 0f) {
                currentBrightness = saved.coerceIn(0.01f, 1f)
                val params = window.attributes
                params.screenBrightness = currentBrightness
                window.attributes = params
            }
        }

        if (playerPreferences.rememberPlayerVolume().get()) {
            val saved = playerPreferences.playerVolumeValue().get()
            if (saved >= 0f) {
                val restoredVol = saved.coerceIn(0f, maxVol).toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoredVol, 0)
                viewModel.updateVolumeState(restoredVol.toFloat(), maxVol, false)
            } else {
                viewModel.updateVolumeState(currentVol, maxVol, false)
            }
        } else {
            viewModel.updateVolumeState(currentVol, maxVol, false)
        }

        viewModel.updateBrightnessState(currentBrightness, false)
    }

    private fun adjustBrightness(delta: Float) {
        val params = window.attributes
        val current = params.screenBrightness.let { if (it < 0) 0.5f else it }
        val newBrightness = (current + delta * 1.5f).coerceIn(0.01f, 1f)
        params.screenBrightness = newBrightness
        window.attributes = params
        viewModel.updateBrightnessState(newBrightness, true)
        if (playerPreferences.rememberPlayerBrightness().get()) {
            playerPreferences.playerBrightnessValue().set(newBrightness)
        }
        scheduleHideSliders()
    }

    private fun adjustVolume(delta: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val scaledDelta = delta * maxVol * 1.5f
        val newVol = (currentVol + scaledDelta).toInt().coerceIn(0, maxVol)
        if (newVol != currentVol) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        }
        viewModel.updateVolumeState(newVol.toFloat(), maxVol.toFloat(), true)
        if (playerPreferences.rememberPlayerVolume().get()) {
            playerPreferences.playerVolumeValue().set(newVol.toFloat())
        }
        scheduleHideSliders()
    }

    private fun scheduleHideSliders() {
        hideSliderJob?.cancel()
        hideSliderJob = lifecycleScope.launch {
            delay(1500)
            viewModel.hideBrightnessVolumeSliders()
        }
    }

    private fun setSpeed(speed: Float) {
        mpvView?.let {
            MPVLib.setPropertyDouble("speed", speed.toDouble())
        }
        viewModel.setSpeed(speed)
    }

    private fun setAspectRatio(aspect: VideoAspect) {
        mpvView?.let {
            when (aspect) {
                VideoAspect.Fit -> {
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    MPVLib.setPropertyString("video-aspect-override", "-1")
                }
                VideoAspect.Crop -> {
                    MPVLib.setPropertyDouble("panscan", 1.0)
                    MPVLib.setPropertyString("video-aspect-override", "-1")
                }
                VideoAspect.Stretch -> {
                    MPVLib.setPropertyDouble("panscan", 0.0)
                    val w = it.width.toDouble()
                    val h = it.height.toDouble()
                    if (h > 0) MPVLib.setPropertyString("video-aspect-override", "${w / h}")
                }
            }
        }
    }

    private fun rotateScreen() {
        requestedOrientation = when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun playCurrentEpisode() {
        val view = mpvView ?: return
        aniSkipFetched = false
        val state = viewModel.state.value
        val episode = state.episode ?: return
        val resolvedVideo = state.resolvedVideo
        val url = resolvedVideo?.videoUrl?.takeIf { it.isNotBlank() }
            ?: episode.url.takeIf { PlayerViewModel.isPlayableScheme(it) }
        if (url.isNullOrBlank()) return
        val resumePos = episode.lastSecondSeen
        val headers = resolvedVideo?.headers?.let { h ->
            (0 until h.size).associate { i -> h.name(i) to h.value(i) }
        }
        if (isTorrentPlaybackUrl(url)) {
            lifecycleScope.launchIO {
                val playUrl = resolveTorrentUrl(url, resolvedVideo?.videoTitle ?: episode.name)
                withContext(Dispatchers.Main) {
                    view.playFile(playUrl, resumePos, headers)
                    resolvedVideo?.subtitleTracks?.forEach { track ->
                        view.addSubtitleTrack(track.url, track.lang)
                    }
                    loadCustomSubtitles(view)
                }
            }
        } else {
            val playUrl = resolvePlaybackUrl(url)
            view.playFile(playUrl, resumePos, headers)
            resolvedVideo?.subtitleTracks?.forEach { track ->
                view.addSubtitleTrack(track.url, track.lang)
            }
            loadCustomSubtitles(view)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerPreferences.pipOnExit().get() && !isInPictureInPictureMode) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPipMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPipMode, newConfig)
        viewModel.updatePipMode(isInPipMode)
        if (!isInPipMode) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                setupImmersiveMode()
            } else {
                finish()
            }
        }
    }

    private fun enterPipMode() {
        if (!playerPreferences.enablePip().get()) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val state = viewModel.state.value
        val params = PictureInPictureParams.Builder()
            .setActions(
                createPipActions(
                    this,
                    isPaused = !state.isPlaying,
                    hasPrevious = state.currentEpisodeIndex > 0,
                    hasNext = state.currentEpisodeIndex >= 0 &&
                        state.currentEpisodeIndex < state.episodes.size - 1,
                ),
            )
            .setAspectRatio(Rational(16, 9))
            .build()
        enterPictureInPictureMode(params)
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            viewModel.saveProgress()
            if (mpvView?.paused == false) {
                mpvView?.pause()
                pausedByBackgrounding = true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (pausedByBackgrounding) {
            pausedByBackgrounding = false
            mpvView?.resume()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: IllegalArgumentException) {}
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: IllegalArgumentException) {}
        audioFocusRequest?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
        audioFocusRequest = null
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
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
        if (!playerPreferences.playerFullscreen().get()) return
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
            val pfd = contentResolver.openFileDescriptor(uri, "r") ?: return url
            val fd = pfd.detachFd()
            return "fdclose://$fd"
        }
        return url
    }

    private fun resolveTorrentUrl(videoUrl: String, title: String): String {
        TorrentServerService.start()
        TorrentServerService.wait(10)

        if (videoUrl.startsWith("content://", ignoreCase = true)) {
            val inputStream = contentResolver.openInputStream(Uri.parse(videoUrl))
                ?: error("Could not open torrent file")
            inputStream.use {
                val torrent = TorrentServerApi.uploadTorrent(it, title, "", "", false)
                return TorrentServerUtils.getTorrentPlayLink(torrent, 0)
            }
        }

        var index = 0
        if (videoUrl.startsWith("magnet", ignoreCase = true) && videoUrl.contains("index=")) {
            index = try {
                videoUrl.substringAfter("index=").substringBefore("&").toInt()
            } catch (_: NumberFormatException) {
                0
            }
        }

        val torrent = TorrentServerApi.addTorrent(videoUrl, title, "", "", false)
        return TorrentServerUtils.getTorrentPlayLink(torrent, index)
    }

    private fun isTorrentPlaybackUrl(url: String): Boolean {
        return PlayerViewModel.isTorrentUrl(url) || isTorrentContentUri(url)
    }

    private fun isTorrentContentUri(url: String): Boolean {
        if (!url.startsWith("content://", ignoreCase = true)) return false
        val uri = Uri.parse(url)
        val type = runCatching { contentResolver.getType(uri)?.lowercase() }.getOrNull()
        if (type?.contains("bittorrent") == true || type?.contains("torrent") == true) return true

        val displayName = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        return displayName?.endsWith(".torrent", ignoreCase = true) == true
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
