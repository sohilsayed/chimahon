package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.repository.AnimeMergeRepository

class DeleteByMergeId(
    private val animeMergeRepository: AnimeMergeRepository,
) {

    suspend fun await(id: Long) {
        return animeMergeRepository.deleteByMergeId(id)
    }
}
