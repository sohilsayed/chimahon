package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.repository.HistoryRepository

class GetAllHistory(
    private val repository: HistoryRepository,
) {

    suspend fun await(): List<History> {
        return repository.getAllHistory()
    }
}
