package eu.kanade.tachiyomi.ui.player.controls.components.panels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.PlayerViewModel.SubtitleCue
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.presentation.core.components.material.padding

@Composable
fun SubtitleListPanel(
    cues: ImmutableList<SubtitleCue>,
    activeCueIndex: Int?,
    onSelectCue: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismissRequest)

    Box(modifier = modifier.fillMaxSize()) {
        SubtitleSideList(
            cues = cues,
            activeCueIndex = activeCueIndex,
            onSelectCue = onSelectCue,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun SubtitleSideList(
    cues: ImmutableList<SubtitleCue>,
    activeCueIndex: Int?,
    onSelectCue: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val width = (configuration.screenWidthDp.dp * 0.34f)
        .coerceIn(280.dp, minOf(430.dp, configuration.screenWidthDp.dp - 24.dp))

    SubtitleCueLazyList(
        cues = cues,
        activeCueIndex = activeCueIndex,
        onSelectCue = onSelectCue,
        modifier = modifier
            .padding(end = 8.dp, top = 36.dp, bottom = 36.dp)
            .width(width)
            .fillMaxHeight(),
    )
}

@Composable
private fun SubtitleCueLazyList(
    cues: ImmutableList<SubtitleCue>,
    activeCueIndex: Int?,
    onSelectCue: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val activePosition = activePosition(cues, activeCueIndex)

    LaunchedEffect(activePosition, cues.size) {
        if (cues.isEmpty()) return@LaunchedEffect
        listState.animateScrollToCenteredItem(activePosition)
    }

    if (cues.isEmpty()) {
        EmptySubtitleListMessage(modifier)
        return
    }

    BoxWithConstraints(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(vertical = maxHeight * 0.5f),
        ) {
            items(cues, key = { it.index }) { cue ->
                SubtitleCueSideRow(
                    cue = cue,
                    selected = cue.index == activeCueIndex,
                    onClick = { onSelectCue(cue.index) },
                )
            }
        }
    }
}

private suspend fun LazyListState.animateScrollToCenteredItem(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        scrollToItem(index)
        withFrameNanos { }
    }

    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = layoutInfo.viewportSize.height / 2
    val itemCenter = item.offset + item.size / 2
    val scrollDelta = itemCenter - viewportCenter

    if (scrollDelta != 0) {
        animateScrollBy(scrollDelta.toFloat())
    }
}

@Composable
private fun SubtitleCueSideRow(
    cue: SubtitleCue,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = cue.text,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(activeLineColor(selected), RoundedCornerShape(2.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = subtitleLogTextStyle(),
        color = Color.White,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptySubtitleListMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Subtitle lines will appear here",
            modifier = Modifier.padding(MaterialTheme.padding.medium),
            style = subtitleLogTextStyle().copy(textAlign = TextAlign.Center),
            color = Color.White.copy(alpha = 0.74f),
        )
    }
}

@Composable
private fun subtitleLogTextStyle(): TextStyle {
    return MaterialTheme.typography.bodyLarge.copy(
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.95f),
            blurRadius = 8f,
        ),
    )
}

@Composable
private fun activeLineColor(selected: Boolean): Color {
    return if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
    } else {
        Color.Transparent
    }
}

private fun activePosition(cues: List<SubtitleCue>, activeCueIndex: Int?): Int {
    if (cues.isEmpty()) return 0
    val active = cues.indexOfFirst { it.index == activeCueIndex }
    return if (active >= 0) active else cues.lastIndex
}
