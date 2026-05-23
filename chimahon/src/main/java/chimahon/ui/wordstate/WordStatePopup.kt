package chimahon.ui.wordstate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import chimahon.jiten.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordStatePopup(
    wordInfo: WordTapInfo,
    onDismiss: () -> Unit,
    onReview: (Int) -> Unit,
    onSetState: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = wordInfo.card

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // Word header
            Text(
                text = card?.spelling ?: wordInfo.word,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (card != null && card.reading.isNotBlank() && card.spelling != card.reading) {
                Text(
                    text = card.reading,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(Modifier.height(6.dp))

            // State indicator + frequency
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(JitenColors.stateColor(wordInfo.currentState)), CircleShape),
                )
                Text(
                    text = wordInfo.currentState.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                card?.frequencyDisplay?.let { freq ->
                    Text(
                        text = freq,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            // POS tags
            if (!card?.partsOfSpeech.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    card.partsOfSpeech.forEach { pos ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = pos,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            // Meanings
            if (!card?.meanings.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp),
                ) {
                    items(card.meanings) { meaning ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "\u2022",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = meaning.glosses.joinToString("; "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (meaning.partsOfSpeech.isNotEmpty()) {
                            Text(
                                text = meaning.partsOfSpeech.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }

            // Conjugations
            if (!card?.conjugations.isNullOrEmpty()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Conjugations",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = card.conjugations.joinToString(" \u2192 "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Sentence context
            if (!wordInfo.sentence.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Context",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = wordInfo.sentence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // SRS Grade buttons
            Spacer(Modifier.height(16.dp))
            Text(
                text = "SRS Grade",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val gradeButtons = listOf(
                    Triple("Again", 1, JitenColors.gradeColor("again")),
                    Triple("Hard", 2, JitenColors.gradeColor("hard")),
                    Triple("Good", 3, JitenColors.gradeColor("good")),
                    Triple("Easy", 4, JitenColors.gradeColor("easy")),
                )
                gradeButtons.forEach { (label, rating, btnColor) ->
                    OutlinedButton(
                        onClick = { onReview(rating) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(label, color = Color(btnColor))
                    }
                }
            }

            // Direct state buttons
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Set State",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val stateButtons = listOf(
                    Triple("Never Forget", "mastered", JitenColors.stateColor("mastered")),
                    Triple("Blacklist", "blacklisted", JitenColors.stateColor("blacklisted")),
                    Triple("Reset", "new", JitenColors.stateColor("new")),
                )
                stateButtons.forEach { (label, state, btnColor) ->
                    OutlinedButton(
                        onClick = { onSetState(state) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(label, color = Color(btnColor))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
