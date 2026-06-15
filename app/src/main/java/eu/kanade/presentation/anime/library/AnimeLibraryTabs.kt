package eu.kanade.presentation.anime.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tachiyomi.domain.category.model.AnimeCategory

@Composable
fun AnimeLibraryTabs(
    categories: List<AnimeCategory>,
    pagerState: PagerState,
    showAnimeCount: Boolean,
    getItemCount: (AnimeCategory) -> Int,
) {
    val scope = rememberCoroutineScope()

    PrimaryScrollableTabRow(
        selectedTabIndex = pagerState.currentPage,
        edgePadding = 0.dp,
    ) {
        categories.forEachIndexed { index, category ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                text = {
                    Column {
                        Text(
                            text = if (category.isSystemCategory) "Default" else category.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (showAnimeCount) {
                            Text(
                                text = "${getItemCount(category)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
            )
        }
    }
}
