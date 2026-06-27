package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.repository.AnimeCategoryRepository
import tachiyomi.domain.library.service.AnimeLibraryPreferences

class CreateAnimeCategory(
    private val animeCategoryRepository: AnimeCategoryRepository,
    private val preferences: AnimeLibraryPreferences,
) {

    private val initialFlags: Long
        get() {
            val sort = preferences.sortingMode().get()
            return sort.type.flag or sort.direction.flag
        }

    suspend fun await(name: String): Result = withNonCancellableContext {
        val categories = animeCategoryRepository.getAll()
        val nextOrder = categories.maxOfOrNull { it.order }?.plus(1) ?: 0

        try {
            animeCategoryRepository.insert(name, nextOrder, initialFlags)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
