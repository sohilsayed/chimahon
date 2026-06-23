package eu.kanade.tachiyomi.ui.browse.animesource

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import eu.kanade.presentation.browse.components.BaseBrowseItem
import eu.kanade.presentation.browse.components.AnimeExtensionIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourcesFilterScreen
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourcesScreenModel.AnimeSourceUiModel
import eu.kanade.tachiyomi.ui.browse.animesource.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.animesource.globalsearch.GlobalAnimeSearchScreen
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.presentation.core.util.secondaryItemAlpha

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
                            navigator.push(BrowseAnimeSourceScreen(source.id, GetRemoteAnime.QUERY_POPULAR))
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun AnimeSourcesList(
    items: Map<String, List<AnimeSourceUiModel>>,
    contentPadding: PaddingValues,
    onClickSource: (AnimeCatalogueSource) -> Unit,
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
                )
            }
        }
    }
}

@Composable
private fun AnimeSourceItem(
    item: AnimeSourceUiModel,
    onClick: () -> Unit,
) {
    BaseBrowseItem(
        onClickItem = onClick,
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
    )
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
