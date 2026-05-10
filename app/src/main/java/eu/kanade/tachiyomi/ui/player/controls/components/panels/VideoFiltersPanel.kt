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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import eu.kanade.tachiyomi.ui.player.VideoFilters
import eu.kanade.tachiyomi.ui.player.setting.DecoderPreferences
import `is`.xyz.mpv.MPVLib
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun VideoFiltersPanel(
    onDismiss: () -> Unit,
) {
    val decoderPreferences = remember { Injekt.get<DecoderPreferences>() }
    var resetKey by remember { mutableStateOf(0) }

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
                    Text(stringResource(MR.strings.player_video_filters), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(MR.strings.action_close))
                    }
                }

                VideoFilters.entries.forEach { filter ->
                    FilterSlider(
                        key = resetKey,
                        filter = filter,
                        decoderPreferences = decoderPreferences,
                    )
                }

                Button(
                    onClick = {
                        VideoFilters.entries.forEach {
                            MPVLib.setPropertyInt(it.mpvProperty, 0)
                            persistFilter(decoderPreferences, it, 0)
                        }
                        resetKey++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(MR.strings.player_reset_all))
                }
            }
        }
    }
}

@Composable
private fun FilterSlider(
    key: Int,
    filter: VideoFilters,
    decoderPreferences: DecoderPreferences,
) {
    var value by remember(key) {
        mutableIntStateOf(MPVLib.getPropertyInt(filter.mpvProperty) ?: 0)
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                filter.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("$value", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = {
                value = it.toInt()
                MPVLib.setPropertyInt(filter.mpvProperty, value)
                persistFilter(decoderPreferences, filter, value)
            },
            valueRange = -100f..100f,
        )
    }
}

private fun persistFilter(prefs: DecoderPreferences, filter: VideoFilters, value: Int) {
    when (filter) {
        VideoFilters.BRIGHTNESS -> prefs.brightnessFilter().set(value)
        VideoFilters.SATURATION -> prefs.saturationFilter().set(value)
        VideoFilters.CONTRAST -> prefs.contrastFilter().set(value)
        VideoFilters.GAMMA -> prefs.gammaFilter().set(value)
        VideoFilters.HUE -> prefs.hueFilter().set(value)
    }
}
