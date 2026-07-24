package eu.kanade.presentation.entries.anime.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import tachiyomi.presentation.core.util.plus
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.domain.source.model.icon
import eu.kanade.tachiyomi.ui.entries.anime.library.AnimeLibraryItem
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.AnimeLibraryPreferences
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.selectedBackground
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun AnimeLibraryGrid(
    items: List<AnimeLibraryItem>,
    displayMode: LibraryDisplayMode,
    selection: Set<Long>,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    searchQuery: String?,
    onAnimeClicked: (Long) -> Unit,
    onContinueWatchingClicked: ((LibraryAnime) -> Unit)?,
    onToggleSelection: (LibraryAnime) -> Unit,
    onToggleRangeSelection: (LibraryAnime) -> Unit,
    onGlobalSearchClicked: () -> Unit,
) {
    val animePrefs = remember { Injekt.get<AnimeLibraryPreferences>() }

    if (items.isEmpty() && searchQuery.isNullOrEmpty()) {
        EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            stringRes = if (hasActiveFilters) MR.strings.error_no_match else MR.strings.information_empty_library,
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
                onToggleRangeSelection = onToggleRangeSelection,
                onContinueWatchingClicked = onContinueWatchingClicked,
            )
        }
        else -> {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val columnsPref by remember(isLandscape) {
                (if (isLandscape) animePrefs.landscapeColumns() else animePrefs.portraitColumns()).changes()
            }.collectAsState(
                initial = if (isLandscape) animePrefs.landscapeColumns().get() else animePrefs.portraitColumns().get(),
            )

            val gridColumns = if (columnsPref > 0) {
                GridCells.Fixed(columnsPref)
            } else {
                when (displayMode) {
                    LibraryDisplayMode.CompactGrid -> GridCells.Adaptive(96.dp)
                    LibraryDisplayMode.ComfortableGrid,
                    LibraryDisplayMode.ComfortableGridPanorama,
                    -> GridCells.Adaptive(128.dp)
                    LibraryDisplayMode.CoverOnlyGrid -> GridCells.Adaptive(80.dp)
                    LibraryDisplayMode.List -> GridCells.Adaptive(96.dp)
                }
            }

            LazyVerticalGrid(
                columns = gridColumns,
                contentPadding = contentPadding + PaddingValues(8.dp),
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
                        onLongClick = { onToggleRangeSelection(item.libraryAnime) },
                        onClickContinueWatching = if (onContinueWatchingClicked != null && item.unseenCount > 0) {
                            { onContinueWatchingClicked(item.libraryAnime) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AnimeSourceIconBadge(source: AnimeSource?) {
    if (source == null) return
    val icon = source.icon

    when {
        source.isStub && icon == null -> {
            Badge(
                imageVector = Icons.Filled.Warning,
                iconColor = MaterialTheme.colorScheme.error,
                color = MaterialTheme.colorScheme.errorContainer,
            )
        }
        icon != null -> {
            Badge(
                imageBitmap = icon,
                modifier = Modifier
                    .scale(1.3f)
                    .height(18.dp),
            )
        }
        source.id == 0L -> {
            Badge(
                imageVector = Icons.Outlined.Folder,
                color = MaterialTheme.colorScheme.tertiary,
                iconColor = MaterialTheme.colorScheme.onTertiary,
            )
        }
        else -> {
            Badge(
                imageVector = Icons.Outlined.LocalLibrary,
                color = MaterialTheme.colorScheme.tertiary,
                iconColor = MaterialTheme.colorScheme.onTertiary,
            )
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
    onClickContinueWatching: (() -> Unit)? = null,
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

        val badgePrefs = remember { Injekt.get<AnimeLibraryPreferences>() }
        val showDownloadBadge by remember { badgePrefs.downloadBadge().changes() }.collectAsState(badgePrefs.downloadBadge().get())
        val showUnseenBadge by remember { badgePrefs.unseenBadge().changes() }.collectAsState(badgePrefs.unseenBadge().get())
        val showLocalBadge by remember { badgePrefs.localBadge().changes() }.collectAsState(badgePrefs.localBadge().get())

        BadgeGroup(
            modifier = Modifier
                .padding(4.dp)
                .align(Alignment.TopStart),
        ) {
            if (showDownloadBadge && item.downloadCount > 0) {
                Badge(
                    text = item.downloadCount.toString(),
                    color = MaterialTheme.colorScheme.tertiary,
                    textColor = MaterialTheme.colorScheme.onTertiary,
                )
            }
            if (showUnseenBadge && item.unseenCount > 0) {
                Badge(text = item.unseenCount.toString())
            }
        }

        if (showLocalBadge) {
            BadgeGroup(
                modifier = Modifier
                    .padding(4.dp)
                    .align(Alignment.TopEnd),
            ) {
                AnimeSourceIconBadge(source = item.source)
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

        if (onClickContinueWatching != null) {
            FilledIconButton(
                onClick = onClickContinueWatching,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
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
    onToggleRangeSelection: (LibraryAnime) -> Unit,
    onContinueWatchingClicked: ((LibraryAnime) -> Unit)? = null,
) {
    ScrollbarLazyColumn(
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = items,
            key = { it.libraryAnime.id },
            ) { item ->
                val isSelected = item.libraryAnime.id in selection
                val badgePrefs = remember { Injekt.get<AnimeLibraryPreferences>() }
                val showUnseenBadge by remember { badgePrefs.unseenBadge().changes() }.collectAsState(badgePrefs.unseenBadge().get())
                val showLocalBadge by remember { badgePrefs.localBadge().changes() }.collectAsState(badgePrefs.localBadge().get())
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
                        onLongClick = { onToggleRangeSelection(item.libraryAnime) },
                    )
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showLocalBadge) {
                    AnimeSourceIconBadge(source = item.source)
                    Spacer(modifier = Modifier.width(4.dp))
                }
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
                if (showUnseenBadge && item.unseenCount > 0) {
                    BadgeGroup {
                        Badge(text = item.unseenCount.toString())
                    }
                }
                if (onContinueWatchingClicked != null && item.unseenCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { onContinueWatchingClicked(item.libraryAnime) },
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
