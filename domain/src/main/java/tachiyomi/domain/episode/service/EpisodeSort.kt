package tachiyomi.domain.episode.service

import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.episode.model.Episode

fun getEpisodeSort(
    manga: Anime,
    sortDescending: Boolean = manga.sortDescending(),
): (
    Episode,
    Episode,
) -> Int {
    return when (manga.sorting) {
        Anime.EPISODE_SORTING_SOURCE -> when (sortDescending) {
            true -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
            false -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
        }
        Anime.EPISODE_SORTING_NUMBER -> when (sortDescending) {
            true -> { c2, c1 -> c2.episodeNumber.compareTo(c1.episodeNumber) }
            false -> { c1, c2 -> c1.episodeNumber.compareTo(c2.episodeNumber) }
        }
        Anime.EPISODE_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c2, c1 -> c2.dateUpload.compareTo(c1.dateUpload) }
            false -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
        }
        Anime.EPISODE_SORTING_ALPHABET -> when (sortDescending) {
            true -> { c2, c1 -> c2.name.compareToWithCollator(c1.name) }
            false -> { c1, c2 -> c1.name.compareToWithCollator(c2.name) }
        }
        else -> throw NotImplementedError("Invalid episode sorting method: ${manga.sorting}")
    }
}
