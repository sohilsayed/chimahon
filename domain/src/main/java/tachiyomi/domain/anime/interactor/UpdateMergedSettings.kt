package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.MergeAnimeSettingsUpdate
import tachiyomi.domain.anime.repository.AnimeMergeRepository

class UpdateMergedSettings(
    private val animeMergeRepository: AnimeMergeRepository,
) {

    suspend fun await(mergeUpdate: MergeAnimeSettingsUpdate): Boolean {
        return animeMergeRepository.updateSettings(mergeUpdate)
    }

    suspend fun awaitAll(values: List<MergeAnimeSettingsUpdate>): Boolean {
        return animeMergeRepository.updateAllSettings(values)
    }
}
