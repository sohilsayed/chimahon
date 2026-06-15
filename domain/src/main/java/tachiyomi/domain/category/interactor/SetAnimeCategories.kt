package tachiyomi.domain.category.interactor

import tachiyomi.domain.anime.repository.AnimeRepository

class SetAnimeCategories(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(animeId: Long, categoryIds: List<Long>) {
        animeRepository.setAnimeCategories(animeId, categoryIds)
    }
}
