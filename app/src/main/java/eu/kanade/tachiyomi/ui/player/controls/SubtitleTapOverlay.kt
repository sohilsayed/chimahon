package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ASS_OVERRIDE_REGEX = Regex("""\{\\[^}]*\}""")

private fun stripAssOverrides(text: String): String {
    return ASS_OVERRIDE_REGEX.replace(text, "").replace("\\N", "\n").replace("\\n", "\n")
}

private val HIGHLIGHT_COLOR = Color(0xFF4FC3F7)

private val subtitleStyle = TextStyle(
    color = Color.White,
    fontSize = 24.sp,
    textAlign = TextAlign.Center,
    shadow = Shadow(
        color = Color.Black,
        offset = Offset(1f, 1f),
        blurRadius = 8f,
    ),
)

private val highlightStyle = subtitleStyle.copy(color = HIGHLIGHT_COLOR)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubtitleTapOverlay(
    subText: String,
    onWordTapped: (word: String, fullText: String, charOffset: Int, anchorX: Float, anchorY: Float) -> Unit,
    highlightRange: IntRange? = null,
    modifier: Modifier = Modifier,
) {
    val cleanText = remember(subText) { stripAssOverrides(subText) }

    if (cleanText.isBlank()) return

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 72.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                var charIndex = 0
                for (char in cleanText) {
                    if (char == '\n') {
                        charIndex++
                        continue
                    }
                    val idx = charIndex
                    key(idx) {
                        val isHighlighted = highlightRange != null && idx in highlightRange
                        val style = if (isHighlighted) highlightStyle else subtitleStyle

                        val anchorX = remember { mutableFloatStateOf(0f) }
                        val anchorY = remember { mutableFloatStateOf(0f) }

                        val charModifier = Modifier
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInRoot()
                                anchorX.floatValue = pos.x + coords.size.width / 2f
                                anchorY.floatValue = pos.y
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                val lookupLen = minOf(10, cleanText.length - idx)
                                val lookupWord = cleanText.substring(idx, idx + lookupLen)
                                onWordTapped(lookupWord, cleanText, idx, anchorX.floatValue, anchorY.floatValue)
                            }

                        Text(
                            text = char.toString(),
                            style = style,
                            modifier = charModifier,
                        )
                    }
                    charIndex++
                }
            }
        }
    }
}
