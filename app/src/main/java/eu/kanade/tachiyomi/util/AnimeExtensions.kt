package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.cache.CoverCache
import tachiyomi.domain.anime.model.Anime
import tachiyomi.source.local.image.LocalCoverManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream
import java.time.Instant

suspend fun Anime.editCover(
    coverManager: LocalCoverManager,
    stream: InputStream,
    updateAnime: tachiyomi.domain.anime.interactor.UpdateAnime = Injekt.get(),
    coverCache: CoverCache = Injekt.get(),
) {
    if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateAnime.await(animeUpdate = tachiyomi.domain.anime.model.AnimeUpdate(
            id = id,
            coverLastModified = Instant.now().toEpochMilli(),
        ))
    }
}

fun buildProgressString(lastSecondSeen: Long?, totalSeconds: Long?): String? {
    if (lastSecondSeen == null || totalSeconds == null || totalSeconds == 0L) return null
    val hours = lastSecondSeen / 3600
    val minutes = (lastSecondSeen % 3600) / 60
    val secs = lastSecondSeen % 60
    val current = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
    val totalHours = totalSeconds / 3600
    val totalMinutes = (totalSeconds % 3600) / 60
    val totalSecs = totalSeconds % 60
    val total = if (totalHours > 0) "%d:%02d:%02d".format(totalHours, totalMinutes, totalSecs) else "%d:%02d".format(totalMinutes, totalSecs)
    return "$current/$total"
}
