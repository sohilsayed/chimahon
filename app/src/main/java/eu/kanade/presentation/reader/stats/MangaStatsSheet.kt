package eu.kanade.presentation.reader.stats

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.canopus.chimareader.data.MangaStatsStorage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max

data class MangaStatsDisplay(
    val charactersRead: Int = 0,
    val readingTimeMs: Long = 0,
) {
    val readingSpeed: Int
        get() = if (readingTimeMs > 0) {
            (charactersRead.toDouble() / (readingTimeMs / 3600000.0)).toInt()
        } else 0
}

data class MangaStatsEstimate(
    val remainingBookCharacters: Int = 0,
    val remainingChapterCharacters: Int = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaStatsSheet(
    context: Context,
    mangaId: Long,
    sessionCharacters: Int,
    sessionTimeMs: Long,
    estimate: MangaStatsEstimate = MangaStatsEstimate(),
    isTracking: Boolean?,
    onToggleTracking: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scrollState = rememberScrollState()

    var todayStats by remember { mutableStateOf(MangaStatsDisplay()) }
    var allTimeStats by remember { mutableStateOf(MangaStatsDisplay()) }

    LaunchedEffect(mangaId) {
        val all = withContext(Dispatchers.IO) {
            MangaStatsStorage.loadAll(context)
        }
        val today = all.filter { it.dateKey == LocalDate.now().toString() && it.mangaId == mangaId }
        val allForManga = all.filter { it.mangaId == mangaId }
        todayStats = MangaStatsDisplay(
            charactersRead = today.sumOf { it.charactersRead },
            readingTimeMs = today.sumOf { it.readingTime },
        )
        allTimeStats = MangaStatsDisplay(
            charactersRead = allForManga.sumOf { it.charactersRead },
            readingTimeMs = allForManga.sumOf { it.readingTime },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(MR.strings.action_manga_stats),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (onToggleTracking != null) {
                    IconButton(onClick = onToggleTracking) {
                        Icon(
                            imageVector = if (isTracking == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Section(title = "Session") {
                val speed = sessionSpeed(sessionCharacters, sessionTimeMs)
                StatRow("Characters Read", sessionCharacters.toString())
                StatRow("Reading Speed", "$speed /h")
                StatRow("Reading Time", formatDuration(sessionTimeMs / 1000))
                StatRow(
                    "Time to finish Book",
                    formatDurationSeconds(secondsRemaining(estimate.remainingBookCharacters, speed)),
                )
                StatRow(
                    "Time to finish Chapter",
                    formatDurationSeconds(secondsRemaining(estimate.remainingChapterCharacters, speed)),
                )
            }

            Section(title = "Today") {
                StatRow("Characters Read", todayStats.charactersRead.toString())
                StatRow("Reading Speed", "${todayStats.readingSpeed} /h")
                StatRow("Reading Time", formatDuration(todayStats.readingTimeMs / 1000))
            }

            Section(title = "All Time") {
                StatRow("Characters Read", allTimeStats.charactersRead.toString())
                StatRow("Reading Speed", "${allTimeStats.readingSpeed} /h")
                StatRow("Reading Time", formatDuration(allTimeStats.readingTimeMs / 1000))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

private fun sessionSpeed(chars: Int, timeMs: Long): Int {
    if (timeMs <= 0) return 0
    return (chars.toDouble() / (timeMs / 3600000.0)).toInt()
}

private fun formatDurationSeconds(seconds: Double): String {
    val totalSeconds = max(seconds.toLong(), 0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val remainingSeconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${remainingSeconds}s"
        minutes > 0 -> "${minutes}m ${remainingSeconds}s"
        else -> "${remainingSeconds}s"
    }
}

private fun secondsRemaining(remainingCharacters: Int, speed: Int): Double {
    if (speed <= 0) return 0.0
    return max(remainingCharacters, 0).toDouble() / (speed.toDouble() / 3600.0)
}
