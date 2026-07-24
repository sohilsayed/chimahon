package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.repository.SearchHistoryRepository

class UpsertSearchHistory(
    private val repository: SearchHistoryRepository,
) {
    suspend fun await(scope: String, query: String, timestamp: Long = System.currentTimeMillis()) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        repository.upsert(scope, trimmed, timestamp)
    }
}
