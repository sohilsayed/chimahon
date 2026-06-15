package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material3.Button

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import eu.kanade.tachiyomi.ui.player.setting.SubtitlePreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@OptIn(ExperimentalStdlibApi::class)
@Composable
fun SubtitleSettingsPanel(
    onDismiss: () -> Unit,
) {
    val preferences = remember { Injekt.get<SubtitlePreferences>() }
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val tabs = listOf(MR.strings.player_subtitle_typography, MR.strings.player_subtitle_colors, MR.strings.player_subtitle_misc)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(MR.strings.player_subtitle_settings), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(MR.strings.action_close))
                    }
                }

                TabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, titleRes ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(stringResource(titleRes)) },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.height(400.dp),
                ) { page ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        when (page) {
                            0 -> TypographyPage(preferences)
                            1 -> ColorsPage(preferences)
                            2 -> MiscPage(preferences)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypographyPage(preferences: SubtitlePreferences) {
    var fontSize by remember { mutableIntStateOf(MPVLib.getPropertyInt("sub-font-size") ?: 55) }
    var borderSize by remember { mutableIntStateOf(MPVLib.getPropertyInt("sub-border-size") ?: 3) }
    var shadowOffset by remember { mutableIntStateOf(MPVLib.getPropertyInt("sub-shadow-offset") ?: 0) }
    var isBold by remember { mutableStateOf(MPVLib.getPropertyBoolean("sub-bold") ?: false) }
    var isItalic by remember { mutableStateOf(MPVLib.getPropertyBoolean("sub-italic") ?: false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(checked = isBold, onCheckedChange = {
            isBold = it
            preferences.boldSubtitles().set(it)
            MPVLib.setPropertyBoolean("sub-bold", it)
        }) { Icon(Icons.Default.FormatBold, null) }

        IconToggleButton(checked = isItalic, onCheckedChange = {
            isItalic = it
            preferences.italicSubtitles().set(it)
            MPVLib.setPropertyBoolean("sub-italic", it)
        }) { Icon(Icons.Default.FormatItalic, null) }
    }

    Spacer(Modifier.height(8.dp))
    LabeledSlider(stringResource(MR.strings.player_subtitle_font_size), fontSize, 1, 100) {
        fontSize = it
        preferences.subtitleFontSize().set(it)
        MPVLib.setPropertyInt("sub-font-size", it)
    }
    LabeledSlider(stringResource(MR.strings.player_subtitle_border_size), borderSize, 0, 20) {
        borderSize = it
        preferences.subtitleBorderSize().set(it)
        MPVLib.setPropertyInt("sub-border-size", it)
    }
    LabeledSlider(stringResource(MR.strings.player_subtitle_shadow_offset), shadowOffset, 0, 20) {
        shadowOffset = it
        preferences.shadowOffsetSubtitles().set(it)
        MPVLib.setPropertyInt("sub-shadow-offset", it)
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            fontSize = 55; borderSize = 3; shadowOffset = 0; isBold = false; isItalic = false
            MPVLib.setPropertyInt("sub-font-size", 55)
            MPVLib.setPropertyInt("sub-border-size", 3)
            MPVLib.setPropertyInt("sub-shadow-offset", 0)
            MPVLib.setPropertyBoolean("sub-bold", false)
            MPVLib.setPropertyBoolean("sub-italic", false)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(MR.strings.action_reset)) }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
private fun ColorsPage(preferences: SubtitlePreferences) {
    var colorType by remember { mutableStateOf(SubColorType.Text) }
    var currentColor by remember { mutableIntStateOf(getCurrentColor(SubColorType.Text)) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SubColorType.entries.forEach { type ->
            Button(
                onClick = {
                    colorType = type
                    currentColor = getCurrentColor(type)
                },
                modifier = Modifier.weight(1f),
                colors = if (colorType == type) {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                } else {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                },
            ) { Text(type.label, maxLines = 1) }
        }
    }

    Spacer(Modifier.height(12.dp))

    ColorSlider(stringResource(MR.strings.player_subtitle_color_red), currentColor.red, Color.Red) {
        currentColor = currentColor.copyAsArgb(red = it)
        applyColor(preferences, colorType, currentColor)
    }
    ColorSlider(stringResource(MR.strings.player_subtitle_color_green), currentColor.green, Color.Green) {
        currentColor = currentColor.copyAsArgb(green = it)
        applyColor(preferences, colorType, currentColor)
    }
    ColorSlider(stringResource(MR.strings.player_subtitle_color_blue), currentColor.blue, Color.Blue) {
        currentColor = currentColor.copyAsArgb(blue = it)
        applyColor(preferences, colorType, currentColor)
    }
    ColorSlider(stringResource(MR.strings.player_subtitle_color_alpha), currentColor.alpha, Color.White) {
        currentColor = currentColor.copyAsArgb(alpha = it)
        applyColor(preferences, colorType, currentColor)
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            val default = when (colorType) {
                SubColorType.Text -> preferences.textColorSubtitles().defaultValue()
                SubColorType.Border -> preferences.borderColorSubtitles().defaultValue()
                SubColorType.Background -> preferences.backgroundColorSubtitles().defaultValue()
            }
            currentColor = default
            applyColor(preferences, colorType, default)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(MR.strings.player_reset_color, colorType.label)) }
}

@Composable
private fun MiscPage(preferences: SubtitlePreferences) {
    var subScale by remember {
        mutableFloatStateOf(MPVLib.getPropertyDouble("sub-scale")?.toFloat() ?: 1f)
    }
    var subPos by remember {
        mutableIntStateOf(MPVLib.getPropertyInt("sub-pos") ?: 100)
    }

    LabeledFloatSlider(stringResource(MR.strings.player_subtitle_scale), subScale, 0f, 5f) {
        subScale = it
        preferences.subtitleFontScale().set(it)
        MPVLib.setPropertyDouble("sub-scale", it.toDouble())
    }
    LabeledSlider(stringResource(MR.strings.player_subtitle_position), subPos, 0, 150) {
        subPos = it
        preferences.subtitlePos().set(it)
        MPVLib.setPropertyInt("sub-pos", it)
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            subScale = 1f; subPos = 100
            MPVLib.setPropertyDouble("sub-scale", 1.0)
            MPVLib.setPropertyInt("sub-pos", 100)
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(MR.strings.action_reset)) }
}

@Composable
private fun LabeledSlider(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$value", style = MaterialTheme.typography.bodyMedium)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = min.toFloat()..max.toFloat(),
    )
}

