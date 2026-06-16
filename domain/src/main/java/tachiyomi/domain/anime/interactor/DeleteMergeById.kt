package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.repository.AnimeMergeRepository

class DeleteMergeById(
    private val animeMergeRepository: AnimeMergeRepository,
) {

    suspend fun await(id: Long) {
        return animeMergeRepository.deleteById(id)
    }
}
