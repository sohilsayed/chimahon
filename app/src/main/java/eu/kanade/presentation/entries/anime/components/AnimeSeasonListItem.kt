package eu.kanade.presentation.entries.anime.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.library.components.AnimeComfortableGridItem
import eu.kanade.presentation.library.components.AnimeCompactGridItem
import eu.kanade.presentation.library.components.AnimeListItem
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.tachiyomi.ui.entries.anime.AnimeSeasonItem
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.anime.model.SeasonAnime
import tachiyomi.domain.entries.anime.model.SeasonDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.util.formatEpisodeNumber

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
    val title = if (anime.seasonDisplayMode == Anime.SEASON_DISPLAY_MODE_NUMBER) {
        val seasonNumber = itemAnime.seasonNumber
        stringResource(MR.strings.display_mode_episode, formatEpisodeNumber(seasonNumber))
    } else {
        itemAnime.title
    }
    val gridMode = anime.seasonDisplayGridMode

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
                    UnviewedBadge(count = item.unseenCount)
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
                    UnviewedBadge(count = item.unseenCount)
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
                    UnviewedBadge(count = item.unseenCount)
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
                    UnviewedBadge(count = item.unseenCount)
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
