package eu.kanade.presentation.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.anime.components.AnimeEpisodeListItem
import eu.kanade.presentation.anime.components.AnimeInfoHeader
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import eu.kanade.tachiyomi.ui.anime.AnimeScreenModel
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreenContent(
    state: AnimeScreenModel.State.Success,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode) -> Unit,
    onContinueWatching: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteClicked: () -> Unit,
    onDismissDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDownloadEpisode: (Episode) -> Unit = {},
    onDeleteEpisodeDownload: (Episode) -> Unit = {},
    onConfirmDownloadQuality: (Episode, Video) -> Unit = { _, _ -> },
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.anime.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDeleteClicked) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(MR.strings.action_delete),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (state.nextUnseenEpisode != null) {
                SmallExtendedFloatingActionButton(
                    onClick = onContinueWatching,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(MR.strings.anime_continue_watching)) },
                )
            }
        },
    ) { contentPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + 80.dp,
            ),
            modifier = Modifier
                .padding(top = contentPadding.calculateTopPadding())
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item(key = "info_header") {
                AnimeInfoHeader(
                    anime = state.anime,
                    onFavoriteToggle = onToggleFavorite,
                )
            }

            item(key = "episode_header") {
                HorizontalDivider()
                Text(
                    text = stringResource(MR.strings.anime_episode_count, state.allEpisodeCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            items(
                items = state.episodes,
                key = { it.id },
            ) { episode ->
                val dlState = state.episodeDownloadState[episode.id]
                AnimeEpisodeListItem(
                    episode = episode,
                    downloadState = dlState?.status ?: AnimeDownload.State.NOT_DOWNLOADED,
                    downloadProgress = dlState?.progress ?: 0,
                    onClick = { onEpisodeClicked(episode) },
                    onDownloadClick = { onDownloadEpisode(episode) },
                    onDeleteDownloadClick = { onDeleteEpisodeDownload(episode) },
                )
                HorizontalDivider()
            }
        }
    }

    when (val dialog = state.dialog) {
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
        null -> {}
    }
}
