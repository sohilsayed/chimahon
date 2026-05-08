package tachiyomi.data.anime

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import tachiyomi.domain.anime.model.Anime

object AnimeMapper {
    fun mapAnime(
        id: Long,
        source: Long,
        url: String,
        artist: String?,
        author: String?,
        description: String?,
        genre: List<String>?,
        title: String,
        status: Long,
        thumbnailUrl: String?,
        favorite: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        initialized: Boolean,
        viewerFlags: Long,
        episodeFlags: Long,
        coverLastModified: Long,
        dateAdded: Long,
        updateStrategy: AnimeUpdateStrategy,
        calculateInterval: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        @Suppress("UNUSED_PARAMETER")
        isSyncing: Long,
    ): Anime = Anime(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate ?: 0,
        nextUpdate = nextUpdate ?: 0,
        fetchInterval = calculateInterval.toInt(),
        dateAdded = dateAdded,
        viewerFlags = viewerFlags,
        episodeFlags = episodeFlags,
        coverLastModified = coverLastModified,
        url = url,
        title = title,
        artist = artist,
        author = author,
        thumbnailUrl = thumbnailUrl,
        description = description,
        genre = genre,
        status = status,
        updateStrategy = updateStrategy,
        initialized = initialized,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
    )
}
