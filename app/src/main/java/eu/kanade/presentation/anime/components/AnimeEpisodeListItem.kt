package eu.kanade.presentation.anime.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import eu.kanade.tachiyomi.ui.player.buildProgressString
import tachiyomi.domain.episode.model.Episode
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA
import tachiyomi.presentation.core.util.selectedBackground

@Composable
fun AnimeEpisodeListItem(
    episode: Episode,
    downloadState: AnimeDownload.State,
    downloadProgress: Int,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDownloadClick: () -> Unit,
    onDeleteDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = if (episode.seen) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectedBackground(selected)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (episode.bookmark) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        CompositionLocalProvider(LocalContentColor provides textColor) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val progress = buildProgressString(episode.lastSecondSeen, episode.totalSeconds)
                if (progress != null) {
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (episode.seen) DISABLED_ALPHA else 1f,
                        ),
                        maxLines = 1,
                    )
                }

                if (!episode.seen && episode.lastSecondSeen > 0 && episode.totalSeconds > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            (episode.lastSecondSeen.toFloat() / episode.totalSeconds.toFloat())
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }

        EpisodeDownloadIndicator(
            state = downloadState,
            progress = downloadProgress,
            onDownloadClick = onDownloadClick,
            onDeleteClick = onDeleteDownloadClick,
        )
    }
}

@Composable
private fun EpisodeDownloadIndicator(
    state: AnimeDownload.State,
    progress: Int,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    when (state) {
        AnimeDownload.State.NOT_DOWNLOADED -> {
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        AnimeDownload.State.QUEUE -> {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        AnimeDownload.State.DOWNLOADING -> {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        AnimeDownload.State.DOWNLOADED -> {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        AnimeDownload.State.ERROR -> {
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
