package tachiyomi.data.history

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.history.model.SearchHistory
import tachiyomi.domain.history.repository.SearchHistoryRepository

class SearchHistoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : SearchHistoryRepository {

    override fun subscribeByScope(scope: String, limit: Long): Flow<List<SearchHistory>> {
        return handler.subscribeToList {
            search_historyQueries.selectByScope(
                scope = scope,
                limit = limit,
                mapper = { id, scope, query, lastSearchedAt ->
                    SearchHistory(
                        id = id,
                        scope = scope,
                        query = query,
                        lastSearchedAt = lastSearchedAt,
                    )
                },
            )
        }
    }

    override suspend fun getByScope(scope: String, limit: Long): List<SearchHistory> {
        return handler.awaitList {
            search_historyQueries.selectByScope(
                scope = scope,
                limit = limit,
                mapper = { id, scope, query, lastSearchedAt ->
                    SearchHistory(
                        id = id,
                        scope = scope,
                        query = query,
                        lastSearchedAt = lastSearchedAt,
                    )
                },
            )
        }
    }

    override suspend fun getAll(): List<SearchHistory> {
        return handler.awaitList {
            search_historyQueries.selectAll(
                mapper = { id, scope, query, lastSearchedAt ->
                    SearchHistory(
                        id = id,
                        scope = scope,
                        query = query,
                        lastSearchedAt = lastSearchedAt,
                    )
                },
            )
        }
    }

    override suspend fun upsert(scope: String, query: String, timestamp: Long) {
        handler.await(inTransaction = true) {
            search_historyQueries.upsertQuery(
                scope = scope,
                query = query,
                lastSearchedAt = timestamp,
            )
            search_historyQueries.deleteOldEntries(
                scope = scope,
                limit = SearchHistory.MAX_ITEMS_PER_SCOPE,
            )
        }
    }

    override suspend fun delete(scope: String, query: String) {
        handler.await {
            search_historyQueries.deleteByQuery(
                scope = scope,
                query = query,
            )
        }
    }

    override suspend fun clearByScope(scope: String) {
        handler.await {
            search_historyQueries.clearByScope(
                scope = scope,
            )
        }
    }

    override suspend fun clearAll() {
        handler.await {
            search_historyQueries.clearAll()
        }
    }
}
