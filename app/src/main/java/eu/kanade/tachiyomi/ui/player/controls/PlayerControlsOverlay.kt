package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import eu.kanade.tachiyomi.ui.player.PlayerViewModel
import eu.kanade.tachiyomi.ui.player.controls.components.CurrentChapter
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.setting.PlayerPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.ChaptersSheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.MoreSheet
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.SpeedSheet
import eu.kanade.tachiyomi.ui.player.mpv.MPVView
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun PlayerControlsOverlay(
    state: PlayerViewModel.State,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onBack: () -> Unit,
    onSelectSubtitle: (Int) -> Unit = {},
    onSelectAudio: (Int) -> Unit = {},
    onToggleLock: () -> Unit = {},
    onSetSpeed: (Float) -> Unit = {},
    onCycleAspectRatio: () -> Unit = {},
    onRotateScreen: () -> Unit = {},
    onNavigatePrevious: () -> Unit = {},
    onNavigateNext: () -> Unit = {},
    onAddSubtitleFile: () -> Unit = {},
    onHideControls: () -> Unit = {},
    onToggleStats: () -> Unit = {},
    onLoadEpisodeAt: (Int) -> Unit = {},
    onSkipIntro: () -> Unit = {},
    onSelectChapter: (Int) -> Unit = {},
    onStartTimer: (Int) -> Unit = {},
    onCancelTimer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showTrackDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEpisodeDialog by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }

    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val controlsTimeout = remember { playerPreferences.playerTimeToDisappear().get().toLong() }
    val reduceMotion = remember { playerPreferences.reduceMotion().get() }
    val showCurrentChapter = remember { playerPreferences.showCurrentChapter().get() }
    val enterAnim = if (reduceMotion) EnterTransition.None else fadeIn()
    val exitAnim = if (reduceMotion) ExitTransition.None else fadeOut()
    LaunchedEffect(state.controlsVisible, state.isPlaying, state.isLocked) {
        if (state.controlsVisible && state.isPlaying && !state.isLocked) {
            delay(controlsTimeout)
            onHideControls()
        }
    }

    if (state.isLocked) {
        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = enterAnim,
            exit = exitAnim,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                IconButton(onClick = onToggleLock) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = stringResource(MR.strings.player_unlock),
                        tint = Color.White,
                    )
                }
            }
        }
        return
    }

    AnimatedVisibility(
        visible = state.controlsVisible,
        enter = enterAnim,
        exit = exitAnim,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        state.anime?.title?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                            )
                        }
                        state.episode?.name?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                    }

                    if (state.episodes.size > 1) {
                        IconButton(onClick = { showEpisodeDialog = true }) {
                            Text(
                                text = "${state.currentEpisodeIndex + 1}/${state.episodes.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    IconButton(onClick = onToggleLock) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = stringResource(MR.strings.player_lock),
                            tint = Color.White,
                        )
                    }

                    IconButton(onClick = { showSpeedDialog = true }) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = stringResource(MR.strings.player_speed),
                            tint = Color.White,
                        )
                    }

                    if (state.subtitleTracks.isNotEmpty() || state.audioTracks.isNotEmpty()) {
                        IconButton(onClick = { showTrackDialog = true }) {
                            Icon(
                                Icons.Default.ClosedCaption,
                                contentDescription = stringResource(MR.strings.player_tracks),
                                tint = Color.White,
                            )
                        }
                    }

                    IconButton(onClick = { showMoreSheet = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(MR.strings.player_more),
                            tint = Color.White,
                        )
                    }
                }
            }

            // Center controls
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.currentEpisodeIndex > 0) {
                    IconButton(onClick = onNavigatePrevious) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = stringResource(MR.strings.player_previous),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (state.isPlaying) MR.strings.action_pause else MR.strings.action_play,
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                }

                if (state.currentEpisodeIndex >= 0 && state.currentEpisodeIndex < state.episodes.size - 1) {
                    IconButton(onClick = onNavigateNext) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = stringResource(MR.strings.player_next),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            // Bottom bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showCurrentChapter && state.currentChapter != null && state.currentChapter.name.isNotEmpty()) {
                        CurrentChapter(
                            chapter = state.currentChapter,
                            onClick = { showChaptersSheet = true },
                        )
                    } else {
                        Spacer(Modifier)
                    }

                    state.skipIntroText?.let { text ->
                        FilledTonalButton(onClick = onSkipIntro) {
                            Text(text)
                        }
                    }
                }

                TimeBar(
                    currentPositionSec = state.currentPositionSec,
                    durationSec = state.durationSec,
                    onSeek = onSeek,
                    modifier = Modifier.fillMaxWidth(),
                    chapters = state.chapters.map { it.toSegment() }.toImmutableList(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCycleAspectRatio) {
                        Icon(
                            Icons.Default.AspectRatio,
                            contentDescription = stringResource(MR.strings.player_aspect_ratio),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onRotateScreen) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            contentDescription = stringResource(MR.strings.player_rotate),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    if (showTrackDialog) {
        TrackSelectionDialog(
            subtitleTracks = state.subtitleTracks,
            audioTracks = state.audioTracks,
            selectedSubId = state.selectedSubId,
            selectedAudioId = state.selectedAudioId,
            onSelectSubtitle = onSelectSubtitle,
            onSelectAudio = onSelectAudio,
            onAddSubtitleFile = {
                showTrackDialog = false
                onAddSubtitleFile()
            },
            onDismiss = { showTrackDialog = false },
        )
    }

    if (showSpeedDialog) {
        SpeedSheet(
            currentSpeed = state.currentSpeed,
            onSpeedSelected = { speed ->
                onSetSpeed(speed)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false },
        )
    }

    if (showMoreSheet) {
        MoreSheet(
            currentSpeed = state.currentSpeed,
            onSpeedSelected = onSetSpeed,
            onToggleStats = { onToggleStats(); showMoreSheet = false },
            onDismiss = { showMoreSheet = false },
            remainingTimerSeconds = state.remainingTimerSeconds,
            onStartTimer = onStartTimer,
            onCancelTimer = onCancelTimer,
        )
    }

    if (showChaptersSheet && state.chapters.isNotEmpty()) {
        ChaptersSheet(
            chapters = state.chapters.toImmutableList(),
            currentChapter = state.currentChapter,
            onClick = { chapter ->
                val idx = state.chapters.indexOf(chapter)
                if (idx >= 0) onSelectChapter(idx)
                showChaptersSheet = false
            },
            onDismissRequest = { showChaptersSheet = false },
        )
    }

    if (showEpisodeDialog) {
        EpisodeListDialog(
            episodes = state.episodes,
            currentIndex = state.currentEpisodeIndex,
            onEpisodeSelected = { index ->
                onLoadEpisodeAt(index)
                showEpisodeDialog = false
            },
            onDismiss = { showEpisodeDialog = false },
        )
    }
}

@Composable
private fun TrackSelectionDialog(
    subtitleTracks: List<MPVView.Track>,
    audioTracks: List<MPVView.Track>,
    selectedSubId: Int,
    selectedAudioId: Int,
    onSelectSubtitle: (Int) -> Unit,
    onSelectAudio: (Int) -> Unit,
    onAddSubtitleFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            ScrollbarLazyColumn(modifier = Modifier.padding(vertical = 16.dp)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(MR.strings.player_subtitles),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = onAddSubtitleFile) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(MR.strings.player_add_subtitle_file),
                            )
                        }
                    }
                }
                item {
                    TrackRow(
                        name = stringResource(MR.strings.off),
                        selected = selectedSubId == -1,
                        onClick = { onSelectSubtitle(-1); onDismiss() },
                    )
                }
                items(subtitleTracks.size) { index ->
                    val track = subtitleTracks[index]
                    TrackRow(
                        name = track.name,
                        selected = track.id == selectedSubId,
                        onClick = { onSelectSubtitle(track.id); onDismiss() },
                    )
                }

                if (audioTracks.size > 1) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(MR.strings.player_audio),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    items(audioTracks.size) { index ->
                        val track = audioTracks[index]
                        TrackRow(
                            name = track.name,
                            selected = track.id == selectedAudioId,
                            onClick = { onSelectAudio(track.id); onDismiss() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListDialog(
    episodes: List<tachiyomi.domain.episode.model.Episode>,
    currentIndex: Int,
    onEpisodeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            ScrollbarLazyColumn(modifier = Modifier.padding(vertical = 16.dp)) {
                item {
                    Text(
                        text = stringResource(MR.strings.player_episodes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                items(episodes.size) { index ->
                    val episode = episodes[index]
                    TrackRow(
                        name = episode.name,
                        selected = index == currentIndex,
                        onClick = { onEpisodeSelected(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, onClick = onClick)
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
