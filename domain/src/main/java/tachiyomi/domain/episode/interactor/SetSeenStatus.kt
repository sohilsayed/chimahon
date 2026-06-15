package tachiyomi.domain.episode.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository

class SetSeenStatus(
    private val episodeRepository: EpisodeRepository,
) {

    private val mapper = { episode: Episode, seen: Boolean ->
        EpisodeUpdate(
            seen = seen,
            lastSecondSeen = if (!seen) 0 else null,
            id = episode.id,
        )
    }

    suspend fun await(seen: Boolean, vararg episodes: Episode): Result = withNonCancellableContext {
        val episodesToUpdate = episodes.filter {
            when (seen) {
                true -> !it.seen
                false -> it.seen || it.lastSecondSeen > 0
            }
        }
        if (episodesToUpdate.isEmpty()) {
            return@withNonCancellableContext Result.NoEpisodes
        }

        try {
            episodeRepository.updateAll(
                episodesToUpdate.map { mapper(it, seen) },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return@withNonCancellableContext Result.InternalError(e)
        }

        Result.Success
    }

    suspend fun await(animeId: Long, seen: Boolean): Result = withNonCancellableContext {
        await(
            seen = seen,
            episodes = episodeRepository
                .getEpisodesByAnimeId(animeId)
                .toTypedArray(),
        )
    }

    sealed interface Result {
        data object Success : Result
        data object NoEpisodes : Result
        data class InternalError(val error: Throwable) : Result
    }
}
