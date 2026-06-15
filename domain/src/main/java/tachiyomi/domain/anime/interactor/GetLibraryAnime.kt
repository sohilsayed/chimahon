package tachiyomi.domain.anime.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.library.model.LibraryAnime

class GetLibraryAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<LibraryAnime> {
        return animeRepository.getLibraryAnime()
    }

    fun subscribe(): Flow<List<LibraryAnime>> {
        return animeRepository.getLibraryAnimeAsFlow()
    }
}
