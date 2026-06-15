package eu.kanade.presentation.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastMap
import eu.kanade.presentation.anime.components.AnimeBottomActionMenu
import eu.kanade.presentation.anime.components.AnimeEpisodeListItem
import eu.kanade.presentation.anime.components.AnimeInfoHeader
import eu.kanade.presentation.anime.components.AnimeToolbar
import eu.kanade.presentation.anime.components.EpisodeHeader
import eu.kanade.tachiyomi.animesource.model.Video
import tachiyomi.core.common.preference.TriState
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import eu.kanade.tachiyomi.ui.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.anime.EpisodeList
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.shouldExpandFAB

@Composable
fun AnimeScreen(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    isTabletUi: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode) -> Unit,
    onContinueWatching: () -> Unit,
    onAddToLibraryClicked: () -> Unit,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onShareClicked: (() -> Unit)? = null,
    onEditCategoryClicked: (() -> Unit)? = null,
    onCoverClicked: () -> Unit = {},
    onDownloadEpisode: (EpisodeList.Item) -> Unit = {},
    onDeleteEpisodeDownload: (EpisodeList.Item) -> Unit = {},
    onConfirmDownloadQuality: (Episode, Video) -> Unit = { _, _ -> },
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    onMultiDownloadClicked: (List<Episode>) -> Unit = {},
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onUnseenFilterChanged: (TriState) -> Unit = {},
    onBookmarkedFilterChanged: (TriState) -> Unit = {},
    onSortModeChanged: (Long) -> Unit = {},
    onDisplayModeChanged: (Long) -> Unit = {},
    onDismissDialog: () -> Unit,
    onDeleteClicked: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onAddToLibraryAnywayClicked: () -> Unit = {},
) {
    if (!isTabletUi) {
        AnimeScreenSmallImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            navigateUp = navigateUp,
            onEpisodeClicked = onEpisodeClicked,
            onContinueWatching = onContinueWatching,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onTagSearch = onTagSearch,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onShareClicked = onShareClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onCoverClicked = onCoverClicked,
            onDownloadEpisode = onDownloadEpisode,
            onDeleteEpisodeDownload = onDeleteEpisodeDownload,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onMultiDownloadClicked = onMultiDownloadClicked,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
        )
    } else {
        AnimeScreenLargeImpl(
            state = state,
            snackbarHostState = snackbarHostState,
            navigateUp = navigateUp,
            onEpisodeClicked = onEpisodeClicked,
            onContinueWatching = onContinueWatching,
            onAddToLibraryClicked = onAddToLibraryClicked,
            onTagSearch = onTagSearch,
            onFilterClicked = onFilterButtonClicked,
            onRefresh = onRefresh,
            onShareClicked = onShareClicked,
            onEditCategoryClicked = onEditCategoryClicked,
            onCoverClicked = onCoverClicked,
            onDownloadEpisode = onDownloadEpisode,
            onDeleteEpisodeDownload = onDeleteEpisodeDownload,
            onMultiBookmarkClicked = onMultiBookmarkClicked,
            onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
            onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
            onMultiDeleteClicked = onMultiDeleteClicked,
            onMultiDownloadClicked = onMultiDownloadClicked,
            onEpisodeSelected = onEpisodeSelected,
            onAllEpisodeSelected = onAllEpisodeSelected,
            onInvertSelection = onInvertSelection,
        )
    }

    // Dialogs
    when (val dialog = state.dialog) {
        is AnimeScreenModel.Dialog.SettingsSheet -> {
            EpisodeSettingsDialog(
                onDismissRequest = onDismissDialog,
                anime = state.anime,
                onUnseenFilterChanged = onUnseenFilterChanged,
                onBookmarkedFilterChanged = onBookmarkedFilterChanged,
                onSortModeChanged = onSortModeChanged,
                onDisplayModeChanged = onDisplayModeChanged,
            )
        }
        is AnimeScreenModel.Dialog.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text(stringResource(MR.strings.action_delete)) },
                text = { Text(stringResource(MR.strings.anime_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = onConfirmDelete) {
                        Text(stringResource(MR.strings.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        is AnimeScreenModel.Dialog.DownloadLoading -> {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text("Resolving videos...") },
                text = { CircularProgressIndicator() },
                confirmButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        is AnimeScreenModel.Dialog.QualitySelection -> {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text("Select quality") },
                text = {
                    LazyColumn {
                        items(
                            items = dialog.videos,
                            key = { it.videoUrl },
                        ) { video ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = video.videoTitle.ifBlank {
                                            video.resolution?.let { "${it}p" } ?: "Unknown"
                                        },
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onConfirmDownloadQuality(dialog.episode, video)
                                },
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        is AnimeScreenModel.Dialog.DuplicateAnime -> {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text("Duplicate in library") },
                text = {
                    Text(
                        "An anime with the same title already exists in your library: " +
                            dialog.duplicates.joinToString { it.title },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDismissDialog()
                        onAddToLibraryAnywayClicked()
                    }) {
                        Text("Add anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }
        null -> {}
    }
}

@Composable
private fun AnimeScreenSmallImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode) -> Unit,
    onContinueWatching: () -> Unit,
    onAddToLibraryClicked: () -> Unit,
    onTagSearch: (String) -> Unit,
    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onCoverClicked: () -> Unit,
    onDownloadEpisode: (EpisodeList.Item) -> Unit,
    onDeleteEpisodeDownload: (EpisodeList.Item) -> Unit,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    onMultiDownloadClicked: (List<Episode>) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val episodeListState = rememberLazyListState()

    val (episodes, isAnySelected) = remember(state) {
        Pair(state.episodes, state.isAnySelected)
    }

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else {
            navigateUp()
        }
    })

    Scaffold(
        topBar = {
            val selectedEpisodeCount = remember(episodes) {
                episodes.count { it.selected }
            }
            val isFirstItemVisible by remember {
                derivedStateOf { episodeListState.firstVisibleItemIndex == 0 }
            }
            val isFirstItemScrolled by remember {
                derivedStateOf { episodeListState.firstVisibleItemScrollOffset > 0 }
            }
            val titleAlpha by animateFloatAsState(
                if (!isFirstItemVisible) 1f else 0f,
                label = "Top Bar Title",
            )
            val backgroundAlpha by animateFloatAsState(
                if (!isFirstItemVisible || isFirstItemScrolled) 1f else 0f,
                label = "Top Bar Background",
            )
            AnimeToolbar(
                title = state.anime.title,
                hasFilters = state.filterActive,
                navigateUp = navigateUp,
                onClickFilter = onFilterClicked,
                onClickRefresh = onRefresh,
                onClickShare = onShareClicked,
                onClickEditCategory = onEditCategoryClicked,
                actionModeCounter = selectedEpisodeCount,
                onCancelActionMode = { onAllEpisodeSelected(false) },
                onSelectAll = { onAllEpisodeSelected(true) },
                onInvertSelection = onInvertSelection,
                titleAlphaProvider = { titleAlpha },
                backgroundAlphaProvider = { backgroundAlpha },
            )
        },
        bottomBar = {
            val selectedEpisodes = remember(episodes) {
                episodes.filter { it.selected }
            }
            SharedAnimeBottomActionMenu(
                selected = selectedEpisodes,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                onMultiDeleteClicked = onMultiDeleteClicked,
                onMultiDownloadClicked = onMultiDownloadClicked,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            val isFABVisible = remember(episodes) {
                episodes.fastAny { !it.episode.seen } && !isAnySelected
            }
            SmallExtendedFloatingActionButton(
                text = {
                    val isWatching = remember(state.episodes) {
                        state.episodes.fastAny { it.episode.seen }
                    }
                    Text(
                        text = stringResource(
                            if (isWatching) MR.strings.action_resume else MR.strings.action_start,
                        ),
                    )
                },
                icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = onContinueWatching,
                expanded = episodeListState.shouldExpandFAB(),
                modifier = Modifier.animateFloatingActionButton(
                    visible = isFABVisible,
                    alignment = Alignment.BottomEnd,
                ),
            )
        },
    ) { contentPadding ->
        val topPadding = contentPadding.calculateTopPadding()

        PullRefresh(
            refreshing = state.isRefreshingData,
            onRefresh = onRefresh,
            enabled = !isAnySelected,
            indicatorPadding = PaddingValues(top = topPadding),
        ) {
            val layoutDirection = LocalLayoutDirection.current
            VerticalFastScroller(
                listState = episodeListState,
                topContentPadding = topPadding,
                endContentPadding = contentPadding.calculateEndPadding(layoutDirection),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    state = episodeListState,
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                ) {
                    item(
                        key = AnimeScreenItem.INFO_BOX,
                        contentType = AnimeScreenItem.INFO_BOX,
                    ) {
                        AnimeInfoHeader(
                            anime = state.anime,
                            appBarPadding = topPadding,
                            onFavoriteToggle = onAddToLibraryClicked,
                            onTagSearch = onTagSearch,
                            onCoverClick = onCoverClicked,
                        )
                    }

                    item(
                        key = AnimeScreenItem.EPISODE_HEADER,
                        contentType = AnimeScreenItem.EPISODE_HEADER,
                    ) {
                        EpisodeHeader(
                            enabled = !isAnySelected,
                            episodeCount = state.allEpisodeCount,
                            onClick = onFilterClicked,
                        )
                    }

                    sharedEpisodeItems(
                        episodes = episodes,
                        isAnySelected = isAnySelected,
                        onEpisodeClicked = onEpisodeClicked,
                        onDownloadEpisode = onDownloadEpisode,
                        onDeleteEpisodeDownload = onDeleteEpisodeDownload,
                        onEpisodeSelected = onEpisodeSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeScreenLargeImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode) -> Unit,
    onContinueWatching: () -> Unit,
    onAddToLibraryClicked: () -> Unit,
    onTagSearch: (String) -> Unit,
    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onCoverClicked: () -> Unit,
    onDownloadEpisode: (EpisodeList.Item) -> Unit,
    onDeleteEpisodeDownload: (EpisodeList.Item) -> Unit,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    onMultiDownloadClicked: (List<Episode>) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
) {
    val episodeListState = rememberLazyListState()

    val (episodes, isAnySelected) = remember(state) {
        Pair(state.episodes, state.isAnySelected)
    }

    BackHandler(onBack = {
        if (isAnySelected) {
            onAllEpisodeSelected(false)
        } else {
            navigateUp()
        }
    })

    Scaffold(
        topBar = {
            val selectedEpisodeCount = remember(episodes) {
                episodes.count { it.selected }
            }
            AnimeToolbar(
                title = state.anime.title,
                hasFilters = state.filterActive,
                navigateUp = navigateUp,
                onClickFilter = onFilterClicked,
                onClickRefresh = onRefresh,
                onClickShare = onShareClicked,
                onClickEditCategory = onEditCategoryClicked,
                actionModeCounter = selectedEpisodeCount,
                onCancelActionMode = { onAllEpisodeSelected(false) },
                onSelectAll = { onAllEpisodeSelected(true) },
                onInvertSelection = onInvertSelection,
                titleAlphaProvider = { 1f },
                backgroundAlphaProvider = { 1f },
            )
        },
        bottomBar = {
            val selectedEpisodes = remember(episodes) {
                episodes.filter { it.selected }
            }
            SharedAnimeBottomActionMenu(
                selected = selectedEpisodes,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                onMultiDeleteClicked = onMultiDeleteClicked,
                onMultiDownloadClicked = onMultiDownloadClicked,
                fillFraction = 0.5f,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            val isFABVisible = remember(episodes) {
                episodes.fastAny { !it.episode.seen } && !isAnySelected
            }
            SmallExtendedFloatingActionButton(
                text = {
                    val isWatching = remember(state.episodes) {
                        state.episodes.fastAny { it.episode.seen }
                    }
                    Text(
                        text = stringResource(
                            if (isWatching) MR.strings.action_resume else MR.strings.action_start,
                        ),
                    )
                },
                icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = onContinueWatching,
                expanded = episodeListState.shouldExpandFAB(),
                modifier = Modifier.animateFloatingActionButton(
                    visible = isFABVisible,
                    alignment = Alignment.BottomEnd,
                ),
            )
        },
    ) { contentPadding ->
        TwoPanelBox(
            modifier = Modifier.padding(
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
            ),
            startContent = {
                AnimeInfoHeader(
                    anime = state.anime,
                    appBarPadding = contentPadding.calculateTopPadding(),
                    onFavoriteToggle = onAddToLibraryClicked,
                    onTagSearch = onTagSearch,
                    onCoverClick = onCoverClicked,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = contentPadding.calculateBottomPadding()),
                )
            },
            endContent = {
                VerticalFastScroller(
                    listState = episodeListState,
                    topContentPadding = contentPadding.calculateTopPadding(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        state = episodeListState,
                        contentPadding = PaddingValues(
                            top = contentPadding.calculateTopPadding(),
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                    ) {
                        item(
                            key = AnimeScreenItem.EPISODE_HEADER,
                            contentType = AnimeScreenItem.EPISODE_HEADER,
                        ) {
                            EpisodeHeader(
                                enabled = !isAnySelected,
                                episodeCount = state.allEpisodeCount,
                                onClick = onFilterClicked,
                            )
                        }

                        sharedEpisodeItems(
                            episodes = episodes,
                            isAnySelected = isAnySelected,
                            onEpisodeClicked = onEpisodeClicked,
                            onDownloadEpisode = onDownloadEpisode,
                            onDeleteEpisodeDownload = onDeleteEpisodeDownload,
                            onEpisodeSelected = onEpisodeSelected,
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SharedAnimeBottomActionMenu(
    selected: List<EpisodeList.Item>,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    onMultiDownloadClicked: (List<Episode>) -> Unit = {},
    fillFraction: Float = 1f,
    modifier: Modifier = Modifier,
) {
    AnimeBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = modifier.fillMaxWidth(fillFraction),
        onBookmarkClicked = {
            onMultiBookmarkClicked(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAll { it.episode.bookmark } },
        onMarkAsSeenClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, true)
        }.takeIf { selected.fastAny { !it.episode.seen } },
        onMarkAsUnseenClicked = {
            onMultiMarkAsSeenClicked(selected.fastMap { it.episode }, false)
        }.takeIf { selected.fastAny { it.episode.seen || it.episode.lastSecondSeen > 0L } },
        onMarkPreviousAsSeenClicked = {
            onMarkPreviousAsSeenClicked(selected[0].episode)
        }.takeIf { selected.size == 1 },
        onDownloadClicked = {
            onMultiDownloadClicked(selected.fastMap { it.episode })
        }.takeIf {
            selected.fastAny { it.downloadState != AnimeDownload.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected.fastMap { it.episode })
        }.takeIf {
            selected.fastAny { it.downloadState == AnimeDownload.State.DOWNLOADED }
        },
    )
}

private fun LazyListScope.sharedEpisodeItems(
    episodes: List<EpisodeList.Item>,
    isAnySelected: Boolean,
    onEpisodeClicked: (Episode) -> Unit,
    onDownloadEpisode: (EpisodeList.Item) -> Unit,
    onDeleteEpisodeDownload: (EpisodeList.Item) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
) {
    items(
        items = episodes,
        key = { it.id },
        contentType = { AnimeScreenItem.EPISODE },
    ) { item ->
        AnimeEpisodeListItem(
            episode = item.episode,
            downloadState = item.downloadState,
            downloadProgress = item.downloadProgress,
            selected = item.selected,
            onClick = {
                when {
                    isAnySelected -> onEpisodeSelected(item, !item.selected, true, false)
                    else -> onEpisodeClicked(item.episode)
                }
            },
            onLongClick = {
                onEpisodeSelected(item, !item.selected, true, true)
            },
            onDownloadClick = { onDownloadEpisode(item) },
            onDeleteDownloadClick = { onDeleteEpisodeDownload(item) },
        )
        HorizontalDivider()
    }
}

private enum class AnimeScreenItem {
    INFO_BOX,
    EPISODE_HEADER,
    EPISODE,
}
