package tachiyomi.domain.history.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.SearchHistory

interface SearchHistoryRepository {
    fun subscribeByScope(scope: String, limit: Long = SearchHistory.MAX_ITEMS_PER_SCOPE): Flow<List<SearchHistory>>
    suspend fun getByScope(scope: String, limit: Long = SearchHistory.MAX_ITEMS_PER_SCOPE): List<SearchHistory>
    suspend fun getAll(): List<SearchHistory>
    suspend fun upsert(scope: String, query: String, timestamp: Long)
    suspend fun delete(scope: String, query: String)
    suspend fun clearByScope(scope: String)
    suspend fun clearAll()
}
