package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.history.model.AnimeHistory
import java.util.Date

@Serializable
data class BackupAnimeHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastSeen: Long,
    @ProtoNumber(3) var seenDuration: Long = 0,
) {
    fun getHistoryImpl(): AnimeHistory {
        return AnimeHistory(
            id = -1,
            episodeId = -1,
            watchedAt = Date(lastSeen),
            watchDuration = seenDuration,
        )
    }
}
