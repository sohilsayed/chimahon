package tachiyomi.domain.track.interactor

import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository

class InsertTrack(
    private val trackRepository: TrackRepository,
) {

    suspend fun await(track: Track) {
        trackRepository.insert(track)
    }

    suspend fun awaitAll(tracks: List<Track>) {
        trackRepository.insertAll(tracks)
    }
}
