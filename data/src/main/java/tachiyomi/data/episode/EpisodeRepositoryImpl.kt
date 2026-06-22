package tachiyomi.data.episode

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository

class EpisodeRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : EpisodeRepository {

    override suspend fun addAll(episodes: List<Episode>): List<Episode> {
        return try {
            handler.await(inTransaction = true) {
                episodes.map { episode ->
                    episodesQueries.insert(
                        animeId = episode.animeId,
                        url = episode.url,
                        name = episode.name,
                        scanlator = episode.scanlator,
                        seen = episode.seen,
                        bookmark = episode.bookmark,
                        lastSecondSeen = episode.lastSecondSeen,
                        totalSeconds = episode.totalSeconds,
                        episodeNumber = episode.episodeNumber,
                        sourceOrder = episode.sourceOrder,
                        dateFetch = episode.dateFetch,
                        dateUpload = episode.dateUpload,
                        version = episode.version,
                        summary = null,
                        previewUrl = null,
                        fillermark = episode.fillermark,
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
                    summary = null,
                    previewUrl = null,
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
            episodesQueries.getEpisodesByAnimeId(animeId, EpisodeMapper::mapEpisode)
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
            episodesQueries.getEpisodesByAnimeId(animeId, EpisodeMapper::mapEpisode)
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
