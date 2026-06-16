package tachiyomi.domain.anime.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeMergeRepository

class GetMergedAnimeById(
    private val animeMergeRepository: AnimeMergeRepository,
) {

    suspend fun await(id: Long): List<Anime> {
        return try {
            animeMergeRepository.getMergedAnimeById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(id: Long): Flow<List<Anime>> {
        return animeMergeRepository.subscribeMergedAnimeById(id)
    }
}
