package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.entries.anime.model.Anime

@Suppress("DEPRECATION")
@Serializable
data class BackupAnime(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(16) var episodes: List<BackupEpisode> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupAnimeTracking> = emptyList(),
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var episodeFlags: Int = 0,
    @ProtoNumber(103) var viewerFlags: Int = 0,
    @ProtoNumber(104) var history: List<BackupAnimeHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(108) var excludedScanlators: List<String> = emptyList(),
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(500) var backgroundUrl: String? = null,
    @ProtoNumber(502) var parentId: Long? = null,
    @ProtoNumber(503) var id: Long? = null,
    @ProtoNumber(504) var seasonFlags: Long = 0,
    @ProtoNumber(505) var seasonNumber: Double = -1.0,
    @ProtoNumber(506) var seasonSourceOrder: Long = 0,
    @ProtoNumber(507) var fetchType: FetchType = FetchType.Episodes,
) {
    fun getAnimeImpl(): Anime {
        return Anime.create().copy(
            url = url,
            ogTitle = title,
            ogArtist = artist,
            ogAuthor = author,
            ogDescription = description,
            ogGenre = genre,
            ogStatus = status.toLong(),
            ogThumbnailUrl = thumbnailUrl,
            backgroundUrl = backgroundUrl,
            favorite = favorite,
            source = source,
            dateAdded = dateAdded,
            viewerFlags = viewerFlags.toLong(),
            episodeFlags = episodeFlags.toLong(),
            updateStrategy = updateStrategy,
            lastModifiedAt = lastModifiedAt,
            favoriteModifiedAt = favoriteModifiedAt,
            version = version,
            fetchType = fetchType,
            parentId = parentId,
            seasonFlags = seasonFlags,
            seasonNumber = seasonNumber,
            seasonSourceOrder = seasonSourceOrder,
        )
    }
}
