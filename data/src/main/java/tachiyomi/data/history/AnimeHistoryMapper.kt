package tachiyomi.data.history

import tachiyomi.domain.anime.model.AnimeCover
import tachiyomi.domain.history.model.AnimeHistory
import tachiyomi.domain.history.model.AnimeHistoryWithRelations
import java.util.Date

object AnimeHistoryMapper {
    fun mapAnimeHistory(
        id: Long,
        episodeId: Long,
        watchedAt: Date?,
        watchDuration: Long,
    ): AnimeHistory = AnimeHistory(
        id = id,
        episodeId = episodeId,
        watchedAt = watchedAt,
        watchDuration = watchDuration,
    )

    fun mapAnimeHistoryWithRelations(
        historyId: Long,
        animeId: Long,
        episodeId: Long,
        title: String,
        thumbnailUrl: String?,
        sourceId: Long,
        isFavorite: Boolean,
        coverLastModified: Long,
        episodeNumber: Double,
        seen: Boolean,
        lastSecondSeen: Long,
        totalSeconds: Long,
        watchedAt: Date?,
        watchDuration: Long,
    ): AnimeHistoryWithRelations = AnimeHistoryWithRelations(
        id = historyId,
        episodeId = episodeId,
        animeId = animeId,
        title = title,
        episodeNumber = episodeNumber,
        seen = seen,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        watchedAt = watchedAt,
        watchDuration = watchDuration,
        coverData = AnimeCover(
            animeId = animeId,
            sourceId = sourceId,
            isAnimeFavorite = isFavorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
