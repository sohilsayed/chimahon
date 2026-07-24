package tachiyomi.data.entries.anime

import tachiyomi.domain.entries.anime.model.MergedAnimeReference

object MergedAnimeMapper {
    fun map(
        id: Long,
        infoAnime: Boolean,
        getEpisodeUpdates: Boolean,
        episodeSortMode: Long,
        episodePriority: Long,
        downloadEpisodes: Boolean,
        mergeId: Long,
        mergeUrl: String,
        animeId: Long?,
        animeUrl: String,
        animeSource: Long,
    ): MergedAnimeReference {
        return MergedAnimeReference(
            id = id,
            isInfoAnime = infoAnime,
            getEpisodeUpdates = getEpisodeUpdates,
            episodeSortMode = episodeSortMode.toInt(),
            episodePriority = episodePriority.toInt(),
            downloadEpisodes = downloadEpisodes,
            mergeId = mergeId,
            mergeUrl = mergeUrl,
            animeId = animeId,
            animeUrl = animeUrl,
            animeSourceId = animeSource,
        )
    }
}
