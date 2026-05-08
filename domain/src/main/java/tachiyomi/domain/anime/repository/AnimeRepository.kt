package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate

interface AnimeRepository {

    suspend fun getAnimeById(id: Long): Anime

    suspend fun getAnimeByIdAsFlow(id: Long): Flow<Anime>

    suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime?

    fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?>

    suspend fun getFavorites(): List<Anime>

    fun getFavoritesBySourceId(sourceId: Long): Flow<List<Anime>>

    suspend fun update(update: AnimeUpdate): Boolean

    suspend fun updateAll(animeUpdates: List<AnimeUpdate>): Boolean

    suspend fun insert(anime: Anime): Long

    suspend fun deleteAnime(animeId: Long)

    suspend fun getAll(): List<Anime>
}
