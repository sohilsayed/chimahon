package tachiyomi.domain.history.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.history.model.AnimeHistory
import tachiyomi.domain.history.model.AnimeHistoryUpdate
import tachiyomi.domain.history.model.AnimeHistoryWithRelations

interface AnimeHistoryRepository {

    fun getAnimeHistory(query: String): Flow<List<AnimeHistoryWithRelations>>

    suspend fun getLastAnimeHistory(): AnimeHistoryWithRelations?

    suspend fun getTotalWatchDuration(): Long

    suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory>

    suspend fun resetHistoryByAnimeIds(animeIds: List<Long>)

    suspend fun deleteAllHistory(): Boolean

    suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate)
}
