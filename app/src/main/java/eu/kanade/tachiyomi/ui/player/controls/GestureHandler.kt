package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.ui.player.controls.components.DoubleTapSeekTriangles
import eu.kanade.tachiyomi.ui.player.setting.GesturePreferences
import eu.kanade.tachiyomi.ui.player.setting.PlayerPreferences
import kotlinx.coroutines.delay
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun GestureHandler(
    isLocked: Boolean,
    subtitleActive: Boolean,
    currentPositionSec: Long,
    durationSec: Long,
    onToggleControls: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSeekTo: (Int) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gesturePreferences = remember { Injekt.get<GesturePreferences>() }
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
    val skipLength = remember { playerPreferences.doubleTapSeekLength().get() }
    val gestureVolumeBrightness = remember { gesturePreferences.gestureVolumeBrightness().get() }
    val seekGesture = remember { gesturePreferences.gestureHorizontalSeek().get() }
    val swapVolumeBrightness = remember { gesturePreferences.swapVolumeBrightness().get() }

    var seekAmount by remember { mutableIntStateOf(0) }
    var isSeekingForward by remember { mutableStateOf(true) }
    var isDoubleTapSeeking by remember { mutableStateOf(false) }
    var seekPreviewText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seekAmount) {
        if (seekAmount != 0) {
            delay(800)
            isDoubleTapSeeking = false
            seekAmount = 0
            seekPreviewText = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    detectTapGestures(
                        onTap = {
                            if (isLocked) return@detectTapGestures
                            onToggleControls()
                        },
                        onDoubleTap = { offset ->
                            if (isLocked) return@detectTapGestures
                            when {
                                offset.x > size.width * 3 / 5 -> {
                                    if (!isSeekingForward) seekAmount = 0
                                    isSeekingForward = true
                                    seekAmount += skipLength
                                    isDoubleTapSeeking = true
                                    onSeekRelative(skipLength)
                                }
                                offset.x < size.width * 2 / 5 -> {
                                    if (isSeekingForward) seekAmount = 0
                                    isSeekingForward = false
                                    seekAmount += skipLength
                                    isDoubleTapSeeking = true
                                    onSeekRelative(-skipLength)
                                }
                                else -> onPlayPause()
                            }
                        },
                    )
                }
                .pointerInput(isLocked, seekGesture) {
                    if (!seekGesture || isLocked) return@pointerInput
                    var startingPosition = currentPositionSec.toInt()
                    var startingX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            startingPosition = currentPositionSec.toInt()
                            startingX = it.x
                            onSeekStart()
                        },
                        onDragEnd = {
                            seekPreviewText = null
                            onSeekEnd()
                        },
                    ) { change, _ ->
                        val newPos = calculateNewHorizontalGestureValue(
                            startingPosition,
                            startingX,
                            change.position.x,
                            0.15f,
                        ).coerceIn(0, durationSec.toInt())
                        val delta = newPos - startingPosition
                        val sign = if (delta >= 0) "+" else ""
                        seekPreviewText = "$sign${delta}s"
                        onSeekTo(newPos)
                    }
                }
                .pointerInput(isLocked, gestureVolumeBrightness, subtitleActive) {
                    if (!gestureVolumeBrightness || isLocked) return@pointerInput
                    var startingY: Float? = null
                    var isBrightnessSide = true
                    detectVerticalDragGestures(
                        onDragEnd = { startingY = null },
                        onDragStart = {
                            if (subtitleActive && it.y > size.height * 0.8f) return@detectVerticalDragGestures
                            startingY = it.y
                            isBrightnessSide = if (swapVolumeBrightness) {
                                it.x > size.width / 2
                            } else {
                                it.x < size.width / 2
                            }
                        },
                    ) { change, _ ->
                        val sy = startingY ?: return@detectVerticalDragGestures
                        val totalDisplacement = sy - change.position.y
                        val normalizedDelta = totalDisplacement / size.height
                        if (isBrightnessSide) {
                            onBrightnessChange(normalizedDelta)
                        } else {
                            onVolumeChange(normalizedDelta)
                        }
                        startingY = change.position.y
                    }
                },
        )

        // Double-tap seek overlay
        AnimatedVisibility(
            visible = isDoubleTapSeeking && seekAmount != 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isSeekingForward) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.4f)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DoubleTapSeekTriangles(isForward = isSeekingForward)
                        Text(
                            text = "${seekAmount}s",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        // Horizontal seek preview text
        if (seekPreviewText != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = seekPreviewText!!,
                    fontSize = 28.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun calculateNewHorizontalGestureValue(
    originalValue: Int,
    startingX: Float,
    newX: Float,
    sensitivity: Float,
): Int {
    return originalValue + ((newX - startingX) * sensitivity).toInt()
}
