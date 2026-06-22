package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.library.model.LibraryAnime

class GetSeenAnimeNotInLibraryView(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<LibraryAnime> {
        return animeRepository.getSeenAnimeNotInLibraryView()
    }
}
