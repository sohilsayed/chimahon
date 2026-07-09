package chimahon.widget

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Hot SharedFlow bus for file-backed immersion data (non-suspending emit). */
object ImmersionWidgetSignals {

    private val _statsChanged = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _novelsChanged = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val statsChanged: SharedFlow<Unit> = _statsChanged.asSharedFlow()
    val novelsChanged: SharedFlow<Unit> = _novelsChanged.asSharedFlow()

    fun notifyStatsChanged() {
        _statsChanged.tryEmit(Unit)
    }

    fun notifyNovelsChanged() {
        _novelsChanged.tryEmit(Unit)
    }
}
