package eu.kanade.tachiyomi.ui.anime.library

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadCache
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.animesource.service.AnimeSourceManager
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.SetSeenStatus
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.AnimeLibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.Collator
import java.util.Locale

class AnimeLibraryScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val preferences: AnimeLibraryPreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadCache: AnimeDownloadCache = Injekt.get(),
) : StateScreenModel<AnimeLibraryScreenModel.State>(State()) {

    private val searchQueryFlow = MutableStateFlow<String?>(null)

    init {
        screenModelScope.launchIO {
            combine(
                getLibraryAnime.subscribe(),
                getAnimeCategories.subscribe(),
                searchQueryFlow.debounce(SEARCH_DEBOUNCE_MILLIS).distinctUntilChanged(),
                preferences.sortingMode().changes(),
                preferences.filterUnseen().changes(),
                preferences.filterStarted().changes(),
                preferences.filterBookmarked().changes(),
                preferences.filterCompleted().changes(),
                preferences.filterDownloaded().changes(),
                preferences.filterFillermarked().changes(),
                preferences.groupLibraryBy().changes(),
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val libraryAnime = values[0] as List<LibraryAnime>
                @Suppress("UNCHECKED_CAST")
                val categories = values[1] as List<AnimeCategory>
                val searchQuery = values[2] as String?
                val sortMode = values[3] as LibrarySort
                val filterUnseen = values[4] as TriState
                val filterStarted = values[5] as TriState
                val filterBookmarked = values[6] as TriState
                val filterCompleted = values[7] as TriState
                val filterDownloaded = values[8] as TriState
                val filterFillermarked = values[9] as TriState
                val groupType = values[10] as Int

                val items = libraryAnime.map { anime ->
                    AnimeLibraryItem(
                        libraryAnime = anime,
                        downloadCount = downloadCache.getDownloadCount(anime.anime).toLong(),
                        unseenCount = anime.unseenCount,
                        isLocal = anime.anime.source == LOCAL_SOURCE_ID,
                        sourceLanguage = sourceManager.getOrStub(anime.anime.source).lang,
                    )
                }

                val filtered = applyFilters(
                    items,
                    searchQuery,
                    filterUnseen,
                    filterStarted,
                    filterBookmarked,
                    filterCompleted,
                    filterDownloaded,
                    filterFillermarked,
                )

                val grouped = applyGrouping(filtered, categories, groupType)
                val collator = Collator.getInstance(Locale.getDefault())
                val sorted = grouped.mapValues { (_, list) ->
                    applySort(list, sortMode, collator)
                }

                val hasActiveFilters = filterUnseen != TriState.DISABLED ||
                    filterStarted != TriState.DISABLED ||
                    filterBookmarked != TriState.DISABLED ||
                    filterCompleted != TriState.DISABLED ||
                    filterDownloaded != TriState.DISABLED ||
                    filterFillermarked != TriState.DISABLED

                State(
                    isLoading = false,
                    library = sorted,
                    categories = categories,
                    searchQuery = searchQuery,
                    hasActiveFilters = hasActiveFilters,
                )
            }.collectLatest { newState ->
                mutableState.update { oldState ->
                    newState.copy(selection = oldState.selection)
                }
            }
        }

        preferences.categoryTabs().changes()
            .onEach { showTabs ->
                mutableState.update { it.copy(showCategoryTabs = showTabs) }
            }
            .launchIn(screenModelScope)

        preferences.categoryNumberOfItems().changes()
            .onEach { show ->
                mutableState.update { it.copy(showAnimeCount = show) }
            }
            .launchIn(screenModelScope)

        preferences.showContinueWatchingButton().changes()
            .onEach { show ->
                mutableState.update { it.copy(showContinueWatchingButton = show) }
            }
            .launchIn(screenModelScope)

    }

    fun search(query: String?) {
        searchQueryFlow.value = query
    }

    fun toggleSelection(anime: LibraryAnime) {
        mutableState.update { state ->
            val newSelection = state.selection.mutate { list ->
                if (list.any { it.id == anime.id }) {
                    list.removeAll { it.id == anime.id }
                } else {
                    list.add(anime)
                }
            }
            state.copy(selection = newSelection)
        }
    }

    fun selectAll(categoryIndex: Int) {
        mutableState.update { state ->
            val items = state.library.values.toList().getOrNull(categoryIndex) ?: return@update state
            val newSelection = state.selection.mutate { list ->
                items.forEach { item ->
                    if (list.none { it.id == item.libraryAnime.id }) {
                        list.add(item.libraryAnime)
                    }
                }
            }
            state.copy(selection = newSelection)
        }
    }

    fun invertSelection(categoryIndex: Int) {
        mutableState.update { state ->
            val items = state.library.values.toList().getOrNull(categoryIndex) ?: return@update state
            val newSelection = state.selection.mutate { list ->
                items.forEach { item ->
                    if (list.any { it.id == item.libraryAnime.id }) {
                        list.removeAll { it.id == item.libraryAnime.id }
                    } else {
                        list.add(item.libraryAnime)
                    }
                }
            }
            state.copy(selection = newSelection)
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun markSeenSelection(seen: Boolean) {
        val animes = state.value.selection.toList()
        screenModelScope.launchNonCancellable {
            animes.forEach { anime ->
                setSeenStatus.await(
                    animeId = anime.id,
                    seen = seen,
                )
            }
        }
        clearSelection()
    }

    fun setAnimeCategories(animeIds: List<Long>, categoryIds: List<Long>) {
        screenModelScope.launchNonCancellable {
            animeIds.forEach { animeId ->
                setAnimeCategories.await(animeId, categoryIds)
            }
        }
    }

    fun removeAnime(animeIds: List<Long>) {
        screenModelScope.launchNonCancellable {
            animeIds.forEach { animeId ->
                updateAnime.await(AnimeUpdate(id = animeId, favorite = false))
            }
        }
        clearSelection()
    }

    private fun applyFilters(
        items: List<AnimeLibraryItem>,
        searchQuery: String?,
        filterUnseen: TriState,
        filterStarted: TriState,
        filterBookmarked: TriState,
        filterCompleted: TriState,
        filterDownloaded: TriState,
        filterFillermarked: TriState,
    ): List<AnimeLibraryItem> {
        var result = items

        if (!searchQuery.isNullOrBlank()) {
            result = result.filter { it.matches(searchQuery) }
        }

        result = applyTriStateFilter(result, filterUnseen) { it.libraryAnime.unseenCount > 0 }
        result = applyTriStateFilter(result, filterStarted) { it.libraryAnime.hasStarted }
        result = applyTriStateFilter(result, filterBookmarked) { it.libraryAnime.hasBookmarks }
        result = applyTriStateFilter(result, filterCompleted) { it.libraryAnime.anime.status == SAnime.COMPLETED.toLong() }
        result = applyTriStateFilter(result, filterFillermarked) { it.libraryAnime.hasFillermarks }
        result = applyTriStateFilter(result, filterDownloaded) { it.downloadCount > 0 || it.isLocal }

        return result
    }

    private fun applyTriStateFilter(
        items: List<AnimeLibraryItem>,
        state: TriState,
        predicate: (AnimeLibraryItem) -> Boolean,
    ): List<AnimeLibraryItem> {
        return when (state) {
            TriState.DISABLED -> items
            TriState.ENABLED_IS -> items.filter(predicate)
            TriState.ENABLED_NOT -> items.filterNot(predicate)
        }
    }

    private fun applyGrouping(
        items: List<AnimeLibraryItem>,
        categories: List<AnimeCategory>,
        groupType: Int,
    ): Map<AnimeCategory, List<AnimeLibraryItem>> {
        return when (groupType) {
            LibraryGroup.BY_SOURCE -> {
                items.groupBy { sourceManager.getOrStub(it.libraryAnime.anime.source) }
                    .toSortedMap(compareBy { it.name })
                    .entries.mapIndexed { index, (source, animeList) ->
                        AnimeCategory(
                            id = source.id,
                            name = source.name,
                            order = index.toLong(),
                            flags = 0L,
                            hidden = false,
                        ) to animeList
                    }.toMap()
            }
            LibraryGroup.BY_STATUS -> {
                val statusOrder = listOf(
                    SAnime.ONGOING.toLong(),
                    SAnime.COMPLETED.toLong(),
                    SAnime.LICENSED.toLong(),
                    SAnime.ON_HIATUS.toLong(),
                    SAnime.CANCELLED.toLong(),
                )
                items.groupBy { it.libraryAnime.anime.status }
                    .toSortedMap(compareBy { statusOrder.indexOf(it) })
                    .entries.mapIndexed { index, (status, animeList) ->
                        AnimeCategory(
                            id = status,
                            name = statusToString(status),
                            order = index.toLong(),
                            flags = 0L,
                            hidden = false,
                        ) to animeList
                    }.toMap()
            }
            else -> {
                if (categories.isEmpty()) {
                    val defaultCategory = AnimeCategory(
                        id = AnimeCategory.UNCATEGORIZED_ID,
                        name = "Default",
                        order = 0,
                        flags = 0L,
                        hidden = false,
                    )
                    mapOf(defaultCategory to items)
                } else {
                    val uncategorized = items.filter { item ->
                        item.libraryAnime.categories.isEmpty() ||
                            item.libraryAnime.categories == listOf(AnimeCategory.UNCATEGORIZED_ID)
                    }
                    val categorized = categories.filter { !it.isSystemCategory }.associateWith { category ->
                        items.filter { item -> category.id in item.libraryAnime.categories }
                    }
                    val result = mutableMapOf<AnimeCategory, List<AnimeLibraryItem>>()
                    if (uncategorized.isNotEmpty() || categorized.isEmpty()) {
                        val systemCategory = categories.find { it.isSystemCategory } ?: AnimeCategory(
                            id = AnimeCategory.UNCATEGORIZED_ID,
                            name = "Default",
                            order = -1,
                            flags = 0L,
                            hidden = false,
                        )
                        result[systemCategory] = uncategorized
                    }
                    result.putAll(categorized)
                    result
                }
            }
        }
    }

    private fun applySort(
        items: List<AnimeLibraryItem>,
        sort: LibrarySort,
        collator: Collator,
    ): List<AnimeLibraryItem> {
        val comparator: Comparator<AnimeLibraryItem> = when (sort.type) {
            LibrarySort.Type.Alphabetical -> Comparator { a, b ->
                collator.compare(a.libraryAnime.anime.title, b.libraryAnime.anime.title)
            }
            LibrarySort.Type.LastRead -> compareByDescending { it.libraryAnime.lastSeen }
            LibrarySort.Type.LastUpdate -> compareByDescending { it.libraryAnime.anime.lastUpdate }
            LibrarySort.Type.UnreadCount -> compareByDescending { it.libraryAnime.unseenCount }
            LibrarySort.Type.TotalChapters -> compareByDescending { it.libraryAnime.totalEpisodes }
            LibrarySort.Type.LatestChapter -> compareByDescending { it.libraryAnime.latestUpload }
            LibrarySort.Type.ChapterFetchDate -> compareByDescending { it.libraryAnime.episodeFetchedAt }
            LibrarySort.Type.DateAdded -> compareByDescending { it.libraryAnime.anime.dateAdded }
            LibrarySort.Type.Random -> {
                val seed = preferences.randomSortSeed().get()
                val random = java.util.Random(seed.toLong())
                val shuffled = items.shuffled(random)
                return shuffled
            }
            else -> compareBy { it.libraryAnime.anime.title }
        }

        val sorted = items.sortedWith(comparator)
        return if (sort.direction == LibrarySort.Direction.Descending) sorted.reversed() else sorted
    }

    private fun statusToString(status: Long): String {
        return when (status.toInt()) {
            SAnime.ONGOING -> "Ongoing"
            SAnime.COMPLETED -> "Completed"
            SAnime.LICENSED -> "Licensed"
            SAnime.ON_HIATUS -> "On Hiatus"
            SAnime.CANCELLED -> "Cancelled"
            else -> "Unknown"
        }
    }

    data class State(
        val isLoading: Boolean = true,
        val library: Map<AnimeCategory, List<AnimeLibraryItem>> = emptyMap(),
        val categories: List<AnimeCategory> = emptyList(),
        val searchQuery: String? = null,
        val selection: PersistentList<LibraryAnime> = persistentListOf(),
        val hasActiveFilters: Boolean = false,
        val showCategoryTabs: Boolean = true,
        val showAnimeCount: Boolean = false,
        val showContinueWatchingButton: Boolean = false,
    ) {
        val selectionMode = selection.isNotEmpty()

        val displayCategories: List<AnimeCategory> = library.keys.toList()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MILLIS = 250L
        private const val LOCAL_SOURCE_ID = 0L
    }
}
