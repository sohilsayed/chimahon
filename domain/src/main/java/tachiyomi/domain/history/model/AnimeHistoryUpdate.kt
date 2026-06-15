package tachiyomi.domain.history.model

import java.util.Date

data class AnimeHistoryUpdate(
    val episodeId: Long,
    val watchedAt: Date,
    val sessionWatchDuration: Long,
)
