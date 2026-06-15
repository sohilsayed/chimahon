package tachiyomi.data.category

import tachiyomi.domain.category.model.AnimeCategory

object AnimeCategoryMapper {
    fun mapAnimeCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): AnimeCategory {
        return AnimeCategory(
            id = id,
            name = name,
            order = order,
            flags = flags,
            hidden = hidden == 1L,
        )
    }
}
