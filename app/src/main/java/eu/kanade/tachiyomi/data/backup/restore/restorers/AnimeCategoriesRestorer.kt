package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeCategoriesRestorer(
    private val animeHandler: AnimeDatabaseHandler = Injekt.get(),
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        if (backupCategories.isEmpty()) return

        val dbCategories = getAnimeCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }
        var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0

        val categories = backupCategories
            .sortedBy { it.order }
            .map {
                val dbCategory = dbCategoriesByName[it.name]
                if (dbCategory != null) return@map dbCategory

                val order = nextOrder++
                animeHandler.awaitOneExecutable {
                    categoriesQueries.insert(it.name, order, it.flags)
                    categoriesQueries.selectLastInsertedRowId()
                }
                    .also { id ->
                        animeHandler.await {
                            categoriesQueries.update(
                                name = null,
                                order = null,
                                flags = null,
                                hidden = if (it.hidden) 1L else 0L,
                                categoryId = id,
                            )
                        }
                    }
                    .let { id ->
                        AnimeCategory(
                            id = id,
                            name = it.name,
                            order = order,
                            flags = it.flags,
                            hidden = it.hidden,
                        )
                    }
            }

        libraryPreferences.categorizedDisplaySettings().set(
            (dbCategories + categories)
                .distinctBy { it.flags }
                .size > 1,
        )
    }
}
