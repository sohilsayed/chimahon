package eu.kanade.domain.episode.interactor

import tachiyomi.data.handlers.anime.AnimeDatabaseHandler

class SetExcludedAnimeScanlators(
    private val handler: AnimeDatabaseHandler,
) {

    suspend fun await(animeId: Long, excludedScanlators: Set<String>) {
        handler.await(inTransaction = true) {
            val currentExcluded = handler.awaitList {
                excluded_anime_scanlatorsQueries.getExcludedScanlatorsByAnimeId(animeId)
            }.toSet()
            val toAdd = excludedScanlators.minus(currentExcluded)
            for (scanlator in toAdd) {
                excluded_anime_scanlatorsQueries.insert(animeId, scanlator)
            }
            val toRemove = currentExcluded.minus(excludedScanlators)
            excluded_anime_scanlatorsQueries.remove(animeId, toRemove)
        }
    }
}
