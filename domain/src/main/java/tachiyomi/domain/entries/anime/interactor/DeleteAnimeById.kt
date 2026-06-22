package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.repository.AnimeRepository

class DeleteAnimeById(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(id: Long) {
        return animeRepository.deleteAnime(id)
    }
}
