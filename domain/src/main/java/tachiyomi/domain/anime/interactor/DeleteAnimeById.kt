package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.repository.AnimeRepository

class DeleteAnimeById(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(id: Long) {
        return animeRepository.deleteAnime(id)
    }
}
