package eu.kanade.tachiyomi.ui.browse.animesource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.AnimeExtensionIcon
import eu.kanade.presentation.browse.components.BaseBrowseItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourcesFilterScreen
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourcesScreenModel.AnimeSourceUiModel
import eu.kanade.tachiyomi.ui.browse.animesource.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.animesource.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.source.local.entries.anime.LocalAnimeSource

@Composable
fun Screen.animeSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { AnimeSourcesScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = MR.strings.label_anime_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_global_search),
                icon = Icons.Outlined.TravelExplore,
                onClick = { navigator.push(GlobalAnimeSearchScreen()) },
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navigator.push(AnimeSourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            val stateValue = state
            when {
                stateValue.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                stateValue.isEmpty -> EmptyScreen(MR.strings.source_empty_screen, modifier = Modifier.padding(contentPadding))
                else -> {
                    AnimeSourcesList(
                        items = stateValue.items,
                        contentPadding = contentPadding,
                        onClickSource = { source ->
                            navigator.push(source.createBrowseScreen(GetRemoteAnime.QUERY_POPULAR))
                        },
                        onClickLatest = { source ->
                            navigator.push(source.createBrowseScreen(GetRemoteAnime.QUERY_LATEST))
                        },
                        onClickPin = screenModel::togglePin,
                        onLongClickSource = screenModel::showSourceDialog,
                    )
                }
            }

            stateValue.dialog?.let { dialog ->
                AnimeSourceOptionsDialog(
                    source = dialog.source,
                    isPinned = stateValue.items.values.flatten()
                        .firstOrNull { it.source.id == dialog.source.id }
                        ?.isPinned == true,
                    onClickPin = {
                        screenModel.togglePin(dialog.source)
                        screenModel.closeDialog()
                    },
                    onClickDisable = {
                        screenModel.toggleSource(dialog.source)
                        screenModel.closeDialog()
                    },
                    onDismiss = screenModel::closeDialog,
                )
            }
        },
    )
}

private fun AnimeCatalogueSource.createBrowseScreen(listingQuery: String?) =
    (this as? AnimeSourceScreenProvider)?.createBrowseScreen(listingQuery)
        ?: BrowseAnimeSourceScreen(id, listingQuery)

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
                        text = LocaleHelper.getSourceDisplayName(lang, context),
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
    BaseBrowseItem(
        onClickItem = onClick,
        onLongClickItem = onLongClick,
        icon = {
            val iconModifier = Modifier
                .height(40.dp)
                .aspectRatio(1f)
            if (item.extension != null) {
                AnimeExtensionIcon(
                    extension = item.extension,
                    modifier = iconModifier,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlayCircle,
                    contentDescription = null,
                    modifier = iconModifier,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        content = {
            AnimeSourceItemContent(item.source)
        },
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
private fun RowScope.AnimeSourceItemContent(source: AnimeCatalogueSource) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .padding(horizontal = MaterialTheme.padding.medium)
            .weight(1f),
    ) {
        Text(
            text = source.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = LocaleHelper.getSourceDisplayName(source.lang, context),
            modifier = Modifier.secondaryItemAlpha(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AnimeSourceOptionsDialog(
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
