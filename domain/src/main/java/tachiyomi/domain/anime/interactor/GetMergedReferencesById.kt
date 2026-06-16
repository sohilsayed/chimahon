package tachiyomi.domain.anime.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.MergedAnimeReference
import tachiyomi.domain.anime.repository.AnimeMergeRepository

class GetMergedReferencesById(
    private val animeMergeRepository: AnimeMergeRepository,
) {

    suspend fun await(id: Long): List<MergedAnimeReference> {
        return try {
            animeMergeRepository.getReferencesById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            emptyList()
        }
    }

    suspend fun subscribe(id: Long): Flow<List<MergedAnimeReference>> {
        return animeMergeRepository.subscribeReferencesById(id)
    }
}
