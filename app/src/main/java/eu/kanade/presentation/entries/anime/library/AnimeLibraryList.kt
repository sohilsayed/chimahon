package eu.kanade.presentation.entries.anime.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.library.components.AnimeListItem
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.GlobalSearchItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.tachiyomi.ui.entries.anime.library.AnimeLibraryItem
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.service.AnimeLibraryPreferences
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun AnimeLibraryList(
    items: List<AnimeLibraryItem>,
    entries: Int,
    containerHeight: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryAnime>,
    onClick: (LibraryAnime) -> Unit,
    onLongClick: (LibraryAnime) -> Unit,
    onClickContinueWatching: ((LibraryAnime) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    val badgePrefs = remember { Injekt.get<AnimeLibraryPreferences>() }
    val showDownloadBadge by remember { badgePrefs.downloadBadge().changes() }.collectAsState(badgePrefs.downloadBadge().get())
    val showUnseenBadge by remember { badgePrefs.unseenBadge().changes() }.collectAsState(badgePrefs.unseenBadge().get())
    val showLocalBadge by remember { badgePrefs.localBadge().changes() }.collectAsState(badgePrefs.localBadge().get())
    val showLanguageBadge by remember { badgePrefs.languageBadge().changes() }.collectAsState(badgePrefs.languageBadge().get())

    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (!searchQuery.isNullOrEmpty()) {
                GlobalSearchItem(
                    modifier = Modifier.fillMaxWidth(),
                    searchQuery = searchQuery,
                    onClick = onGlobalSearchClicked,
                )
            }
        }

        items(
            items = items,
            contentType = { "anime_library_list_item" },
        ) { libraryItem ->
            val anime = libraryItem.libraryAnime.anime
            AnimeListItem(
                isSelected = selection.fastAny { it.id == libraryItem.libraryAnime.id },
                title = anime.title,
                coverData = AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    ogUrl = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
                ),
                badge = {
                    if (showDownloadBadge) DownloadsBadge(count = libraryItem.downloadCount)
                    if (showUnseenBadge) UnviewedBadge(count = libraryItem.unseenCount)
                    if (showLocalBadge) AnimeSourceIconBadge(source = libraryItem.source)
                    if (showLanguageBadge) {
                        LanguageBadge(
                            isLocal = libraryItem.isLocal,
                            sourceLanguage = libraryItem.sourceLanguage,
                        )
                    }
                },
                onLongClick = { onLongClick(libraryItem.libraryAnime) },
                onClick = { onClick(libraryItem.libraryAnime) },
                onClickContinueWatching = if (onClickContinueWatching != null && libraryItem.unseenCount > 0) {
                    { onClickContinueWatching(libraryItem.libraryAnime) }
                } else {
                    null
                },
                entries = entries,
                containerHeight = containerHeight,
            )
        }
    }
}
