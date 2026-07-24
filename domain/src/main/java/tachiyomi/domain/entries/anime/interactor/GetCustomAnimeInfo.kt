package tachiyomi.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.repository.CustomAnimeRepository

class GetCustomAnimeInfo(
    private val customAnimeRepository: CustomAnimeRepository,
) {

    fun get(mangaId: Long) = customAnimeRepository.get(mangaId)
}
