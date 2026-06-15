package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.AnimeHistoryUpdate
import tachiyomi.domain.history.repository.AnimeHistoryRepository

class UpsertAnimeHistory(
    private val animeHistoryRepository: AnimeHistoryRepository,
) {

    suspend fun await(historyUpdate: AnimeHistoryUpdate) {
        animeHistoryRepository.upsertHistory(historyUpdate)
    }
}
