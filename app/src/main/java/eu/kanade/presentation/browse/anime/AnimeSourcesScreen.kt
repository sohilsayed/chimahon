package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.anime.components.BaseAnimeSourceItem
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourcesScreenModel.AnimeSourceUiModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun AnimeSourcesScreen(
    state: AnimeSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickSource: (AnimeCatalogueSource) -> Unit,
    onClickLatest: (AnimeCatalogueSource) -> Unit,
    onClickPin: (AnimeCatalogueSource) -> Unit,
    onLongClickSource: (AnimeCatalogueSource) -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(MR.strings.source_empty_screen, modifier = Modifier.padding(contentPadding))
        else -> {
            AnimeSourcesList(
                items = state.items,
                contentPadding = contentPadding,
                onClickSource = onClickSource,
                onClickLatest = onClickLatest,
                onClickPin = onClickPin,
                onLongClickSource = onLongClickSource,
            )
        }
    }
}

@Composable
private fun AnimeSourcesList(
    items: Map<String, List<AnimeSourceUiModel>>,
    contentPadding: PaddingValues,
    onClickSource: (AnimeCatalogueSource) -> Unit,
    onClickLatest: (AnimeCatalogueSource) -> Unit,
    onClickPin: (AnimeCatalogueSource) -> Unit,
    onLongClickSource: (AnimeCatalogueSource) -> Unit,
) {
    val context = LocalContext.current

    FastScrollLazyColumn(
        contentPadding = contentPadding + topSmallPaddingValues,
    ) {
        items.forEach { (lang, sources) ->
            item(key = "anime-source-header-$lang") {
                Row(
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = LocaleHelper.getSourceDisplayName(lang, context) +
                            " ${FlagEmoji.getEmojiLangFlag(lang)}",
                        modifier = Modifier
                            .padding(vertical = MaterialTheme.padding.small)
                            .weight(1f),
                        style = MaterialTheme.typography.header,
                    )
                }
            }

            items(
                items = sources,
                key = { "anime-source-${it.source.id}" },
            ) { item ->
                AnimeSourceItem(
                    item = item,
                    onClick = { onClickSource(item.source) },
                    onLongClick = { onLongClickSource(item.source) },
                    onClickLatest = { onClickLatest(item.source) },
                    onClickPin = { onClickPin(item.source) },
                )
            }
        }
    }
}

@Composable
private fun AnimeSourceItem(
    item: AnimeSourceUiModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickLatest: () -> Unit,
    onClickPin: () -> Unit,
) {
    BaseAnimeSourceItem(
        source = item.source,
        onClickItem = onClick,
        onLongClickItem = onLongClick,
        action = {
            if (item.source.supportsLatest) {
                TextButton(onClick = onClickLatest) {
                    Text(
                        text = stringResource(MR.strings.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            AnimeSourcePinButton(
                isPinned = item.isPinned,
                onClick = onClickPin,
            )
        },
    )
}

@Composable
private fun AnimeSourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA)
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun AnimeSourceOptionsDialog(
    source: AnimeCatalogueSource,
    isPinned: Boolean,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = source.name)
        },
        text = {
            Column {
                Text(
                    text = stringResource(if (isPinned) MR.strings.action_unpin else MR.strings.action_pin),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (source.id != LocalAnimeSource.ID) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}
