package tachiyomi.domain.episode.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.repository.EpisodeRepository

class GetEpisodeByUrl(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(url: String): List<Episode> {
        return try {
            episodeRepository.getEpisodeByUrl(url)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
