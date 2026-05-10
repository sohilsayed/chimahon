package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.kanade.tachiyomi.ui.player.setting.AudioPreferences
import eu.kanade.tachiyomi.ui.player.setting.SubtitlePreferences
import `is`.xyz.mpv.MPVLib
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

@Composable
fun DelayPanel(
    title: String,
    mpvProperty: String,
    onDismiss: () -> Unit,
) {
    val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }
    val audioPreferences = remember { Injekt.get<AudioPreferences>() }

    var delayMs by remember {
        mutableIntStateOf((MPVLib.getPropertyDouble(mpvProperty) * 1000).roundToInt())
    }

    LaunchedEffect(delayMs) {
        MPVLib.setPropertyDouble(mpvProperty, delayMs / 1000.0)
        when (mpvProperty) {
            "sub-delay" -> subtitlePreferences.subtitlesDelay().set(delayMs)
            "audio-delay" -> audioPreferences.audioDelay().set(delayMs)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(MR.strings.action_close))
                    }
                }

                Text(
                    text = "${delayMs}ms",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 16.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    OutlinedButton(onClick = { delayMs -= 500 }) { Text("-500") }
                    OutlinedButton(onClick = { delayMs -= 100 }) { Text("-100") }
                    OutlinedButton(onClick = { delayMs += 100 }) { Text("+100") }
                    OutlinedButton(onClick = { delayMs += 500 }) { Text("+500") }
                }

                Button(
                    onClick = { delayMs = 0 },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text(stringResource(MR.strings.action_reset))
                }
            }
        }
    }
}
