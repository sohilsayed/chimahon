package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.category.model.AnimeCategoryUpdate
import tachiyomi.domain.category.repository.AnimeCategoryRepository
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat

class RenameAnimeCategory(
    private val animeCategoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(categoryId: Long, name: String) = withNonCancellableContext {
        val update = AnimeCategoryUpdate(
            id = categoryId,
            name = name,
        )

        try {
            animeCategoryRepository.update(update)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    suspend fun await(category: AnimeCategory, name: String) = await(category.id, name)

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
