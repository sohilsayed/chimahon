package eu.kanade.tachiyomi.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.tachiyomi.ui.player.controls.GestureHandler
import eu.kanade.tachiyomi.ui.player.controls.PlayerControlsOverlay
import eu.kanade.tachiyomi.ui.player.controls.SubtitleTapOverlay
import eu.kanade.tachiyomi.ui.player.controls.components.BrightnessVolumeIndicator
import eu.kanade.tachiyomi.ui.player.controls.components.StatsOverlay
import eu.kanade.tachiyomi.ui.player.mpv.MPVView
import eu.kanade.tachiyomi.ui.player.setting.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onMPVViewCreated: (MPVView) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onBack: () -> Unit,
    onSelectSubtitle: (Int) -> Unit = {},
    onSelectAudio: (Int) -> Unit = {},
    onSubtitleWordTapped: (word: String, fullText: String, charOffset: Int, anchorX: Float, anchorY: Float) -> Unit = { _, _, _, _, _ -> },
    onBrightnessChange: (Float) -> Unit = {},
    onVolumeChange: (Float) -> Unit = {},
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    onSetSpeed: (Float) -> Unit = {},
    onSetAspectRatio: (VideoAspect) -> Unit = {},
    onRotateScreen: () -> Unit = {},
    onNavigatePrevious: () -> Unit = {},
    onNavigateNext: () -> Unit = {},
    onAddSubtitleFile: () -> Unit = {},
    lookupContent: @Composable () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val showLoadingCircle = remember { playerPreferences.showLoadingCircle().get() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { context ->
                MPVView(context).also(onMPVViewCreated)
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.isInPipMode) return@Box

        GestureHandler(
            isLocked = state.isLocked,
            subtitleActive = state.currentSubText.isNotBlank(),
            currentPositionSec = state.currentPositionSec,
            durationSec = state.durationSec,
            onToggleControls = { viewModel.toggleControls() },
            onSeekRelative = onSeekRelative,
            onPlayPause = onPlayPause,
            onBrightnessChange = onBrightnessChange,
            onVolumeChange = onVolumeChange,
            onSeekTo = onSeekTo,
            onSeekStart = onSeekStart,
            onSeekEnd = onSeekEnd,
        )

        if (state.isLoading && showLoadingCircle) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        BrightnessVolumeIndicator(
            isBrightness = true,
            value = state.currentBrightness,
            maxValue = 1f,
            visible = state.showBrightnessSlider,
        )
        BrightnessVolumeIndicator(
            isBrightness = false,
            value = state.currentVolume,
            maxValue = state.maxVolume,
            visible = state.showVolumeSlider,
        )

        if (state.showStats) {
            StatsOverlay()
        }

        if (state.lookupState == null) {
            PlayerControlsOverlay(
                state = state,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onBack = onBack,
                onSelectSubtitle = onSelectSubtitle,
                onSelectAudio = onSelectAudio,
                onToggleLock = { viewModel.toggleLock() },
                onSetSpeed = onSetSpeed,
                onCycleAspectRatio = {
                    viewModel.cycleAspectRatio()
                    onSetAspectRatio(viewModel.state.value.aspectRatio)
                },
                onRotateScreen = onRotateScreen,
                onNavigatePrevious = onNavigatePrevious,
                onNavigateNext = onNavigateNext,
                onAddSubtitleFile = onAddSubtitleFile,
                onHideControls = { viewModel.hideControls() },
                onToggleStats = { viewModel.toggleStats() },
                onLoadEpisodeAt = { viewModel.loadEpisodeAt(it) },
                onSkipIntro = { viewModel.onSkipIntro() },
                onSelectChapter = { viewModel.selectChapter(it) },
                onStartTimer = { viewModel.startTimer(it) },
                onCancelTimer = { viewModel.cancelTimer() },
            )
        }

        lookupContent()

        if (state.currentSubText.isNotBlank()) {
            SubtitleTapOverlay(
                subText = state.currentSubText,
                onWordTapped = onSubtitleWordTapped,
                highlightRange = state.highlightRange,
            )
        }
    }
}
