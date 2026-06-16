package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeMergeRepository

class GetMergedAnimeForDownloading(
    private val animeMergeRepository: AnimeMergeRepository,
) {

    suspend fun await(mergeId: Long): List<Anime> {
        return animeMergeRepository.getMergeAnimeForDownloading(mergeId)
    }
}
