package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository

class GetAnimeBySource(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(sourceId: Long): List<Anime> {
        return animeRepository.getAnimeBySourceId(sourceId)
    }
}
