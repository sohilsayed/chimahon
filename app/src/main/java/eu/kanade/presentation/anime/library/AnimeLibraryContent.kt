package eu.kanade.presentation.anime.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.kanade.tachiyomi.ui.anime.library.AnimeLibraryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.PullRefresh
import kotlin.time.Duration.Companion.seconds

@Composable
fun AnimeLibraryContent(
    categories: List<AnimeCategory>,
    currentPage: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    hasActiveFilters: Boolean,
    showPageTabs: Boolean,
    showAnimeCount: Boolean,
    displayMode: LibraryDisplayMode,
    onChangeCurrentPage: (Int) -> Unit,
    onAnimeClicked: (Long) -> Unit,
    onContinueWatchingClicked: ((LibraryAnime) -> Unit)?,
    onToggleSelection: (LibraryAnime) -> Unit,
    onRefresh: () -> Boolean,
    getItemsForCategory: (AnimeCategory) -> List<AnimeLibraryItem>,
) {
    Column(
        modifier = Modifier.padding(
            top = contentPadding.calculateTopPadding(),
            start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
            end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
        ),
    ) {
        val pagerState = rememberPagerState(currentPage) { categories.size }

        val scope = rememberCoroutineScope()
        var isRefreshing by remember(pagerState.currentPage) { mutableStateOf(false) }

        if (showPageTabs && categories.size > 1) {
            AnimeLibraryTabs(
                categories = categories,
                pagerState = pagerState,
                showAnimeCount = showAnimeCount,
                getItemCount = { category -> getItemsForCategory(category).size },
            )
        }

        LaunchedEffect(pagerState.currentPage) {
            onChangeCurrentPage(pagerState.currentPage)
        }

        PullRefresh(
            refreshing = isRefreshing,
            onRefresh = {
                val shouldRefresh = onRefresh()
                if (shouldRefresh) {
                    isRefreshing = true
                    scope.launch {
                        delay(1.5.seconds)
                        isRefreshing = false
                    }
                }
            },
            enabled = selection.isEmpty(),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
            ) { page ->
                val category = categories.getOrNull(page) ?: return@HorizontalPager
                val items = getItemsForCategory(category)

                AnimeLibraryGrid(
                    items = items,
                    displayMode = displayMode,
                    selection = selection,
                    contentPadding = PaddingValues(
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                    onAnimeClicked = onAnimeClicked,
                    onContinueWatchingClicked = onContinueWatchingClicked,
                    onToggleSelection = onToggleSelection,
                    hasActiveFilters = hasActiveFilters,
                )
            }
        }
    }
}
