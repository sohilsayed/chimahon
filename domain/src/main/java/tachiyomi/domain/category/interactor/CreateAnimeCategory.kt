package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.repository.AnimeCategoryRepository

class CreateAnimeCategory(
    private val animeCategoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(name: String): Result {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.NameEmpty
        }

        val categories = animeCategoryRepository.getAll()
        if (categories.any { it.name == trimmedName }) {
            return Result.NameAlreadyExists
        }

        val nextOrder = categories.maxOfOrNull { it.order }?.plus(1) ?: 0
        animeCategoryRepository.insert(trimmedName, nextOrder, 0)
        return Result.Success
    }

    sealed interface Result {
        data object Success : Result
        data object NameEmpty : Result
        data object NameAlreadyExists : Result
    }
}
