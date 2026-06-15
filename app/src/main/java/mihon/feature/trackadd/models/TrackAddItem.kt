package mihon.feature.trackadd.models

import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.flow.MutableStateFlow
import tachiyomi.domain.manga.model.Manga

class TrackAddItem(
    val manga: Manga,
) {
    val searchResult = MutableStateFlow<SearchResult>(SearchResult.Searching)

    sealed interface SearchResult {
        data object Searching : SearchResult
        data class Found(val track: TrackSearch) : SearchResult
        data object NotFound : SearchResult
        data class Failed(val error: String) : SearchResult
    }
}
