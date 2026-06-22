package tachiyomi.data.history

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.history.model.AnimeHistory
import tachiyomi.domain.history.model.AnimeHistoryUpdate
import tachiyomi.domain.history.model.AnimeHistoryWithRelations
import tachiyomi.domain.history.repository.AnimeHistoryRepository

class AnimeHistoryRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeHistoryRepository {

    override fun getAnimeHistory(query: String): Flow<List<AnimeHistoryWithRelations>> {
        return handler.subscribeToList {
            animehistoryViewQueries.animehistory(query, AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getLastAnimeHistory(): AnimeHistoryWithRelations? {
        return handler.awaitOneOrNull {
            animehistoryViewQueries.getLatestAnimeHistory(AnimeHistoryMapper::mapAnimeHistoryWithRelations)
        }
    }

    override suspend fun getTotalWatchDuration(): Long {
        return handler.awaitOne { animehistoryQueries.getWatchDuration() }
    }

    override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> {
        return handler.awaitList {
            animehistoryQueries.getHistoryByAnimeId(animeId, AnimeHistoryMapper::mapAnimeHistory)
        }
    }

    override suspend fun resetHistoryByAnimeIds(animeIds: List<Long>) {
        try {
            handler.await { animehistoryQueries.resetHistoryByAnimeIds(animeIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
        }
    }

    override suspend fun deleteAllHistory(): Boolean {
        return try {
            handler.await { animehistoryQueries.removeAllHistory() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, throwable = e)
            false
        }
    }

    override suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate) {
        try {
            handler.await {
                animehistoryQueries.upsert(
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
