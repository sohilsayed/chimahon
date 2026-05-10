package tachiyomi.domain.history.model

import java.util.Date

data class AnimeHistory(
    val id: Long,
    val episodeId: Long,
    val watchedAt: Date?,
    val watchDuration: Long,
)
