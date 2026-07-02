package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.AnimeCategory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeCategoriesBackupCreator(
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCategory> {
        return getAnimeCategories.await()
            .filterNot(AnimeCategory::isSystemCategory)
            .map {
                BackupCategory(
                    id = it.id,
                    name = it.name,
                    order = it.order,
                    flags = it.flags,
                    hidden = it.hidden,
                )
            }
    }
}
