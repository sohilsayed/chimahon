package eu.kanade.presentation.anime.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.buildProgressString
import tachiyomi.domain.episode.model.Episode
import tachiyomi.presentation.core.components.material.DISABLED_ALPHA

@Composable
fun AnimeEpisodeListItem(
    episode: Episode,
    onClick: () -> Unit,
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
            .clickable(onClick = onClick)
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
            }
        }
    }
}
