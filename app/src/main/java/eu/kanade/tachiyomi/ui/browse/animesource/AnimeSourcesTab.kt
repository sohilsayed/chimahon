package eu.kanade.tachiyomi.ui.browse.animesource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
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
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.animesource.browse.BrowseAnimeSourceScreen
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
        actions = persistentListOf(),
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
                            navigator.push(BrowseAnimeSourceScreen(source.id, source.name))
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun AnimeSourcesList(
    items: Map<String, List<AnimeCatalogueSource>>,
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
                            .padding(vertical = 8.dp)
                            .weight(1f),
                        style = MaterialTheme.typography.header,
                    )
                }
            }

            items(
                items = sources,
                key = { "anime-source-${it.id}" },
            ) { source ->
                AnimeSourceItem(
                    source = source,
                    onClick = { onClickSource(source) },
                )
            }
        }
    }
}

@Composable
private fun AnimeSourceItem(
    source: AnimeCatalogueSource,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayCircleOutline,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Column(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
            Text(
                text = source.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = LocaleHelper.getSourceDisplayName(source.lang, LocalContext.current),
                modifier = Modifier.secondaryItemAlpha(),
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
