package tachiyomi.domain.category.interactor

import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.category.model.AnimeCategoryUpdate
import tachiyomi.domain.category.repository.AnimeCategoryRepository
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.plus
import tachiyomi.domain.library.service.AnimeLibraryPreferences
import kotlin.random.Random

class SetSortModeForAnimeCategory(
    private val preferences: AnimeLibraryPreferences,
    private val animeCategoryRepository: AnimeCategoryRepository,
) {

    suspend fun await(categoryId: Long?, type: LibrarySort.Type, direction: LibrarySort.Direction) {
        if (preferences.groupLibraryBy().get() != LibraryGroup.BY_DEFAULT) {
            setGlobalSort(type, direction)
            return
        }

        val category = categoryId?.let { id ->
            animeCategoryRepository.getAll().firstOrNull { it.id == id }
        }
        val flags = (category?.flags ?: 0) + type + direction
        if (type == LibrarySort.Type.Random) {
            preferences.randomSortSeed().set(Random.nextInt())
        }

        if (category != null && preferences.categorizedDisplaySettings().get()) {
            animeCategoryRepository.update(
                AnimeCategoryUpdate(
                    id = category.id,
                    flags = flags,
                ),
            )
        } else {
            preferences.sortingMode().set(LibrarySort(type, direction))
            animeCategoryRepository.updateAllFlags(flags)
        }
    }

    suspend fun await(
        category: AnimeCategory?,
        type: LibrarySort.Type,
        direction: LibrarySort.Direction,
    ) {
        await(category?.id, type, direction)
    }

    private fun setGlobalSort(type: LibrarySort.Type, direction: LibrarySort.Direction) {
        if (type == LibrarySort.Type.Random) {
            preferences.randomSortSeed().set(Random.nextInt())
        }
        preferences.sortingMode().set(LibrarySort(type, direction))
    }
}
