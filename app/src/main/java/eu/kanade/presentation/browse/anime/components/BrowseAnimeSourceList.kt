package eu.kanade.presentation.browse.anime.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.components.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.library.components.AnimeListItem
import eu.kanade.presentation.library.components.CommonAnimeItemDefaults
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.presentation.core.util.plus

@Composable
fun BrowseAnimeSourceList(
    animeList: LazyPagingItems<StateFlow<Anime>>,
    entries: Int,
    topBarHeight: Int,
    contentPadding: PaddingValues,
    onAnimeClick: (Anime) -> Unit,
    onAnimeLongClick: (Anime) -> Unit,
) {
    val sourceListState = rememberLazyListState()
    BoxWithConstraints {
        val density = LocalDensity.current
        val containerHeightPx = with(density) { this@BoxWithConstraints.maxHeight.roundToPx() }

        LazyColumn(
            state = sourceListState,
            contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
        ) {
            item {
                if (animeList.loadState.prepend is LoadState.Loading) {
                    BrowseSourceLoadingItem()
                }
            }

            items(count = animeList.itemCount) { index ->
                val anime by animeList[index]?.collectAsState() ?: return@items
                BrowseAnimeSourceListItem(
                    anime = anime,
                    onClick = { onAnimeClick(anime) },
                    onLongClick = { onAnimeLongClick(anime) },
                    entries = entries,
                    containerHeight = containerHeightPx - topBarHeight,
                )
            }

            item {
                if (animeList.loadState.refresh is LoadState.Loading ||
                    animeList.loadState.append is LoadState.Loading
                ) {
                    BrowseSourceLoadingItem()
                }
            }
        }
    }
}

@Composable
private fun BrowseAnimeSourceListItem(
    anime: Anime,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
    entries: Int,
    containerHeight: Int,
) {
    AnimeListItem(
        title = anime.title,
        coverData = AnimeCover(
            animeId = anime.id,
            sourceId = anime.source,
            isAnimeFavorite = anime.favorite,
            ogUrl = anime.thumbnailUrl,
            lastModified = anime.coverLastModified,
        ),
        coverAlpha = if (anime.favorite) CommonAnimeItemDefaults.BrowseFavoriteCoverAlpha else 1f,
        badge = {
            InLibraryBadge(enabled = anime.favorite)
        },
        onLongClick = onLongClick,
        onClick = onClick,
        entries = entries,
        containerHeight = containerHeight,
    )
}
