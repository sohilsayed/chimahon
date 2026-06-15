package eu.kanade.presentation.more.stats

import androidx.compose.runtime.Immutable
import eu.kanade.presentation.more.stats.data.StatsData
import eu.kanade.presentation.more.stats.data.StatsType

enum class StatsDateScale {
    Day, Week, Month, Year, AllTime
}

sealed interface StatsScreenState {
    @Immutable
    data object Loading : StatsScreenState

    @Immutable
    data class Success(
        val overview: StatsData.Overview,
        val titles: StatsData.Titles,
        val chapters: StatsData.Chapters,
        val trackers: StatsData.Trackers,
        val dateScale: StatsDateScale,
        val dateOffset: Int,
        val statsType: StatsType,
    ) : StatsScreenState
}
