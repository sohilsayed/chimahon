package tachiyomi.domain.library.model

import tachiyomi.domain.entries.anime.model.Anime

data class LibraryAnime(
    val anime: Anime,
    val categories: List<Long>,
    val totalEpisodes: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    val fillermarkCount: Long,
    val latestUpload: Long,
    val episodeFetchedAt: Long,
    val lastSeen: Long,
) {
    val id: Long = anime.id

    val unseenCount: Long
        get() = totalEpisodes - seenCount

    val hasBookmarks: Boolean
        get() = bookmarkCount > 0

    val hasFillermarks: Boolean
        get() = fillermarkCount > 0

    val hasStarted: Boolean = seenCount > 0
}
