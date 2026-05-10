package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.controls.components.panels.DelayPanel
import eu.kanade.tachiyomi.ui.player.controls.components.panels.SubtitleSettingsPanel
import eu.kanade.tachiyomi.ui.player.controls.components.panels.VideoFiltersPanel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onToggleStats: () -> Unit = {},
    onDismiss: () -> Unit,
    remainingTimerSeconds: Int? = null,
    onStartTimer: (Int) -> Unit = {},
    onCancelTimer: () -> Unit = {},
) {
    var showSubtitleDelay by remember { mutableStateOf(false) }
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var showAudioDelay by remember { mutableStateOf(false) }
    var showVideoFilters by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }

    if (showSubtitleSettings) {
        SubtitleSettingsPanel(onDismiss = { showSubtitleSettings = false })
        return
    }
    if (showSubtitleDelay) {
        DelayPanel(title = stringResource(MR.strings.player_subtitle_delay), mpvProperty = "sub-delay", onDismiss = { showSubtitleDelay = false })
        return
    }
    if (showAudioDelay) {
        DelayPanel(title = stringResource(MR.strings.player_audio_delay), mpvProperty = "audio-delay", onDismiss = { showAudioDelay = false })
        return
    }
    if (showVideoFilters) {
        VideoFiltersPanel(onDismiss = { showVideoFilters = false })
        return
    }
    if (showSpeedSheet) {
        SpeedSheet(
            currentSpeed = currentSpeed,
            onSpeedSelected = {
                onSpeedSelected(it)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false },
        )
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            MoreSheetItem(
                icon = Icons.Default.Speed,
                title = stringResource(MR.strings.player_playback_speed_with_value, currentSpeed.toString()),
                onClick = { showSpeedSheet = true },
            )
            MoreSheetItem(
                icon = Icons.Default.TextFields,
                title = stringResource(MR.strings.player_subtitle_settings),
                onClick = { showSubtitleSettings = true },
            )
            MoreSheetItem(
                icon = Icons.Default.Subtitles,
                title = stringResource(MR.strings.player_subtitle_delay),
                onClick = { showSubtitleDelay = true },
            )
            MoreSheetItem(
                icon = Icons.Default.MusicNote,
                title = stringResource(MR.strings.player_audio_delay),
                onClick = { showAudioDelay = true },
            )
            MoreSheetItem(
                icon = Icons.Default.Tune,
                title = stringResource(MR.strings.player_video_filters),
                onClick = { showVideoFilters = true },
            )
            MoreSheetItem(
                icon = Icons.Default.GraphicEq,
                title = stringResource(MR.strings.player_statistics),
                onClick = onToggleStats,
            )

            if (remainingTimerSeconds != null) {
                val mins = remainingTimerSeconds / 60
                val secs = remainingTimerSeconds % 60
                MoreSheetItem(
                    icon = Icons.Default.AlarmOff,
                    title = stringResource(MR.strings.player_sleep_timer_active, "${mins}:${"%02d".format(secs)}"),
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    },
                )
            } else {
                MoreSheetItem(
                    icon = Icons.Default.Alarm,
                    title = stringResource(MR.strings.player_sleep_timer),
                    onClick = { showTimerDialog = true },
                )
            }
        }
    }

    if (showTimerDialog) {
        SleepTimerDialog(
            onSelectDuration = { minutes ->
                onStartTimer(minutes * 60)
                showTimerDialog = false
                onDismiss()
            },
            onDismiss = { showTimerDialog = false },
        )
    }
}

@Composable
private fun SleepTimerDialog(
    onSelectDuration: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.player_sleep_timer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    TextButton(
                        onClick = { onSelectDuration(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(MR.strings.player_sleep_timer_minutes, minutes))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun MoreSheetItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
