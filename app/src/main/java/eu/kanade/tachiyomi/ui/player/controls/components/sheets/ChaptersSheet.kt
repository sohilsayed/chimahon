package eu.kanade.tachiyomi.ui.player.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.formatTime
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ChaptersSheet(
    chapters: ImmutableList<IndexedSegment>,
    currentChapter: IndexedSegment?,
    onClick: (IndexedSegment) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GenericTracksSheet(
        tracks = chapters,
        header = {
            TrackSheetTitle(
                title = stringResource(MR.strings.player_sheets_chapters_title),
                modifier = modifier.padding(top = MaterialTheme.padding.small),
            )
        },
        track = { chapter ->
            ChapterTrack(
                chapter = chapter,
                index = chapters.indexOf(chapter),
                selected = currentChapter == chapter,
                onClick = { onClick(chapter) },
            )
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    )
}

@Composable
fun ChapterTrack(
    chapter: IndexedSegment,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.small, horizontal = MaterialTheme.padding.medium),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${index + 1}. ${chapter.name}",
            fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatTime(chapter.start.toLong()),
            fontStyle = if (selected) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
        )
    }
}
