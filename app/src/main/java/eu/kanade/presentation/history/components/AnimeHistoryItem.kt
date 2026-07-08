package eu.kanade.presentation.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.anime.components.AnimeCover
import eu.kanade.presentation.entries.components.DotSeparatorText
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.util.lang.toTimestampString
import tachiyomi.domain.history.model.AnimeHistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

private val HistoryItemHeight = 96.dp

@Composable
fun AnimeHistoryItem(
    history: AnimeHistoryWithRelations,
    onClickCover: () -> Unit,
    onClickResume: () -> Unit,
    onClickDelete: () -> Unit,
    onClickFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClickResume)
            .height(HistoryItemHeight)
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimeCover.Book(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(onClick = onClickCover),
            data = history.coverData,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
        ) {
            Text(
                text = history.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            val watchedAt = remember { history.watchedAt?.toTimestampString() ?: "" }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = if (history.episodeNumber > -1) {
                        stringResource(
                            MR.strings.recent_anime_time,
                            formatEpisodeNumber(history.episodeNumber),
                            watchedAt,
                        )
                    } else {
                        watchedAt
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!history.seen && history.lastSecondSeen > 0) {
                    DotSeparatorText()
                    Text(
                        text = formatDuration(history.lastSecondSeen / 1000L, history.totalSeconds / 1000L),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (!history.coverData.isAnimeFavorite) {
            IconButton(onClick = onClickFavorite) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(MR.strings.add_to_library),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun formatDuration(seconds: Long, totalSeconds: Long): String {
    val pos = maxOf(seconds, 0L)
    val total = maxOf(totalSeconds, 0L)
    val fmtPos = formatSeconds(pos)
    val fmtTotal = formatSeconds(total)
    return "$fmtPos / $fmtTotal"
}

private fun formatSeconds(seconds: Long): String {
    val s = seconds % 60
    val m = (seconds / 60) % 60
    val h = seconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
