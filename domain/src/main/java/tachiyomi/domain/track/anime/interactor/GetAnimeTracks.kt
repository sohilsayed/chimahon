package tachiyomi.domain.track.anime.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository

class GetAnimeTracks(
    private val animeTrackRepository: AnimeTrackRepository,
) {

    suspend fun await(animeId: Long): List<AnimeTrack> {
        return animeTrackRepository.getTracksByAnimeId(animeId)
    }

    fun subscribe(): Flow<List<AnimeTrack>> {
        return animeTrackRepository.getAnimeTracksAsFlow()
    }

    fun subscribe(animeId: Long): Flow<List<AnimeTrack>> {
        return animeTrackRepository.getTracksByAnimeIdAsFlow(animeId)
    }
}
