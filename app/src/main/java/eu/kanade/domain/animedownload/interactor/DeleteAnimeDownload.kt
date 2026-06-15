package eu.kanade.domain.animedownload.interactor

import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.animesource.service.AnimeSourceManager
import tachiyomi.domain.episode.model.Episode

class DeleteAnimeDownload(
    private val animeSourceManager: AnimeSourceManager,
    private val animeDownloadManager: AnimeDownloadManager,
) {

    suspend fun awaitAll(anime: Anime, vararg episodes: Episode) = withNonCancellableContext {
        animeSourceManager.get(anime.source)?.let { source ->
            animeDownloadManager.deleteEpisodes(episodes.toList(), anime, source)
        }
    }
}
