package tachiyomi.domain.category.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.category.repository.AnimeCategoryRepository

class GetAnimeCategories(
    private val animeCategoryRepository: AnimeCategoryRepository,
) {

    fun subscribe(): Flow<List<AnimeCategory>> {
        return animeCategoryRepository.getAllAsFlow()
    }

    fun subscribe(animeId: Long): Flow<List<AnimeCategory>> {
        return animeCategoryRepository.getCategoriesByAnimeIdAsFlow(animeId)
    }

    suspend fun await(): List<AnimeCategory> {
        return animeCategoryRepository.getAll()
    }

    suspend fun await(animeId: Long): List<AnimeCategory> {
        return animeCategoryRepository.getCategoriesByAnimeId(animeId)
    }
}
