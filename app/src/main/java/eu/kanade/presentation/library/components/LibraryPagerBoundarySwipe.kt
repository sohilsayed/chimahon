package eu.kanade.presentation.library.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class LibraryPagerBoundary {
    Start,
    End,
}

fun Modifier.libraryPagerBoundarySwipe(
    state: PagerState,
    enabled: Boolean,
    onBoundarySwipe: (LibraryPagerBoundary) -> Unit,
): Modifier = composed {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val currentCallback = rememberUpdatedState(onBoundarySwipe)

    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var triggered by remember { androidx.compose.runtime.mutableStateOf(false) }

    val connection = remember(state, enabled, threshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!enabled || triggered || source != NestedScrollSource.UserInput) return Offset.Zero

                val boundary = when {
                    !state.canScrollBackward && available.x > 0f -> LibraryPagerBoundary.Start
                    !state.canScrollForward && available.x < 0f -> LibraryPagerBoundary.End
                    else -> null
                }
                if (boundary == null) {
                    accumulatedDrag = 0f
                    return Offset.Zero
                }

                if (accumulatedDrag != 0f && accumulatedDrag * available.x < 0f) {
                    accumulatedDrag = 0f
                }
                accumulatedDrag += available.x
                if (abs(accumulatedDrag) >= threshold) {
                    triggered = true
                    currentCallback.value(boundary)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                accumulatedDrag = 0f
                triggered = false
                return Velocity.Zero
            }
        }
    }

    nestedScroll(connection)
}

fun Modifier.libraryModeBoundarySwipe(
    enabled: Boolean,
    onBoundarySwipe: (LibraryPagerBoundary) -> Unit,
): Modifier = composed {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val currentCallback = rememberUpdatedState(onBoundarySwipe)

    pointerInput(enabled, threshold) {
        var distance = 0f
        var triggered = false
        detectHorizontalDragGestures(
            onHorizontalDrag = { change, dragAmount ->
                if (!enabled || triggered) return@detectHorizontalDragGestures
                distance += dragAmount
                if (abs(distance) >= threshold) {
                    triggered = true
                    currentCallback.value(
                        if (distance > 0f) LibraryPagerBoundary.Start else LibraryPagerBoundary.End,
                    )
                    change.consume()
                }
            },
            onDragEnd = {
                distance = 0f
                triggered = false
            },
            onDragCancel = {
                distance = 0f
                triggered = false
            },
        )
    }
}
