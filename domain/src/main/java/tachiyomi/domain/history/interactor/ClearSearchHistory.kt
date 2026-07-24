package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.repository.SearchHistoryRepository

class ClearSearchHistory(
    private val repository: SearchHistoryRepository,
) {
    suspend fun await() = repository.clearAll()
}
