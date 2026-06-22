package eu.kanade.tachiyomi.ui.browse.animesource.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.anime.model.titleOrUrl
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.animesource.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseAnimeSourceScreenModel(
    private val sourceId: Long,
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
) : StateScreenModel<BrowseAnimeSourceScreenModel.State>(State()) {

    private val source: AnimeCatalogueSource?
        get() = animeSourceManager.get(sourceId) as? AnimeCatalogueSource

    @Volatile
    private var currentPage = 1
    @Volatile
    private var currentQuery: String? = null
    @Volatile
    private var currentListing = Listing.Feed
    private var currentFilters = AnimeFilterList()
    @Volatile
    private var isLoadingMore = false

    private var browseJob: Job? = null

    fun loadFeed() {
        loadSourceFeed()
    }

    fun loadSourceFeed() {
        browseJob?.cancel()
        browseJob = screenModelScope.launchIO {
            mutableState.value = state.value.copy(isLoading = true, error = false)
            try {
                val source = source ?: run {
                    mutableState.value = State(error = true)
                    return@launchIO
                }
                val filters = source.getFilterList()
                currentFilters = filters
                currentListing = Listing.Feed
                currentPage = 1
                currentQuery = null

                val latest = if (source.supportsLatest) {
                    runCatching { source.getLatestUpdates(1).animes }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val popular = runCatching { source.getPopularAnime(1).animes }.getOrDefault(emptyList())

                mutableState.value = State(
                    listing = Listing.Feed,
                    latestItems = latest,
                    popularItems = popular,
                    filters = filters,
                    supportsLatest = source.supportsLatest,
                    error = latest.isEmpty() && popular.isEmpty(),
                )
            } catch (e: Exception) {
                mutableState.value = State(error = true)
            }
        }
    }

    fun loadPopular() {
        loadListing(
            listing = Listing.Popular,
            filters = source?.getFilterList() ?: AnimeFilterList(),
        )
    }

    fun loadLatest() {
        loadListing(
            listing = Listing.Latest,
            filters = source?.getFilterList() ?: AnimeFilterList(),
        )
    }

    fun search(query: String, filters: AnimeFilterList = state.value.filters) {
        loadListing(Listing.Search, query.trim(), filters)
    }

    private fun loadListing(
        listing: Listing,
        query: String? = null,
        filters: AnimeFilterList = AnimeFilterList(),
    ) {
        browseJob?.cancel()
        browseJob = screenModelScope.launchIO {
            mutableState.value = state.value.copy(
                isLoading = true,
                error = false,
                listing = listing,
                items = emptyList(),
                hasNextPage = false,
            )
            try {
                val source = source ?: run {
                    mutableState.value = State(error = true)
                    return@launchIO
                }
                val result = source.loadPage(listing, 1, query, filters)
                currentPage = 1
                currentQuery = query?.takeIf { it.isNotBlank() }
                currentListing = listing
                currentFilters = filters
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        listing = listing,
                        items = result.animes,
                        hasNextPage = result.hasNextPage,
                        filters = filters,
                        supportsLatest = source.supportsLatest,
                    )
                }
            } catch (e: Exception) {
                mutableState.value = State(error = true)
            }
        }
    }

    fun resetFilters() {
        val source = source ?: return
        setFilters(source.getFilterList())
    }

    fun setFilters(filters: AnimeFilterList) {
        currentFilters = filters
        mutableState.update { it.copy(filters = filters) }
    }

    fun applyFilters() {
        loadListing(Listing.Search, currentQuery.orEmpty(), state.value.filters)
    }

    private suspend fun AnimeCatalogueSource.loadPage(
        listing: Listing,
        page: Int,
        query: String?,
        filters: AnimeFilterList,
    ): AnimesPage {
        return when (listing) {
            Listing.Feed,
            Listing.Popular,
            -> getPopularAnime(page)
            Listing.Latest -> getLatestUpdates(page)
            Listing.Search -> getSearchAnime(page, query.orEmpty(), filters)
        }
    }

    fun loadNextPage() {
        if (isLoadingMore) return
        if (!state.value.hasNextPage) return

        isLoadingMore = true
        screenModelScope.launchIO {
            try {
                val source = source ?: return@launchIO
                val nextPage = currentPage + 1
                val result = source.loadPage(
                    listing = currentListing,
                    page = nextPage,
                    query = currentQuery,
                    filters = currentFilters,
                )
                currentPage = nextPage
                mutableState.update {
                    it.copy(
                        items = it.items + result.animes,
                        hasNextPage = result.hasNextPage,
                    )
                }
            } catch (_: Exception) {
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun addAnimeToDatabase(sAnime: SAnime, onAdded: (Long) -> Unit) {
        screenModelScope.launchIO {
            val existingAnime = getAnime.await(sAnime.url, sourceId)
            if (existingAnime != null) {
                withUIContext { onAdded(existingAnime.id) }
                return@launchIO
            }

            val anime = Anime.create().copy(
                source = sourceId,
                url = sAnime.url,
                ogTitle = sAnime.titleOrUrl(),
                ogArtist = sAnime.artist,
                ogAuthor = sAnime.author,
                ogDescription = sAnime.description,
                ogGenre = sAnime.getGenres(),
                ogThumbnailUrl = sAnime.thumbnail_url,
                ogStatus = sAnime.status.toLong(),
                initialized = sAnime.initialized,
            )
            val id = animeRepository.insert(anime)!!
            withUIContext { onAdded(id) }
        }
    }

    enum class Listing {
        Feed,
        Popular,
        Latest,
        Search,
    }

    @Immutable
    data class State(
        val isLoading: Boolean = false,
        val listing: Listing = Listing.Feed,
        val latestItems: List<SAnime> = emptyList(),
        val popularItems: List<SAnime> = emptyList(),
        val items: List<SAnime> = emptyList(),
        val hasNextPage: Boolean = false,
        val filters: AnimeFilterList = AnimeFilterList(),
        val supportsLatest: Boolean = false,
        val error: Boolean = false,
    ) {
        val hasFilters: Boolean
            get() = filters.isNotEmpty()
    }
}
