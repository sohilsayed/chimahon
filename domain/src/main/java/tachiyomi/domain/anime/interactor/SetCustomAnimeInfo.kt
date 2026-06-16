package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.CustomAnimeInfo
import tachiyomi.domain.anime.repository.CustomAnimeRepository

class SetCustomAnimeInfo(
    private val customAnimeRepository: CustomAnimeRepository,
) {

    fun set(mangaInfo: CustomAnimeInfo) = customAnimeRepository.set(mangaInfo)
}
