package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.repository.AnimeCategoryRepository

class DeleteAnimeCategory(
    private val animeCategoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(categoryId: Long) {
        animeCategoryRepository.delete(categoryId)
    }
}
