package tachiyomi.domain.history.interactor

import tachiyomi.domain.history.repository.AnimeHistoryRepository

class RemoveAnimeHistory(
    private val repository: AnimeHistoryRepository,
) {

    suspend fun awaitAll(): Boolean {
        return repository.deleteAllHistory()
    }

    suspend fun awaitAnime(animeIds: List<Long>) {
        repository.resetHistoryByAnimeIds(animeIds)
    }
}
