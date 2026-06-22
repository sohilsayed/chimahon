package tachiyomi.source.local.entries.anime

import eu.kanade.tachiyomi.animesource.model.FetchType
import tachiyomi.source.local.io.ArchiveAnime
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem

actual class LocalAnimeFetchTypeManager(
    private val fileSystem: LocalAnimeSourceFileSystem,
) {
    actual fun find(animeUrl: String): FetchType {
        val files = fileSystem.getFilesInAnimeDirectory(animeUrl)

        return when {
            files.any(ArchiveAnime::isSupported) -> FetchType.Episodes
            files.any { it.isDirectory } -> FetchType.Seasons
            else -> FetchType.Episodes
        }
    }
}
