package eu.kanade.tachiyomi.ui.entries.anime.track

import eu.kanade.tachiyomi.data.track.BaseTracker
import tachiyomi.domain.track.anime.model.AnimeTrack

data class TrackItem(val track: AnimeTrack?, val tracker: BaseTracker)

typealias AnimeTrackItem = TrackItem
