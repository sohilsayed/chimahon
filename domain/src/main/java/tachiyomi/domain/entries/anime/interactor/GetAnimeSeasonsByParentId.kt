package tachiyomi.domain.entries.anime.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.SeasonAnime
import tachiyomi.domain.entries.anime.repository.AnimeRepository

class GetAnimeSeasonsByParentId(
    private val animeRepository: AnimeRepository,
) {
    suspend fun await(animeId: Long): List<SeasonAnime> {
        return try {
            animeRepository.getAnimeSeasonsById(animeId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }
}
