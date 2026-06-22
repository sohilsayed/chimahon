package tachiyomi.domain.history.model

import tachiyomi.domain.entries.anime.model.AnimeCover
import java.util.Date

data class AnimeHistoryWithRelations(
    val id: Long,
    val episodeId: Long,
    val animeId: Long,
    val title: String,
    val episodeNumber: Double,
    val seen: Boolean,
    val lastSecondSeen: Long,
    val totalSeconds: Long,
    val watchedAt: Date?,
    val watchDuration: Long,
    val coverData: AnimeCover,
)
