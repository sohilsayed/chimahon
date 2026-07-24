package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.model.MergeAnimeSettingsUpdate
import tachiyomi.domain.entries.anime.repository.AnimeMergeRepository

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
