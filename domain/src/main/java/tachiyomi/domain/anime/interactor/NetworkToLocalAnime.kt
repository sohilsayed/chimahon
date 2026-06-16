package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository

class NetworkToLocalAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(manga: Anime): Anime {
        val localManga = getManga(manga.url, manga.source)
        return when {
            localManga == null -> {
                val id = insertManga(manga)
                manga.copy(id = id!!)
            }
            !localManga.favorite -> {
                // if the manga isn't a favorite, set its display title from source
                // if it later becomes a favorite, updated title will go to db
                localManga.copy(/* SY --> */ogTitle/* SY <-- */ = manga.title)
            }
            else -> {
                localManga
            }
        }
    }

    // KMK -->
    suspend fun getLocal(manga: Anime): Anime = if (manga.id <= 0) {
        await(manga)
    } else {
        manga
    }
    // KMK <--

    private suspend fun getManga(url: String, sourceId: Long): Anime? {
        return animeRepository.getAnimeByUrlAndSourceId(url, sourceId)
    }

    private suspend fun insertManga(manga: Anime): Long? {
        return animeRepository.insert(manga)
    }
}
