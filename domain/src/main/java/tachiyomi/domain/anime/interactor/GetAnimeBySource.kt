package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository

class GetAnimeBySource(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(sourceId: Long): List<Anime> {
        return animeRepository.getAnimeBySourceId(sourceId)
    }
}
