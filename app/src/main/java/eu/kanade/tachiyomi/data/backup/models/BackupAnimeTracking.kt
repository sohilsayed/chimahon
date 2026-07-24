package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.track.anime.model.AnimeTrack

@Serializable
data class BackupAnimeTracking(
    @ProtoNumber(1) var syncId: Int,
    @ProtoNumber(2) var libraryId: Long,
    @Deprecated("Use mediaId instead", level = DeprecationLevel.WARNING)
    @ProtoNumber(3)
    var mediaIdInt: Int = 0,
    @ProtoNumber(4) var trackingUrl: String = "",
    @ProtoNumber(5) var title: String = "",
    @ProtoNumber(6) var lastEpisodeSeen: Float = 0F,
    @ProtoNumber(7) var totalEpisodes: Int = 0,
    @ProtoNumber(8) var score: Float = 0F,
    @ProtoNumber(9) var status: Int = 0,
    @ProtoNumber(10) var startedWatchingDate: Long = 0,
    @ProtoNumber(11) var finishedWatchingDate: Long = 0,
    @ProtoNumber(12) var private: Boolean = false,
    @ProtoNumber(100) var mediaId: Long = 0,
) {

    @Suppress("DEPRECATION")
    fun getTrackImpl(): AnimeTrack {
        return AnimeTrack(
            id = -1,
            animeId = -1,
            trackerId = syncId.toLong(),
            remoteId = if (mediaIdInt != 0) mediaIdInt.toLong() else mediaId,
            libraryId = libraryId,
            title = title,
            lastEpisodeSeen = lastEpisodeSeen.toDouble(),
            totalEpisodes = totalEpisodes.toLong(),
            score = score.toDouble(),
            status = status.toLong(),
            startDate = startedWatchingDate,
            finishDate = finishedWatchingDate,
            remoteUrl = trackingUrl,
            private = private,
        )
    }
}

val backupAnimeTrackMapper = {
        _: Long,
        _: Long,
        syncId: Long,
        mediaId: Long,
        libraryId: Long?,
        title: String,
        lastEpisodeSeen: Double,
        totalEpisodes: Long,
        status: Long,
        score: Double,
        remoteUrl: String,
        startDate: Long,
        finishDate: Long,
        private: Boolean,
    ->
    BackupAnimeTracking(
        syncId = syncId.toInt(),
        mediaId = mediaId,
        libraryId = libraryId ?: 0,
        title = title,
        lastEpisodeSeen = lastEpisodeSeen.toFloat(),
        totalEpisodes = totalEpisodes.toInt(),
        score = score.toFloat(),
        status = status.toInt(),
        startedWatchingDate = startDate,
        finishedWatchingDate = finishDate,
        trackingUrl = remoteUrl,
        private = private,
    )
}
