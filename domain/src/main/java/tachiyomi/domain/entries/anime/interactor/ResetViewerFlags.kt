package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.repository.AnimeRepository

class ResetViewerFlags(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): Boolean {
        return animeRepository.resetViewerFlags()
    }
}
