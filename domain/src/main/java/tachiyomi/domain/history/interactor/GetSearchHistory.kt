package tachiyomi.domain.history.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.SearchHistory
import tachiyomi.domain.history.repository.SearchHistoryRepository

class GetSearchHistory(
    private val repository: SearchHistoryRepository,
) {
    fun subscribe(scope: String): Flow<List<SearchHistory>> = repository.subscribeByScope(scope)
    suspend fun await(scope: String): List<SearchHistory> = repository.getByScope(scope)
    suspend fun awaitAll(): List<SearchHistory> = repository.getAll()
}
