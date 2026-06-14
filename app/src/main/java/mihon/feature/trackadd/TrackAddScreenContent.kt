package mihon.feature.trackadd

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import mihon.feature.trackadd.models.TrackAddItem
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.util.plus

@Composable
fun TrackAddScreenContent(
    items: ImmutableList<TrackAddItem>,
    allComplete: Boolean,
    finishedCount: Int,
    trackerName: String,
    onSearchManually: (TrackAddItem) -> Unit,
    onCancelItem: (Long) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onTrackAll: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = if (items.isNotEmpty()) {
                    "$trackerName ($finishedCount/${items.size})"
                } else {
                    trackerName
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (allComplete && items.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onTrackAll,
                ) {
                    Text(text = "Add All")
                }
            }
        },
    ) { contentPadding ->
        FastScrollLazyColumn(contentPadding = contentPadding + topSmallPaddingValues) {
            items(items, key = { it.manga.id }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .animateItemFastScroll()
                        .padding(
                            start = MaterialTheme.padding.medium,
                            end = MaterialTheme.padding.small,
                        )
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrackAddSourceItem(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        manga = item.manga,
                        onClick = {},
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.weight(0.2f),
                    )

                    val result by item.searchResult.collectAsState()
                    TrackAddResultItem(
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.Top)
                            .fillMaxHeight(),
                        result = result,
                    )

                    TrackAddActionItem(
                        modifier = Modifier.weight(0.2f),
                        result = result,
                        onSearchManually = { onSearchManually(item) },
                        onCancel = { onCancelItem(item.manga.id) },
                        onSkip = { onRemoveItem(item.manga.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackAddSourceItem(
    modifier: Modifier,
    manga: Manga,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = 150.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MangaCover.Book.ratio),
        ) {
            MangaCover.Book(
                modifier = Modifier.fillMaxWidth(),
                data = manga,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    )
                    .fillMaxHeight(0.4f)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
            Text(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart),
                text = manga.title,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun TrackAddResultItem(
    modifier: Modifier,
    result: TrackAddItem.SearchResult,
) {
    Box(modifier.height(IntrinsicSize.Min)) {
        when (result) {
            TrackAddItem.SearchResult.Searching -> {
                Box(
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .fillMaxSize()
                        .aspectRatio(MangaCover.Book.ratio),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            TrackAddItem.SearchResult.NotFound -> {
                Column(
                    Modifier
                        .widthIn(max = 150.dp)
                        .fillMaxSize()
                        .padding(4.dp),
                ) {
                    Image(
                        painter = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(MangaCover.Book.ratio)
                            .clip(MaterialTheme.shapes.extraSmall),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = "No match found",
                        modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            is TrackAddItem.SearchResult.Found -> {
                TrackAddResultFound(
                    modifier = Modifier.fillMaxSize(),
                    track = result.track,
                )
            }
            is TrackAddItem.SearchResult.Failed -> {
                Text(
                    text = result.error,
                    modifier = Modifier.padding(4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun TrackAddResultFound(
    modifier: Modifier,
    track: TrackSearch,
) {
    Column(
        modifier = modifier
            .widthIn(max = 150.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MangaCover.Book.ratio),
        ) {
            AsyncImage(
                model = track.cover_url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(MangaCover.Book.ratio)
                    .clip(MaterialTheme.shapes.extraSmall),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    )
                    .fillMaxHeight(0.4f)
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            )
            Text(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart),
                text = track.title,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun TrackAddActionItem(
    modifier: Modifier,
    result: TrackAddItem.SearchResult,
    onSearchManually: () -> Unit,
    onCancel: () -> Unit,
    onSkip: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val closeMenu = { menuExpanded = false }
    Box(modifier) {
        when (result) {
            TrackAddItem.SearchResult.Searching -> {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                    )
                }
            }
            is TrackAddItem.SearchResult.Found, TrackAddItem.SearchResult.NotFound -> {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = closeMenu,
                    offset = DpOffset(8.dp, (-56).dp),
                ) {
                    DropdownMenuItem(
                        text = { Text("Search manually") },
                        onClick = {
                            closeMenu()
                            onSearchManually()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Skip") },
                        onClick = {
                            closeMenu()
                            onSkip()
                        },
                    )
                }
            }
            is TrackAddItem.SearchResult.Failed -> {}
        }
    }
}
