package tachiyomi.domain.entries.anime.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeMergeRepository

class GetMergedAnime(
    private val animeMergeRepository: AnimeMergeRepository,
) {

    suspend fun await(): List<Anime> {
        return try {
            animeMergeRepository.getMergedAnime()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(): Flow<List<Anime>> {
        return animeMergeRepository.subscribeMergedAnime()
    }
}
