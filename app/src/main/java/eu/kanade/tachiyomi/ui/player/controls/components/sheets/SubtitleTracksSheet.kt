/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.JimakuState
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.VideoTrack
import eu.kanade.tachiyomi.ui.player.utils.JimakuEntry
import eu.kanade.tachiyomi.ui.player.utils.JimakuFile
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SubtitlesSheet(
    tracks: ImmutableList<VideoTrack>,
    selectedTracks: ImmutableList<Int>,
    jimakuState: JimakuState,
    jimakuTitle: String,
    currentJimakuTitle: String,
    onSelect: (Int) -> Unit,
    onAddSubtitle: () -> Unit,
    onSearchJimaku: () -> Unit,
    onSelectJimakuEntry: (JimakuEntry) -> Unit,
    onSelectJimakuFile: (JimakuFile) -> Unit,
    onDismissJimaku: () -> Unit,
    onUpdateJimakuTitle: (String) -> Unit,
    onOpenSubtitleSettings: () -> Unit,
    onOpenSubtitleDelay: () -> Unit,
    onOpenSubtitleRegex: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showJimakuTitleDialog by remember { mutableStateOf(false) }

    GenericTracksSheet(
        tracks = tracks,
        onDismissRequest = onDismissRequest,
        header = {
            TrackSheetTitle(
                title = stringResource(MR.strings.pref_player_subtitle),
                actions = {
                    TextButton(onClick = onOpenSubtitleSettings) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        ) {
                            Icon(imageVector = Icons.Default.Palette, contentDescription = null)
                            Text(text = stringResource(MR.strings.player_sheets_track_palette))
                        }
                    }
                    TextButton(onClick = onOpenSubtitleDelay) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        ) {
                            Icon(imageVector = Icons.Default.MoreTime, contentDescription = null)
                            Text(text = stringResource(MR.strings.player_sheets_track_delay))
                        }
                    }
                    TextButton(onClick = onOpenSubtitleRegex) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                        ) {
                            Icon(imageVector = Icons.Outlined.Code, contentDescription = null)
                            Text(text = stringResource(MR.strings.player_sheets_track_regex))
                        }
                    }
                },
            )
            AddTrackRow(
                title = stringResource(MR.strings.player_sheets_add_ext_sub),
                onClick = onAddSubtitle,
            )
            JimakuActionRow(
                icon = Icons.Default.Search,
                title = "Search Jimaku",
                subtitle = currentJimakuTitle,
                onClick = onSearchJimaku,
            )
            JimakuTitleRow(
                title = jimakuTitle,
                fallbackTitle = currentJimakuTitle,
                onClick = { showJimakuTitleDialog = true },
            )
        },
        track = { track ->
            SubtitleTrackRow(
                title = getTrackTitle(track),
                selected = selectedTracks.indexOf(track.id),
                onClick = { onSelect(track.id) },
            )
        },
        footer = {
            Column(
                modifier = modifier
                    .padding(MaterialTheme.padding.medium)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                horizontalAlignment = Alignment.Start,
            ) {
                Icon(Icons.Outlined.Info, null)
                Text(stringResource(MR.strings.player_sheets_subtitles_footer_secondary_sid_no_styles))
            }
        },
        modifier = modifier,
    )

    if (showJimakuTitleDialog) {
        JimakuTitleDialog(
            title = jimakuTitle,
            fallbackTitle = currentJimakuTitle,
            onDismissRequest = { showJimakuTitleDialog = false },
            onConfirm = {
                onUpdateJimakuTitle(it)
                showJimakuTitleDialog = false
            },
        )
    }

    JimakuDialog(
        state = jimakuState,
        onDismissRequest = onDismissJimaku,
        onSelectEntry = onSelectJimakuEntry,
        onSelectFile = onSelectJimakuFile,
    )
}

@Composable
private fun JimakuActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(56.dp)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Column {
            Text(text = title, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JimakuTitleRow(
    title: String,
    fallbackTitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .height(56.dp)
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        Icon(
            imageVector = Icons.Default.Subtitles,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Column {
            Text(text = "Jimaku title", fontWeight = FontWeight.Medium)
            Text(
                text = title.ifBlank { "Use current video title: $fallbackTitle" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JimakuTitleDialog(
    title: String,
    fallbackTitle: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var textFieldValue by rememberSaveable(title, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(title))
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Jimaku title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(fallbackTitle) },
                )
                Text(
                    text = "Leave blank to search with the current video title.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(textFieldValue.text) }) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun JimakuDialog(
    state: JimakuState,
    onDismissRequest: () -> Unit,
    onSelectEntry: (JimakuEntry) -> Unit,
    onSelectFile: (JimakuFile) -> Unit,
) {
    when (state) {
        JimakuState.Idle -> Unit
        is JimakuState.Searching -> JimakuProgressDialog(
            title = "Jimaku",
            message = "Searching for ${state.title}",
            onDismissRequest = onDismissRequest,
        )
        is JimakuState.LoadingFiles -> JimakuProgressDialog(
            title = state.entry.name,
            message = "Loading subtitles",
            onDismissRequest = onDismissRequest,
        )
        is JimakuState.Downloading -> JimakuProgressDialog(
            title = "Jimaku",
            message = "Downloading ${state.file.name}",
            onDismissRequest = onDismissRequest,
        )
        is JimakuState.EntryResults -> AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Select Jimaku title") },
            text = {
                LazyColumn {
                    items(state.entries) { entry ->
                        JimakuEntryRow(entry = entry, onClick = { onSelectEntry(entry) })
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
        is JimakuState.FileResults -> AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Select subtitle") },
            text = {
                LazyColumn {
                    items(state.files) { file ->
                        JimakuFileRow(file = file, onClick = { onSelectFile(file) })
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_cancel))
                }
            },
        )
        is JimakuState.Error -> AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text("Jimaku") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(MR.strings.action_ok))
                }
            },
        )
    }
}

@Composable
private fun JimakuProgressDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(message)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun JimakuEntryRow(entry: JimakuEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null)
        Column {
            Text(entry.name, fontWeight = FontWeight.Medium)
            entry.englishName?.takeIf { it.isNotBlank() && it != entry.name }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun JimakuFileRow(file: JimakuFile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Subtitles, contentDescription = null)
        Column {
            Text(file.name, fontWeight = FontWeight.Medium)
            Text(
                text = file.size.toReadableFileSize(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Long.toReadableFileSize(): String {
    if (this < 1024) return "$this B"
    val kib = this / 1024.0
    if (kib < 1024) return "%.1f KiB".format(kib)
    return "%.1f MiB".format(kib / 1024.0)
}

@Composable
fun SubtitleTrackRow(
    title: String,
    selected: Int, // -1 unselected, otherwise return 0 and 1 for the selected indices
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = MaterialTheme.padding.small, end = MaterialTheme.padding.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected > -1,
            onCheckedChange = { _ -> onClick() },
        )
        Text(
            text = title,
            fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (selected != -1) {
            Text(
                text = "#${selected + 1}",
                fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
                fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
            )
        }
    }
}
