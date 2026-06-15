package tachiyomi.data.history

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.history.model.AnimeHistory
import tachiyomi.domain.history.model.AnimeHistoryUpdate
import tachiyomi.domain.history.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.repository.AnimeHistoryRepository

class AnimeHistoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : AnimeHistoryRepository {

    override fun getAnimeHistory(query: String): Flow<List<AnimeHistoryWithRelations>> {
        return handler.subscribeToList {
            animeHistoryViewQueries.animeHistory(query, AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getLastAnimeHistory(): AnimeHistoryWithRelations? {
        return handler.awaitOneOrNull {
            animeHistoryViewQueries.getLatestAnimeHistory(AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getTotalWatchDuration(): Long {
        return handler.awaitOne { anime_historyQueries.getWatchDuration() }
    }

    override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> {
        return handler.awaitList {
            anime_historyQueries.getHistoryByAnimeId(animeId, AnimeHistoryMapper::mapAnimeHistory)
        }
    }

    override suspend fun resetHistoryByAnimeIds(animeIds: List<Long>) {
        try {
            handler.await { anime_historyQueries.resetHistoryByAnimeIds(animeIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllHistory(): Boolean {
        return try {
            handler.await { anime_historyQueries.removeAllHistory() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate) {
        try {
            handler.await {
                anime_historyQueries.upsert(
                    historyUpdate.episodeId,
                    historyUpdate.watchedAt,
                    historyUpdate.sessionWatchDuration,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }
}
