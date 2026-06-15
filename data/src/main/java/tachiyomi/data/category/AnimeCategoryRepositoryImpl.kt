package tachiyomi.data.category

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.category.model.AnimeCategoryUpdate
import tachiyomi.domain.category.repository.AnimeCategoryRepository

class AnimeCategoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : AnimeCategoryRepository {

    override suspend fun getAll(): List<AnimeCategory> {
        return handler.awaitList {
            anime_categoriesQueries.getCategories(AnimeCategoryMapper::mapAnimeCategory)
        }
    }

    override fun getAllAsFlow(): Flow<List<AnimeCategory>> {
        return handler.subscribeToList {
            anime_categoriesQueries.getCategories(AnimeCategoryMapper::mapAnimeCategory)
        }
    }

    override suspend fun getCategoriesByAnimeId(animeId: Long): List<AnimeCategory> {
        return handler.awaitList {
            anime_categoriesQueries.getCategoriesByAnimeId(animeId, AnimeCategoryMapper::mapAnimeCategory)
        }
    }

    override fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeCategory>> {
        return handler.subscribeToList {
            anime_categoriesQueries.getCategoriesByAnimeId(animeId, AnimeCategoryMapper::mapAnimeCategory)
        }
    }

    override suspend fun insert(name: String, order: Long, flags: Long) {
        handler.await {
            anime_categoriesQueries.insert(
                name = name,
                order = order,
                flags = flags,
                hidden = 0L,
            )
        }
    }

    override suspend fun delete(categoryId: Long) {
        handler.await {
            anime_categoriesQueries.delete(categoryId = categoryId)
        }
    }

    override suspend fun update(update: AnimeCategoryUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updateAllFlags(flags: Long?) {
        handler.await {
            anime_categoriesQueries.updateAllFlags(flags)
        }
    }

    private fun Database.updatePartialBlocking(update: AnimeCategoryUpdate) {
        anime_categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = update.hidden?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }
}