@Composable
private fun LabeledFloatSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("%.2f".format(value), style = MaterialTheme.typography.bodyMedium)
    }
    Slider(
        value = value,
        onValueChange = { onChange(kotlin.math.round(it * 100) / 100f) },
        valueRange = min..max,
    )
}

@Composable
private fun ColorSlider(label: String, value: Int, tint: Color, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$value", style = MaterialTheme.typography.bodyMedium)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = 0f..255f,
        colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
    )
}

private enum class SubColorType(val label: String, val property: String) {
    Text("Text", "sub-color"),
    Border("Border", "sub-border-color"),
    Background("BG", "sub-back-color"),
}

private fun Int.copyAsArgb(
    alpha: Int = this.alpha,
    red: Int = this.red,
    green: Int = this.green,
    blue: Int = this.blue,
) = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

@OptIn(ExperimentalStdlibApi::class)
private fun Int.toColorHexString() = "#" + this.toHexString().uppercase()

private fun getCurrentColor(type: SubColorType): Int {
    return try {
        MPVLib.getPropertyString(type.property)?.let {
            android.graphics.Color.parseColor(it.uppercase())
        } ?: android.graphics.Color.WHITE
    } catch (_: Exception) {
        android.graphics.Color.WHITE
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun applyColor(preferences: SubtitlePreferences, type: SubColorType, color: Int) {
    val hex = color.toColorHexString()
    MPVLib.setPropertyString(type.property, hex)
    when (type) {
        SubColorType.Text -> preferences.textColorSubtitles().set(color)
        SubColorType.Border -> preferences.borderColorSubtitles().set(color)
        SubColorType.Background -> preferences.backgroundColorSubtitles().set(color)
    }
}
