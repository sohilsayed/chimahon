package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.category.model.AnimeCategoryUpdate
import tachiyomi.domain.category.repository.AnimeCategoryRepository
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat

class HideAnimeCategory(
    private val animeCategoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(category: AnimeCategory) = withNonCancellableContext {
        val update = AnimeCategoryUpdate(
            id = category.id,
            hidden = !category.hidden,
        )

        try {
            animeCategoryRepository.update(update)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed class Result {
        data object Success : Result()
        data class InternalError(val error: Throwable) : Result()
    }
}
