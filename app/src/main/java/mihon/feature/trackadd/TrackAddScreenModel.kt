package mihon.feature.trackadd

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import logcat.LogPriority
import mihon.feature.trackadd.models.TrackAddItem
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrackAddScreenModel(
    mangaIds: Collection<Long>,
    private val tracker: Tracker,
    private val getTracks: GetTracks = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
) : StateScreenModel<TrackAddScreenModel.State>(State()) {

    private val navigateBackChannel = Channel<Unit>()
    val navigateBackEvent = navigateBackChannel.receiveAsFlow()

    private var trackAllJob: Job? = null

    init {
        screenModelScope.launchIO {
            val existingTracks = getTracks.await(mangaIds.toList())
            val untrackedMangaIds = mangaIds.filter { id ->
                existingTracks[id]?.none { it.trackerId == tracker.id } ?: true
            }
            val items = untrackedMangaIds
                .mapNotNull { getManga.await(it) }
                .map { TrackAddItem(it) }
            mutableState.update { it.copy(items = items.toImmutableList()) }
            if (items.isNotEmpty()) {
                searchTrackers(items)
            } else {
                navigateBack()
            }
        }
    }

    private suspend fun searchTrackers(items: List<TrackAddItem>) {
        for (item in items) {
            if (!currentCoroutineContext().isActive) break
            if (item.manga.id !in state.value.mangaIds) continue
            if (item.searchResult.value != TrackAddItem.SearchResult.Searching) continue

            val result = try {
                withIOContext {
                    val results = tracker.search(item.manga.title.sanitize())
                    results.firstOrNull()
                }
            } catch (e: CancellationException) {
                continue
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to search tracker for ${item.manga.title}" }
                null
            }

            item.searchResult.value = if (result != null) {
                TrackAddItem.SearchResult.Found(result)
            } else {
                TrackAddItem.SearchResult.NotFound
            }

            updateProgress()
        }
    }

    fun cancelItem(mangaId: Long) {
        screenModelScope.launchIO {
            val item = items.find { it.manga.id == mangaId } ?: return@launchIO
            item.searchResult.value = TrackAddItem.SearchResult.NotFound
            updateProgress()
        }
    }

    fun removeItem(mangaId: Long) {
        screenModelScope.launchIO {
            val item = items.find { it.manga.id == mangaId } ?: return@launchIO
            mutableState.update { it.copy(items = it.items.toPersistentList().remove(item)) }
            if (state.value.items.isEmpty()) {
                navigateBack()
            }
            updateProgress()
        }
    }

    fun updateManualResult(mangaId: Long, trackSearch: TrackSearch) {
        val item = items.find { it.manga.id == mangaId } ?: return
        item.searchResult.value = TrackAddItem.SearchResult.Found(trackSearch)
        updateProgress()
    }

    private fun updateProgress() {
        mutableState.update { state ->
            state.copy(
                finishedCount = state.items.count { it.searchResult.value !is TrackAddItem.SearchResult.Searching },
                allComplete = state.items.all { it.searchResult.value !is TrackAddItem.SearchResult.Searching },
            )
        }
    }

    fun trackAll() {
        trackAllJob = screenModelScope.launchIO {
            mutableState.update { it.copy(dialog = Dialog.Progress(0f)) }
            val currentItems = items
            try {
                currentItems.forEachIndexed { index, item ->
                    try {
                        ensureActive()
                        val trackSearch = (item.searchResult.value as? TrackAddItem.SearchResult.Found)?.track
                            ?: return@forEachIndexed
                        addTracks.bind(tracker, trackSearch, item.manga.id)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.WARN, throwable = e)
                    }
                    mutableState.update {
                        it.copy(dialog = Dialog.Progress((index.toFloat() / currentItems.size).coerceAtMost(1f)))
                    }
                }
                navigateBack()
            } finally {
                mutableState.update { it.copy(dialog = null) }
                trackAllJob = null
            }
        }
    }

    fun cancelTrackAll() {
        trackAllJob?.cancel()
        trackAllJob = null
    }

    private suspend fun navigateBack() {
        navigateBackChannel.send(Unit)
    }

    fun showConfirmDialog() {
        mutableState.update { state ->
            state.copy(
                dialog = Dialog.Confirm(
                    totalCount = state.items.size,
                    skippedCount = state.items.count { it.searchResult.value !is TrackAddItem.SearchResult.Found },
                ),
            )
        }
    }

    fun showExitDialog() {
        mutableState.update { it.copy(dialog = Dialog.Exit) }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    private val items
        inline get() = state.value.items

    override fun onDispose() {
        super.onDispose()
        trackAllJob?.cancel()
    }

    sealed interface Dialog {
        data class Confirm(val totalCount: Int, val skippedCount: Int) : Dialog
        data class Progress(val progress: Float) : Dialog
        data object Exit : Dialog
    }

    data class State(
        val items: ImmutableList<TrackAddItem> = persistentListOf(),
        val finishedCount: Int = 0,
        val allComplete: Boolean = false,
        val dialog: Dialog? = null,
    ) {
        val mangaIds: List<Long> = items.map { it.manga.id }
    }
}
