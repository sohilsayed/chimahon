package eu.kanade.domain.episode.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler

class GetExcludedAnimeScanlators(
    private val handler: AnimeDatabaseHandler,
) {

    suspend fun await(animeId: Long): Set<String> {
        return handler.awaitList {
            excluded_anime_scanlatorsQueries.getExcludedScanlatorsByAnimeId(animeId)
        }
            .toSet()
    }

    fun subscribe(animeId: Long): Flow<Set<String>> {
        return handler.subscribeToList {
            excluded_anime_scanlatorsQueries.getExcludedScanlatorsByAnimeId(animeId)
        }
            .map { it.toSet() }
    }
}
