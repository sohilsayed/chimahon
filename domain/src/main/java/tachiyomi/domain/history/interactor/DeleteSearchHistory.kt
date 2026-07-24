package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.repository.SearchHistoryRepository

class DeleteSearchHistory(
    private val repository: SearchHistoryRepository,
) {
    suspend fun await(scope: String, query: String) = repository.delete(scope, query)
    suspend fun clearScope(scope: String) = repository.clearByScope(scope)
}
