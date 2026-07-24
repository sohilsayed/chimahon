package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeMergeRepository

class GetMergedAnimeForDownloading(
    private val animeMergeRepository: AnimeMergeRepository,
) {

    suspend fun await(mergeId: Long): List<Anime> {
        return animeMergeRepository.getMergeAnimeForDownloading(mergeId)
    }
}
