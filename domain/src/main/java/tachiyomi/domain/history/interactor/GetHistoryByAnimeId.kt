package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.AnimeHistory
import tachiyomi.domain.history.repository.AnimeHistoryRepository

class GetHistoryByAnimeId(
    private val repository: AnimeHistoryRepository,
) {

    suspend fun await(animeId: Long): List<AnimeHistory> {
        return repository.getHistoryByAnimeId(animeId)
    }
}
