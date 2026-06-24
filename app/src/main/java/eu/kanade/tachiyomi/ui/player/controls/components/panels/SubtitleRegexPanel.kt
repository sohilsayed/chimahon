package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.constraintlayout.compose.ConstraintLayout
import eu.kanade.tachiyomi.ui.player.controls.CARDS_MAX_WIDTH
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.panelCardsColors
import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences
import eu.kanade.tachiyomi.ui.player.utils.customSubtitleRegex
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun SubtitleRegexPanel(
    onFiltersChanged: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismissRequest)

    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.padding.medium),
    ) {
        val regexCard = createRef()

        SubtitleRegexCard(
            onFiltersChanged = onFiltersChanged,
            onClose = onDismissRequest,
            modifier = Modifier.constrainAs(regexCard) {
                linkTo(parent.top, parent.bottom, bias = 0.8f)
                end.linkTo(parent.end)
            },
        )
    }
}

@Composable
private fun SubtitleRegexCard(
    onFiltersChanged: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences = remember { Injekt.get<SubtitlePreferences>() }
    val removeSpeakerNames by preferences.subtitleRegexRemoveSpeakerNames().collectAsState()
    val mergeMultiline by preferences.subtitleRegexMergeMultiline().collectAsState()
    val removeBracketedText by preferences.subtitleRegexRemoveBracketedText().collectAsState()
    val removeUppercaseLines by preferences.subtitleRegexRemoveUppercaseLines().collectAsState()
    val removeMusicSymbols by preferences.subtitleRegexRemoveMusicSymbols().collectAsState()
    val removeCurlyBracedText by preferences.subtitleRegexRemoveCurlyBracedText().collectAsState()
    val customRegexEnabled by preferences.subtitleRegexCustomEnabled().collectAsState()
    val customRegexPattern by preferences.subtitleRegexCustomPattern().collectAsState()
    val anyFilterEnabled = removeSpeakerNames ||
        mergeMultiline ||
        removeBracketedText ||
        removeUppercaseLines ||
        removeMusicSymbols ||
        removeCurlyBracedText ||
        customRegexEnabled
    val customRegexError = customRegexEnabled &&
        customRegexPattern.isNotBlank() &&
        customSubtitleRegex(customRegexPattern) == null

    fun clearFilters() {
        preferences.subtitleRegexRemoveSpeakerNames().set(false)
        preferences.subtitleRegexMergeMultiline().set(false)
        preferences.subtitleRegexRemoveBracketedText().set(false)
        preferences.subtitleRegexRemoveUppercaseLines().set(false)
        preferences.subtitleRegexRemoveMusicSymbols().set(false)
        preferences.subtitleRegexRemoveCurlyBracedText().set(false)
        preferences.subtitleRegexCustomEnabled().set(false)
        onFiltersChanged()
    }

    Card(
        colors = panelCardsColors(),
        modifier = modifier.widthIn(max = CARDS_MAX_WIDTH),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                Icon(Icons.Outlined.Code, null)
                Text(
                    stringResource(MR.strings.player_subtitle_regex_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            ControlsButton(Icons.Default.Close, onClose)
        }

        LazyColumn {
            item {
                SubtitleRegexCheckboxRow(
                    text = stringResource(MR.strings.player_subtitle_regex_none),
                    checked = !anyFilterEnabled,
                    onCheckedChange = {
                        if (it) clearFilters()
                    },
                )
            }
            item {
                SubtitleRegexCheckboxRow(
                    text = stringResource(MR.strings.player_subtitle_regex_remove_speaker_names),
                    checked = removeSpeakerNames,
                    onCheckedChange = {
                        preferences.subtitleRegexRemoveSpeakerNames().set(it)
                        onFiltersChanged()
                    },
                )
            }
            item {
                SubtitleRegexCheckboxRow(
                    text = stringResource(MR.strings.player_subtitle_regex_merge_multiline),
                    checked = mergeMultiline,
                    onCheckedChange = {
                        preferences.subtitleRegexMergeMultiline().set(it)
                        onFiltersChanged()
                    },
                )
            }
            item {
                SubtitleRegexCheckboxRow(
                    text = stringResource(MR.strings.player_subtitle_regex_remove_bracketed_text),
                    checked = removeBracketedText,
                    onCheckedChange = {
                        preferences.subtitleRegexRemoveBracketedText().set(it)
                        onFiltersChanged()
                    },
                )
            }
            item {
                SubtitleRegexCheckboxRow(
                    text = stringResource(MR.strings.player_subtitle_regex_remove_uppercase_lines),
                    checked = removeUppercaseLines,
                    onCheckedChange = {
                        preferences.subtitleRegexRemoveUppercaseLines().set(it)
                        onFiltersChanged()
                    },
                )
            }
            item {
                SubtitleRegexCheckboxRow(
                    text = stringResource(MR.strings.player_subtitle_regex_remove_music_symbols),
                    checked = removeMusicSymbols,
                    onCheckedChange = {
                        preferences.subtitleRegexRemoveMusicSymbols().set(it)
                        onFiltersChanged()
                    },
                )
            }
            item {
                SubtitleRegexCheckboxRow(
                    text = stringResource(MR.strings.player_subtitle_regex_remove_curly_braced_text),
                    checked = removeCurlyBracedText,
                    onCheckedChange = {
                        preferences.subtitleRegexRemoveCurlyBracedText().set(it)
                        onFiltersChanged()
                    },
                )
            }
            item {
                SubtitleRegexCheckboxRow(
                    text = stringResource(MR.strings.player_subtitle_regex_custom),
                    checked = customRegexEnabled,
                    onCheckedChange = {
                        preferences.subtitleRegexCustomEnabled().set(it)
                        onFiltersChanged()
                    },
                )
            }
            if (customRegexEnabled) {
                item {
                    OutlinedTextField(
                        value = customRegexPattern,
                        onValueChange = {
                            preferences.subtitleRegexCustomPattern().set(it)
                            onFiltersChanged()
                        },
                        label = { Text(stringResource(MR.strings.player_subtitle_regex_custom_pattern)) },
                        isError = customRegexError,
                        supportingText = {
                            if (customRegexError) {
                                Text(stringResource(MR.strings.player_subtitle_regex_invalid))
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = MaterialTheme.padding.medium,
                                end = MaterialTheme.padding.medium,
                                bottom = MaterialTheme.padding.medium,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleRegexCheckboxRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
        Text(text)
    }
}
