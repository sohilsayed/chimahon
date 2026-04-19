package com.canopus.chimareader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsSheet(
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scrollState = rememberScrollState()

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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Statistics",
                style = MaterialTheme.typography.headlineSmall
            )

            Section(title = "Session") {
                StatRow("Characters Read", viewModel.sessionCharactersRead.toString())
                // Assuming simple speed calculation chars per minute * 60
                // Speed calculation: chars / (time_in_seconds / 3600) = chars * 3600 / time_in_seconds
                val timeReadingSeconds = maxOf(1.0, viewModel.sessionReadingTime)
                val speed = (viewModel.sessionCharactersRead / timeReadingSeconds * 3600).toInt()
                StatRow("Reading Speed", "$speed / h")
                
                val timeStr = formatDuration(viewModel.sessionReadingTime.toLong())
                StatRow("Reading Time", timeStr)
                
                // Pause button
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.isTimerPaused = !viewModel.isTimerPaused },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (viewModel.isTimerPaused) "Resume Timer" else "Pause Timer")
                }
            }

            Section(title = "Today") {
                StatRow("Characters Read", viewModel.todayCharactersRead.toString())
                val timeReadingSeconds = maxOf(1.0, viewModel.todayReadingTime)
                val speed = (viewModel.todayCharactersRead / timeReadingSeconds * 3600).toInt()
                StatRow("Reading Speed", "$speed / h")
                StatRow("Reading Time", formatDuration(viewModel.todayReadingTime.toLong()))
            }

            Section(title = "All Time") {
                StatRow("Characters Read", viewModel.allTimeCharactersRead.toString())
                val timeReadingSeconds = maxOf(1.0, viewModel.allTimeReadingTime)
                val speed = (viewModel.allTimeCharactersRead / timeReadingSeconds * 3600).toInt()
                StatRow("Reading Speed", "$speed / h")
                StatRow("Reading Time", formatDuration(viewModel.allTimeReadingTime.toLong()))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / (60 * 60)
    
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
