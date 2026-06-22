package eu.kanade.presentation.entries.anime.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.AnimeLibraryPreferences
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AnimeLibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    preferences: AnimeLibraryPreferences = Injekt.get(),
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf("Filter", "Sort", "Display"),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterTab(preferences)
                1 -> SortTab(preferences)
                2 -> DisplayTab(preferences)
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterTab(preferences: AnimeLibraryPreferences) {
    val filterUnseen by remember { preferences.filterUnseen().changes() }.collectAsState(preferences.filterUnseen().get())
    val filterStarted by remember { preferences.filterStarted().changes() }.collectAsState(preferences.filterStarted().get())
    val filterBookmarked by remember { preferences.filterBookmarked().changes() }.collectAsState(preferences.filterBookmarked().get())
    val filterCompleted by remember { preferences.filterCompleted().changes() }.collectAsState(preferences.filterCompleted().get())
    val filterDownloaded by remember { preferences.filterDownloaded().changes() }.collectAsState(preferences.filterDownloaded().get())
    val filterFillermarked by remember { preferences.filterFillermarked().changes() }.collectAsState(preferences.filterFillermarked().get())

    TriStateItem(label = "Unseen", state = filterUnseen, onClick = { preferences.filterUnseen().set(it) })
    TriStateItem(label = "Started", state = filterStarted, onClick = { preferences.filterStarted().set(it) })
    TriStateItem(label = "Bookmarked", state = filterBookmarked, onClick = { preferences.filterBookmarked().set(it) })
    TriStateItem(label = "Completed", state = filterCompleted, onClick = { preferences.filterCompleted().set(it) })
    TriStateItem(label = "Downloaded", state = filterDownloaded, onClick = { preferences.filterDownloaded().set(it) })
    TriStateItem(label = "Fillermarked", state = filterFillermarked, onClick = { preferences.filterFillermarked().set(it) })
}

@Composable
private fun ColumnScope.SortTab(preferences: AnimeLibraryPreferences) {
    val sortMode by remember { preferences.sortingMode().changes() }.collectAsState(preferences.sortingMode().get())

    val sortTypes = listOf(
        LibrarySort.Type.Alphabetical to "Alphabetical",
        LibrarySort.Type.LastRead to "Last watched",
        LibrarySort.Type.LastUpdate to "Last update",
        LibrarySort.Type.UnreadCount to "Unseen count",
        LibrarySort.Type.TotalChapters to "Total episodes",
        LibrarySort.Type.LatestChapter to "Latest episode",
        LibrarySort.Type.ChapterFetchDate to "Episode fetch date",
        LibrarySort.Type.DateAdded to "Date added",
    )

    sortTypes.forEach { (type, label) ->
        val sortDescending = if (sortMode.type == type) !sortMode.isAscending else null
        SortItem(
            label = label,
            sortDescending = sortDescending,
            onClick = {
                val newDirection = if (sortMode.type == type) {
                    if (sortMode.isAscending) LibrarySort.Direction.Descending
                    else LibrarySort.Direction.Ascending
                } else {
                    LibrarySort.Direction.Ascending
                }
                preferences.sortingMode().set(LibrarySort(type, newDirection))
            },
        )
    }
}

@Composable
private fun ColumnScope.DisplayTab(preferences: AnimeLibraryPreferences) {
    val displayMode by remember { preferences.displayMode().changes() }.collectAsState(preferences.displayMode().get())

    val modes = listOf(
        LibraryDisplayMode.CompactGrid to "Compact grid",
        LibraryDisplayMode.ComfortableGrid to "Comfortable grid",
        LibraryDisplayMode.List to "List",
        LibraryDisplayMode.CoverOnlyGrid to "Cover only",
    )

    modes.forEach { (mode, label) ->
        FilterChip(
            selected = displayMode == mode,
            onClick = { preferences.displayMode().set(mode) },
            label = { Text(label) },
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
