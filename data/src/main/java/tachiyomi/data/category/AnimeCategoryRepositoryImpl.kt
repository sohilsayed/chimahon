package tachiyomi.data.category

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.category.model.AnimeCategoryUpdate
import tachiyomi.domain.category.repository.AnimeCategoryRepository
import tachiyomi.mi.data.AnimeDatabase

class AnimeCategoryRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeCategoryRepository {

    override suspend fun getAll(): List<AnimeCategory> {
        return handler.awaitList {
            categoriesQueries.getCategories(AnimeCategoryMapper::mapAnimeCategory)
        }
    }

    override fun getAllAsFlow(): Flow<List<AnimeCategory>> {
        return handler.subscribeToList {
            categoriesQueries.getCategories(AnimeCategoryMapper::mapAnimeCategory)
        }
    }

    override suspend fun getCategoriesByAnimeId(animeId: Long): List<AnimeCategory> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByAnimeId(animeId, AnimeCategoryMapper::mapAnimeCategory)
        }
    }

    override fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeCategory>> {
        return handler.subscribeToList {
            categoriesQueries.getCategoriesByAnimeId(animeId, AnimeCategoryMapper::mapAnimeCategory)
        }
    }

    override suspend fun insert(name: String, order: Long, flags: Long) {
        handler.await {
            categoriesQueries.insert(
                name = name,
                order = order,
                flags = flags,
            )
        }
    }

    override suspend fun delete(categoryId: Long) {
        handler.await {
            categoriesQueries.delete(categoryId = categoryId)
        }
    }

    override suspend fun update(update: AnimeCategoryUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun update(updates: List<AnimeCategoryUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    override suspend fun updateAllFlags(flags: Long?) {
        handler.await {
            categoriesQueries.updateAllFlags(flags)
        }
    }

    private fun AnimeDatabase.updatePartialBlocking(update: AnimeCategoryUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = update.hidden?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }
}
