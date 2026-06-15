package eu.kanade.presentation.anime.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.anime.library.AnimeLibraryItem
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.selectedBackground

@Composable
fun AnimeLibraryGrid(
    items: List<AnimeLibraryItem>,
    displayMode: LibraryDisplayMode,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    onAnimeClicked: (Long) -> Unit,
    onContinueWatchingClicked: ((LibraryAnime) -> Unit)?,
    onToggleSelection: (LibraryAnime) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = if (hasActiveFilters) "No anime matching filters" else "Your anime library is empty",
        )
        return
    }

    when (displayMode) {
        LibraryDisplayMode.List -> {
            AnimeLibraryList(
                items = items,
                selection = selection,
                contentPadding = contentPadding,
                onAnimeClicked = onAnimeClicked,
                onToggleSelection = onToggleSelection,
            )
        }
        else -> {
            val columns = when (displayMode) {
                LibraryDisplayMode.CompactGrid -> GridCells.Adaptive(96.dp)
                LibraryDisplayMode.ComfortableGrid,
                LibraryDisplayMode.ComfortableGridPanorama,
                -> GridCells.Adaptive(128.dp)
                LibraryDisplayMode.CoverOnlyGrid -> GridCells.Adaptive(80.dp)
                LibraryDisplayMode.List -> GridCells.Adaptive(96.dp)
            }

            LazyVerticalGrid(
                columns = columns,
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = items,
                    key = { it.libraryAnime.id },
                ) { item ->
                    val isSelected = item.libraryAnime.id in selection
                    AnimeGridItem(
                        item = item,
                        isSelected = isSelected,
                        showTitle = displayMode != LibraryDisplayMode.CoverOnlyGrid,
                        onClick = {
                            if (selection.isNotEmpty()) {
                                onToggleSelection(item.libraryAnime)
                            } else {
                                onAnimeClicked(item.libraryAnime.id)
                            }
                        },
                        onLongClick = { onToggleSelection(item.libraryAnime) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeGridItem(
    item: AnimeLibraryItem,
    isSelected: Boolean,
    showTitle: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .selectedBackground(isSelected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .clip(RoundedCornerShape(4.dp)),
    ) {
        AsyncImage(
            model = item.libraryAnime.anime.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .alpha(if (isSelected) 0.76f else 1f),
        )

        if (item.unseenCount > 0 || item.downloadCount > 0) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopStart),
            ) {
                if (item.downloadCount > 0) {
                    Badge(
                        text = item.downloadCount.toString(),
                        color = MaterialTheme.colorScheme.tertiary,
                        textColor = MaterialTheme.colorScheme.onTertiary,
                    )
                }
                if (item.unseenCount > 0) {
                    Badge(text = item.unseenCount.toString())
                }
            }
        }

        if (showTitle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = item.libraryAnime.anime.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AnimeLibraryList(
    items: List<AnimeLibraryItem>,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    onAnimeClicked: (Long) -> Unit,
    onToggleSelection: (LibraryAnime) -> Unit,
) {
    ScrollbarLazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = items,
            key = { it.libraryAnime.id },
        ) { item ->
            val isSelected = item.libraryAnime.id in selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectedBackground(isSelected)
                    .combinedClickable(
                        onClick = {
                            if (selection.isNotEmpty()) {
                                onToggleSelection(item.libraryAnime)
                            } else {
                                onAnimeClicked(item.libraryAnime.id)
                            }
                        },
                        onLongClick = { onToggleSelection(item.libraryAnime) },
                    )
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = item.libraryAnime.anime.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(48.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = item.libraryAnime.anime.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.unseenCount > 0) {
                    BadgeGroup {
                        Badge(text = item.unseenCount.toString())
                    }
                }
            }
        }
    }
}
