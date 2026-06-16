package tachiyomi.data.episode

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository

class EpisodeRepositoryImpl(
    private val handler: DatabaseHandler,
) : EpisodeRepository {

    override suspend fun addAll(episodes: List<Episode>): List<Episode> {
        return try {
            handler.await(inTransaction = true) {
                episodes.map { episode ->
                    episodesQueries.insert(
                        episode.animeId,
                        episode.url,
                        episode.name,
                        episode.scanlator,
                        episode.seen,
                        episode.bookmark,
                        episode.fillermark,
                        episode.lastSecondSeen,
                        episode.totalSeconds,
                        episode.episodeNumber,
                        episode.sourceOrder,
                        episode.dateFetch,
                        episode.dateUpload,
                        episode.version,
                    )
                    val lastInsertId = episodesQueries.selectLastInsertedRowId().executeAsOne()
                    episode.copy(id = lastInsertId)
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    override suspend fun update(episodeUpdate: EpisodeUpdate) {
        partialUpdate(episodeUpdate)
    }

    override suspend fun updateAll(episodeUpdates: List<EpisodeUpdate>) {
        partialUpdate(*episodeUpdates.toTypedArray())
    }

    private suspend fun partialUpdate(vararg episodeUpdates: EpisodeUpdate) {
        handler.await(inTransaction = true) {
            episodeUpdates.forEach { episodeUpdate ->
                episodesQueries.update(
                    animeId = episodeUpdate.animeId,
                    url = episodeUpdate.url,
                    name = episodeUpdate.name,
                    scanlator = episodeUpdate.scanlator,
                    seen = episodeUpdate.seen,
                    bookmark = episodeUpdate.bookmark,
                    fillermark = episodeUpdate.fillermark,
                    lastSecondSeen = episodeUpdate.lastSecondSeen,
                    totalSeconds = episodeUpdate.totalSeconds,
                    episodeNumber = episodeUpdate.episodeNumber,
                    sourceOrder = episodeUpdate.sourceOrder,
                    dateFetch = episodeUpdate.dateFetch,
                    dateUpload = episodeUpdate.dateUpload,
                    episodeId = episodeUpdate.id,
                    version = episodeUpdate.version,
                    isSyncing = 0,
                )
            }
        }
    }

    override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) {
        try {
            handler.await { episodesQueries.removeEpisodesWithIds(episodeIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override suspend fun getEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean): List<Episode> {
        return handler.awaitList {
            episodesQueries.getEpisodesByAnimeId(animeId, 0L, EpisodeMapper::mapEpisode)
        }
    }

    override suspend fun getScanlatorsByAnimeId(animeId: Long): List<String> {
        return handler.awaitList {
            episodesQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }
        }
    }

    override fun getScanlatorsByAnimeIdAsFlow(animeId: Long): Flow<List<String>> {
        return handler.subscribeToList {
            episodesQueries.getScanlatorsByAnimeId(animeId) { it.orEmpty() }
        }
    }

    override suspend fun getBookmarkedEpisodesByAnimeId(animeId: Long): List<Episode> {
        return handler.awaitList {
            episodesQueries.getBookmarkedEpisodesByAnimeId(
                animeId,
                EpisodeMapper::mapEpisode,
            )
        }
    }

    override suspend fun getFillermarkedEpisodesByAnimeId(animeId: Long): List<Episode> {
        return handler.awaitList { episodesQueries.getFillermarkedEpisodesByAnimeId(animeId, EpisodeMapper::mapEpisode) }
    }

    override suspend fun getEpisodeById(id: Long): Episode? {
        return handler.awaitOneOrNull { episodesQueries.getEpisodeById(id, EpisodeMapper::mapEpisode) }
    }

    override suspend fun getEpisodeByAnimeIdAsFlow(animeId: Long, applyScanlatorFilter: Boolean): Flow<List<Episode>> {
        return handler.subscribeToList {
            episodesQueries.getEpisodesByAnimeId(animeId, 0L, EpisodeMapper::mapEpisode)
        }
    }

    override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): Episode? {
        return handler.awaitOneOrNull {
            episodesQueries.getEpisodeByUrlAndAnimeId(
                url,
                animeId,
                EpisodeMapper::mapEpisode,
            )
        }
    }

    override suspend fun getEpisodeByUrl(url: String): List<Episode> {
        return handler.awaitList { episodesQueries.getEpisodeByUrl(url, EpisodeMapper::mapEpisode) }
    }

    override suspend fun getMergedEpisodeByAnimeId(animeId: Long, applyScanlatorFilter: Boolean): List<Episode> {
        return getEpisodeByAnimeId(animeId, applyScanlatorFilter)
    }

    override suspend fun getMergedEpisodeByAnimeIdAsFlow(
        animeId: Long,
        applyScanlatorFilter: Boolean,
    ): Flow<List<Episode>> {
        return getEpisodeByAnimeIdAsFlow(animeId, applyScanlatorFilter)
    }

    override suspend fun getScanlatorsByMergeId(animeId: Long): List<String> {
        return emptyList()
    }

    override fun getScanlatorsByMergeIdAsFlow(animeId: Long): Flow<List<String>> {
        return kotlinx.coroutines.flow.flowOf(emptyList())
    }
}
