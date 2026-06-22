package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.repository.AnimeMergeRepository

class DeleteMergeById(
    private val animeMergeRepository: AnimeMergeRepository,
) {

    suspend fun await(id: Long) {
        return animeMergeRepository.deleteById(id)
    }
}
