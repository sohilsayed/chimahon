package tachiyomi.domain.category.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.category.model.AnimeCategoryUpdate

interface AnimeCategoryRepository {

    suspend fun getAll(): List<AnimeCategory>

    fun getAllAsFlow(): Flow<List<AnimeCategory>>

    suspend fun getCategoriesByAnimeId(animeId: Long): List<AnimeCategory>

    fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeCategory>>

    suspend fun insert(name: String, order: Long, flags: Long)

    suspend fun delete(categoryId: Long)

    suspend fun update(update: AnimeCategoryUpdate)

    suspend fun update(updates: List<AnimeCategoryUpdate>)

    suspend fun updateAllFlags(flags: Long?)
}
