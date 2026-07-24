package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.util.buildProgressString
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun AnimeListScreen(
    screenModel: AnimeListScreenModel,
    onAnimeClick: (Long) -> Unit,
) {
    val state by screenModel.state.collectAsState()
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            context.startActivity(PlayerActivity.newStandaloneIntent(context, uri))
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(MR.strings.action_open_video))
            }
        },
    ) { contentPadding ->
        when (val s = state) {
            is AnimeListScreenModel.State.Loading -> {
                LoadingScreen(modifier = Modifier.padding(contentPadding))
            }
            is AnimeListScreenModel.State.Success -> {
                if (s.items.isEmpty()) {
                    EmptyScreen(
                        message = stringResource(MR.strings.anime_empty_message),
                        modifier = Modifier.padding(contentPadding),
                    )
                } else {
                    ScrollbarLazyColumn(
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = s.items,
                            key = { it.anime.id },
                        ) { item ->
                            AnimeListItem(
                                title = item.anime.title,
                                thumbnailUrl = item.anime.thumbnailUrl,
                                episodeName = item.lastEpisode?.name,
                                unseenCount = item.unseenCount,
                                progress = item.lastEpisode?.let {
                                    buildProgressString(it.lastSecondSeen, it.totalSeconds)
                                },
                                onClick = { onAnimeClick(item.anime.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
            is AnimeListScreenModel.State.Error -> {
                EmptyScreen(
                    message = s.error.localizedMessage ?: stringResource(MR.strings.unknown_error),
                    modifier = Modifier.padding(contentPadding),
                )
            }
        }
    }

    if (showDialog) {
        OpenVideoDialog(
            onDismiss = { showDialog = false },
            onOpenUrl = { url ->
                showDialog = false
                context.startActivity(PlayerActivity.newStandaloneIntent(context, Uri.parse(url)))
            },
            onPickFile = {
                showDialog = false
                filePickerLauncher.launch(arrayOf("video/*", "application/x-bittorrent", "application/octet-stream"))
            },
        )
    }
}

@Composable
private fun AnimeListItem(
    title: String,
    thumbnailUrl: String?,
    episodeName: String?,
    unseenCount: Int,
    progress: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(48.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(4.dp)),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .width(48.dp)
                        .aspectRatio(2f / 3f),
                )
            }

            if (unseenCount > 0) {
                BadgeGroup(modifier = Modifier.align(Alignment.TopEnd)) {
                    Badge(text = unseenCount.toString())
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (episodeName != null) {
                Text(
                    text = episodeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (progress != null) {
            Text(
                text = progress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
