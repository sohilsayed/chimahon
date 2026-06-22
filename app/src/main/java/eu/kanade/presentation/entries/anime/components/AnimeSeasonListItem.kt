package eu.kanade.presentation.entries.anime.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.library.components.AnimeComfortableGridItem
import eu.kanade.presentation.library.components.AnimeCompactGridItem
import eu.kanade.presentation.library.components.AnimeListItem
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.UnreadBadge
import eu.kanade.tachiyomi.ui.entries.anime.AnimeSeasonItem
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.anime.model.SeasonAnime
import tachiyomi.domain.entries.anime.model.SeasonDisplayMode

@Composable
fun AnimeSeasonListItem(
    anime: Anime,
    item: AnimeSeasonItem,
    containerHeight: Int,
    onSeasonClicked: (SeasonAnime) -> Unit,
    onClickContinueWatching: ((SeasonAnime) -> Unit)?,
    listItemModifier: Modifier = Modifier,
) {
    val itemAnime = item.seasonAnime.anime
    val title = itemAnime.title
    val gridMode = SeasonDisplayMode.fromLong(anime.seasonDisplayGridMode)

    when (gridMode) {
        SeasonDisplayMode.ComfortableGrid -> {
            AnimeComfortableGridItem(
                title = title,
                coverData = AnimeCover(
                    animeId = itemAnime.id,
                    sourceId = itemAnime.source,
                    isAnimeFavorite = itemAnime.favorite,
                    ogUrl = itemAnime.thumbnailUrl,
                    lastModified = itemAnime.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = item.downloadCount)
                    UnreadBadge(count = item.unseenCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = item.isLocal,
                        sourceLanguage = item.sourceLanguage,
                    )
                },
                onLongClick = { onSeasonClicked(item.seasonAnime) },
                onClick = { onSeasonClicked(item.seasonAnime) },
                onClickContinueWatching = if (onClickContinueWatching != null && item.showContinueOverlay) {
                    { onClickContinueWatching(item.seasonAnime) }
                } else {
                    null
                },
            )
        }
        SeasonDisplayMode.CompactGrid -> {
            AnimeCompactGridItem(
                title = title,
                coverData = AnimeCover(
                    animeId = itemAnime.id,
                    sourceId = itemAnime.source,
                    isAnimeFavorite = itemAnime.favorite,
                    ogUrl = itemAnime.thumbnailUrl,
                    lastModified = itemAnime.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = item.downloadCount)
                    UnreadBadge(count = item.unseenCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = item.isLocal,
                        sourceLanguage = item.sourceLanguage,
                    )
                },
                onLongClick = { onSeasonClicked(item.seasonAnime) },
                onClick = { onSeasonClicked(item.seasonAnime) },
                onClickContinueWatching = if (onClickContinueWatching != null && item.showContinueOverlay) {
                    { onClickContinueWatching(item.seasonAnime) }
                } else {
                    null
                },
            )
        }
        SeasonDisplayMode.CoverOnlyGrid -> {
            AnimeCompactGridItem(
                title = null,
                coverData = AnimeCover(
                    animeId = itemAnime.id,
                    sourceId = itemAnime.source,
                    isAnimeFavorite = itemAnime.favorite,
                    ogUrl = itemAnime.thumbnailUrl,
                    lastModified = itemAnime.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = item.downloadCount)
                    UnreadBadge(count = item.unseenCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = item.isLocal,
                        sourceLanguage = item.sourceLanguage,
                    )
                },
                onLongClick = { onSeasonClicked(item.seasonAnime) },
                onClick = { onSeasonClicked(item.seasonAnime) },
                onClickContinueWatching = if (onClickContinueWatching != null && item.showContinueOverlay) {
                    { onClickContinueWatching(item.seasonAnime) }
                } else {
                    null
                },
            )
        }
        SeasonDisplayMode.List -> {
            AnimeListItem(
                title = title,
                coverData = AnimeCover(
                    animeId = itemAnime.id,
                    sourceId = itemAnime.source,
                    isAnimeFavorite = itemAnime.favorite,
                    ogUrl = itemAnime.thumbnailUrl,
                    lastModified = itemAnime.coverLastModified,
                ),
                badge = {
                    DownloadsBadge(count = item.downloadCount)
                    UnreadBadge(count = item.unseenCount)
                    LanguageBadge(
                        isLocal = item.isLocal,
                        sourceLanguage = item.sourceLanguage,
                    )
                },
                onLongClick = { onSeasonClicked(item.seasonAnime) },
                onClick = { onSeasonClicked(item.seasonAnime) },
                onClickContinueWatching = if (onClickContinueWatching != null && item.showContinueOverlay) {
                    { onClickContinueWatching(item.seasonAnime) }
                } else {
                    null
                },
                entries = anime.seasonDisplayGridSize,
                containerHeight = containerHeight,
                // AM: AnimeListItem doesn't have a modifier parameter
            )
        }
    }
}
