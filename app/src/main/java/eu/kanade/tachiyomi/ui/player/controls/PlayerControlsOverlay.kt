package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.kanade.tachiyomi.ui.player.mpv.MPVView
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlayerControlsOverlay(
    isPlaying: Boolean,
    animeTitle: String?,
    episodeName: String?,
    currentPositionSec: Long,
    durationSec: Long,
    doubleTapSeekSec: Int,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekRelative: (Int) -> Unit,
    onBack: () -> Unit,
    subtitleTracks: List<MPVView.Track> = emptyList(),
    audioTracks: List<MPVView.Track> = emptyList(),
    selectedSubId: Int = -1,
    selectedAudioId: Int = -1,
    onSelectSubtitle: (Int) -> Unit = {},
    onSelectAudio: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var showTrackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3.seconds)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(doubleTapSeekSec) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        val isLeftSide = offset.x < size.width / 2
                        onSeekRelative(if (isLeftSide) -doubleTapSeekSec else doubleTapSeekSec)
                    },
                )
            },
    ) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                            animeTitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                            }
                            episodeName?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                )
                            }
                        }
                        if (subtitleTracks.isNotEmpty() || audioTracks.isNotEmpty()) {
                            IconButton(onClick = { showTrackDialog = true }) {
                                Icon(
                                    Icons.Default.ClosedCaption,
                                    contentDescription = stringResource(MR.strings.player_tracks),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) MR.strings.action_pause else MR.strings.action_play,
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                }

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
                    TimeBar(
                        currentPositionSec = currentPositionSec,
                        durationSec = durationSec,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showTrackDialog) {
        TrackSelectionDialog(
            subtitleTracks = subtitleTracks,
            audioTracks = audioTracks,
            selectedSubId = selectedSubId,
            selectedAudioId = selectedAudioId,
            onSelectSubtitle = onSelectSubtitle,
            onSelectAudio = onSelectAudio,
            onDismiss = { showTrackDialog = false },
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
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            ScrollbarLazyColumn(modifier = Modifier.padding(vertical = 16.dp)) {
                if (subtitleTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(MR.strings.player_subtitles),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
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
private fun TrackRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
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
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}
