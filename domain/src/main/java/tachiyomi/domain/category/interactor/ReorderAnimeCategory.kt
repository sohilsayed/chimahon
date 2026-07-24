package tachiyomi.domain.category.interactor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.category.model.AnimeCategoryUpdate
import tachiyomi.domain.category.repository.AnimeCategoryRepository
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.core.common.util.system.logcat

class ReorderAnimeCategory(
    private val animeCategoryRepository: AnimeCategoryRepository,
) {
    private val mutex = Mutex()

    suspend fun await(category: AnimeCategory, newIndex: Int) = withNonCancellableContext {
        mutex.withLock {
            val categories = animeCategoryRepository.getAll()
                .filterNot(AnimeCategory::isSystemCategory)
                .toMutableList()

            val currentIndex = categories.indexOfFirst { it.id == category.id }
            if (currentIndex == -1) {
                return@withNonCancellableContext Result.Unchanged
            }

            try {
                categories.add(newIndex, categories.removeAt(currentIndex))

                val updates = categories.mapIndexed { index, cat ->
                    AnimeCategoryUpdate(
                        id = cat.id,
                        order = index.toLong(),
                    )
                }

                animeCategoryRepository.update(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
