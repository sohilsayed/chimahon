package eu.kanade.tachiyomi.data.track.bangumi

import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack

fun Track.toApiStatus() = when (status) {
    Bangumi.PLAN_TO_READ -> 1
    Bangumi.COMPLETED -> 2
    Bangumi.READING -> 3
    Bangumi.ON_HOLD -> 4
    Bangumi.DROPPED -> 5
    else -> 3
}

fun AnimeTrack.toApiStatus() = when (status) {
    Bangumi.PLAN_TO_READ -> 1
    Bangumi.COMPLETED -> 2
    Bangumi.READING -> 3
    Bangumi.ON_HOLD -> 4
    Bangumi.DROPPED -> 5
    else -> 3
}
