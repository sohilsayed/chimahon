package tachiyomi.domain.episode.interactor

import tachiyomi.domain.episode.repository.EpisodeRepository

class DeleteEpisodes(
    private val episodeRepository: EpisodeRepository,
) {

    suspend fun await(chapters: List<Long>) {
        episodeRepository.removeEpisodesWithIds(chapters)
    }
}
