package eu.kanade.tachiyomi.util

import androidx.compose.runtime.Immutable
import tachiyomi.domain.entries.anime.model.Anime
import eu.kanade.tachiyomi.ui.entries.anime.track.TrackItem

@Immutable
class AniChartApi {

    suspend fun loadAiringTime(
        anime: Anime,
        trackItems: List<TrackItem>,
        manualFetch: Boolean,
    ): Pair<Int, Long> {
        return Pair(anime.nextEpisodeToAir, anime.nextEpisodeAiringAt)
    }
}
