package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.repository.AnimeHistoryRepository

class GetTotalWatchDuration(
    private val repository: AnimeHistoryRepository,
) {

    suspend fun await(): Long {
        return repository.getTotalWatchDuration()
    }
}
