package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository

class GetAllAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<Anime> {
        return animeRepository.getAll()
    }
}
