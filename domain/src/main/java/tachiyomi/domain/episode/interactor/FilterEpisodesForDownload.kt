package tachiyomi.domain.episode.interactor

import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.episode.model.Episode

class FilterEpisodesForDownload(
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val downloadPreferences: tachiyomi.domain.download.service.DownloadPreferences,
    private val getCategories: tachiyomi.domain.category.interactor.GetAnimeCategories,
) {
    suspend fun await(anime: Anime, newEpisodes: List<Episode>): List<Episode> {
        if (newEpisodes.isEmpty()) return emptyList()
        return newEpisodes
    }
}
