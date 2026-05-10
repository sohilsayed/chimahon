package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vivvvek.seeker.Seeker
import dev.vivvvek.seeker.SeekerDefaults
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.ui.player.formatTime
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun TimeBar(
    currentPositionSec: Long,
    durationSec: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    chapters: ImmutableList<Segment> = persistentListOf(),
) {
    var seekingPosition by remember { mutableFloatStateOf(-1f) }
    val position = if (seekingPosition >= 0f) seekingPosition else currentPositionSec.toFloat()
    val duration = durationSec.toFloat().coerceAtLeast(1f)

    Row(
        modifier = modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = formatTime(
                if (seekingPosition >= 0f) seekingPosition.toLong() else currentPositionSec,
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(72.dp),
        )

        Seeker(
            value = position.coerceIn(0f, duration),
            range = 0f..duration,
            onValueChange = { seekingPosition = it },
            onValueChangeFinished = {
                if (seekingPosition >= 0f) {
                    onSeek(seekingPosition / duration)
                    seekingPosition = -1f
                }
            },
            segments = chapters
                .filter { it.start in 0f..duration }
                .let {
                    if (it.isNotEmpty() && it[0].start != 0f) {
                        persistentListOf(Segment("", 0f)) + it
                    } else {
                        it
                    }
                },
            modifier = Modifier.weight(1f),
            colors = SeekerDefaults.seekerColors(
                progressColor = MaterialTheme.colorScheme.primary,
                thumbColor = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.background,
                readAheadColor = MaterialTheme.colorScheme.inversePrimary,
            ),
        )

        Text(
            text = formatTime(durationSec),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(72.dp),
        )
    }
}
