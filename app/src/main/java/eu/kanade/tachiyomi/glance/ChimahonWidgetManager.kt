package eu.kanade.tachiyomi.glance

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import chimahon.widget.ImmersionWidgetSignals
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.history.interactor.GetHistory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicBoolean

object ChimahonWidgetManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    private val requestChannel = Channel<Set<WidgetTarget>>(Channel.UNLIMITED)

    private enum class WidgetTarget {
        CONTINUE,
        HISTORY,
        STATS,
        NOVEL,
        SEARCH,
        SYSTEM_LOOKUP,
        ;

        fun createWidget(): GlanceAppWidget = when (this) {
            CONTINUE -> ContinueReadingWidget()
            HISTORY -> RecentHistoryWidget()
            STATS -> ReadingStatsWidget()
            NOVEL -> NovelProgressWidget()
            SEARCH -> SearchWidget()
            SYSTEM_LOOKUP -> ScreenOcrWidget()
        }

        companion object {
            val All = entries.toSet()
            val Data = setOf(CONTINUE, HISTORY, STATS, NOVEL)
            val HistoryRelated = setOf(CONTINUE, HISTORY)
        }
    }

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext

        scope.launch {
            while (true) {
                val first = requestChannel.receive()
                val merged = first.toMutableSet()
                val deadline = System.currentTimeMillis() + DEBOUNCE_MS
                while (true) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0L) break
                    val next = withTimeoutOrNull(remaining) { requestChannel.receive() } ?: break
                    merged += next
                }
                while (true) {
                    val next = requestChannel.tryReceive().getOrNull() ?: break
                    merged += next
                }
                updateTargets(app, merged)
            }
        }

        runCatching {
            Injekt.get<GetHistory>()
                .subscribe(
                    query = "",
                    unfinishedManga = null,
                    unfinishedChapter = null,
                    nonLibraryEntries = null,
                )
                .map { list ->
                    list.take(HISTORY_SIGNATURE_LIMIT).map { item ->
                        HistorySignature(
                            chapterId = item.chapterId,
                            mangaId = item.mangaId,
                            chapterNumber = item.chapterNumber,
                            readAtMillis = item.readAt?.time,
                        )
                    }
                }
                .distinctUntilChanged()
                .onEach { enqueue(WidgetTarget.HistoryRelated) }
                .flowOn(Dispatchers.Default)
                .launchIn(scope)
        }.onFailure { e ->
            logcat(LogPriority.ERROR, e) { "Failed to subscribe history for widgets" }
        }

        runCatching {
            Injekt.get<SecurityPreferences>()
                .useAuthenticator()
                .changes()
                .distinctUntilChanged()
                .onEach { enqueue(WidgetTarget.All) }
                .launchIn(scope)
        }.onFailure { e ->
            logcat(LogPriority.ERROR, e) { "Failed to subscribe app-lock for widgets" }
        }

        ImmersionWidgetSignals.statsChanged
            .debounce(STATS_DEBOUNCE_MS)
            .onEach { enqueue(setOf(WidgetTarget.STATS)) }
            .launchIn(scope)

        ImmersionWidgetSignals.novelsChanged
            .onEach { enqueue(setOf(WidgetTarget.NOVEL)) }
            .launchIn(scope)
    }

    fun updateAllWidgets(context: Context) {
        start(context)
        enqueue(WidgetTarget.Data)
    }

    private fun enqueue(targets: Set<WidgetTarget>) {
        if (targets.isEmpty()) return
        requestChannel.trySend(targets)
    }

    private suspend fun updateTargets(context: Context, targets: Set<WidgetTarget>) {
        for (target in targets) {
            try {
                target.createWidget().updateAll(context)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to update ${target.name} widget" }
            }
        }
    }

    private data class HistorySignature(
        val chapterId: Long,
        val mangaId: Long,
        val chapterNumber: Double,
        val readAtMillis: Long?,
    )

    private const val DEBOUNCE_MS = 1_000L
    private const val STATS_DEBOUNCE_MS = 20_000L
    private const val HISTORY_SIGNATURE_LIMIT = 12
}
