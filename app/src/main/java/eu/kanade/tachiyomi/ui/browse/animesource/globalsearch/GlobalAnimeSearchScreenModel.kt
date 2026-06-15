package eu.kanade.tachiyomi.ui.browse.animesource.globalsearch

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.animesource.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

class GlobalAnimeSearchScreenModel(
    initialQuery: String = "",
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
) : StateScreenModel<GlobalAnimeSearchScreenModel.State>(State(searchQuery = initialQuery)) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var searchJob: Job? = null

    init {
        if (initialQuery.isNotBlank()) {
            search()
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun search() {
        val query = state.value.searchQuery
        if (query.isNullOrBlank()) return

        searchJob?.cancel()

        val sources = animeSourceManager.getCatalogueSources()
        if (sources.isEmpty()) return

        mutableState.update {
            it.copy(
                items = sources.associateWith { AnimeSearchItemResult.Loading as AnimeSearchItemResult }
                    .toPersistentMap(),
            )
        }

        searchJob = screenModelScope.launch {
            sources.map { source ->
                async {
                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchAnime(1, query, AnimeFilterList())
                        }
                        val results = page.animes.distinctBy { it.url }
                        if (isActive) {
                            updateItem(source, AnimeSearchItemResult.Success(results))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, AnimeSearchItemResult.Error(e))
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun updateItem(source: AnimeCatalogueSource, result: AnimeSearchItemResult) {
        mutableState.update { state ->
            val newItems = state.items.toPersistentMap().put(source, result)
            state.copy(items = newItems)
        }
    }

    fun addAnimeToDatabase(sAnime: SAnime, sourceId: Long, onAdded: (Long) -> Unit) {
        screenModelScope.launchIO {
            val existing = getAnime.await(sAnime.url, sourceId)
            if (existing != null) {
                withUIContext { onAdded(existing.id) }
                return@launchIO
            }

            val anime = Anime.create().copy(
                source = sourceId,
                url = sAnime.url,
                title = sAnime.title,
                artist = sAnime.artist,
                author = sAnime.author,
                description = sAnime.description,
                genre = sAnime.getGenres(),
                thumbnailUrl = sAnime.thumbnail_url,
                status = sAnime.status.toLong(),
                initialized = true,
            )
            val id = animeRepository.insert(anime)
            withUIContext { onAdded(id) }
        }
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val items: ImmutableMap<AnimeCatalogueSource, AnimeSearchItemResult> = persistentMapOf(),
    ) {
        val progress: Int = items.count { it.value !is AnimeSearchItemResult.Loading }
        val total: Int = items.size
    }
}

sealed interface AnimeSearchItemResult {
    data object Loading : AnimeSearchItemResult
    data class Error(val throwable: Throwable) : AnimeSearchItemResult
    data class Success(val result: List<SAnime>) : AnimeSearchItemResult {
        val isEmpty: Boolean get() = result.isEmpty()
    }
}
