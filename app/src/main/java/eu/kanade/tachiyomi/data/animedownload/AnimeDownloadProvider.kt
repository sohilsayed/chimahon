package eu.kanade.tachiyomi.data.animedownload

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class AnimeDownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    private val downloadsDir: UniFile?
        get() = storageManager.getAnimeDownloadsDirectory()

    internal fun getAnimeDir(animeTitle: String, source: AnimeSource): UniFile {
        val downloadsDir = downloadsDir
        if (downloadsDir == null) {
            logcat(LogPriority.ERROR) { "Failed to create anime download directory" }
            throw IOException(context.stringResource(MR.strings.storage_failed_to_create_download_directory))
        }

        val sourceDirName = getSourceDirName(source)
        val sourceDir = downloadsDir.createDirectory(sourceDirName)
        if (sourceDir == null) {
            val displayablePath = downloadsDir.displayablePath + "/$sourceDirName"
            logcat(LogPriority.ERROR) { "Failed to create source download directory: $displayablePath" }
            throw IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath))
        }

        val animeDirName = getAnimeDirName(animeTitle)
        val animeDir = sourceDir.createDirectory(animeDirName)
        if (animeDir == null) {
            val displayablePath = sourceDir.displayablePath + "/$animeDirName"
            logcat(LogPriority.ERROR) { "Failed to create anime download directory: $displayablePath" }
            throw IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath))
        }

        return animeDir
    }

    fun findSourceDir(source: AnimeSource): UniFile? {
        return downloadsDir?.findFile(getSourceDirName(source))
    }

    fun findAnimeDir(animeTitle: String, source: AnimeSource): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getAnimeDirName(animeTitle))
    }

    fun findEpisodeDir(
        episodeName: String,
        episodeScanlator: String?,
        animeTitle: String,
        source: AnimeSource,
    ): UniFile? {
        val animeDir = findAnimeDir(animeTitle, source)
        return getValidEpisodeDirNames(episodeName, episodeScanlator).asSequence()
            .mapNotNull { animeDir?.findFile(it) }
            .firstOrNull()
    }

    fun findEpisodeDirs(episodes: List<Episode>, anime: Anime, source: AnimeSource): Pair<UniFile?, List<UniFile>> {
        val animeDir = findAnimeDir(anime.title, source) ?: return null to emptyList()
        return animeDir to episodes.mapNotNull { episode ->
            getValidEpisodeDirNames(episode.name, episode.scanlator).asSequence()
                .mapNotNull { animeDir.findFile(it) }
                .firstOrNull()
        }
    }

    fun getEpisodeFileSize(
        episodeName: String,
        @Suppress("UNUSED_PARAMETER") episodeUrl: String,
        episodeScanlator: String?,
        animeTitle: String,
        source: AnimeSource,
    ): Long? {
        val episodeDir = findEpisodeDir(episodeName, episodeScanlator, animeTitle, source) ?: return null
        return episodeDir
            .listFiles()
            .orEmpty()
            .sumOf { it.length() }
            .takeIf { it > 0L }
    }

    fun getSourceDirName(source: AnimeSource): String {
        return DiskUtil.buildValidFilename(
            source.toString(),
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().get(),
        )
    }

    fun getAnimeDirName(animeTitle: String): String {
        return DiskUtil.buildValidFilename(
            animeTitle,
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().get(),
        )
    }

    fun getEpisodeDirName(episodeName: String, episodeScanlator: String?): String {
        var dirName = episodeName.ifBlank { "Episode" }
        if (!episodeScanlator.isNullOrBlank()) {
            dirName = "${episodeScanlator}_$dirName"
        }
        return DiskUtil.buildValidFilename(
            dirName,
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames().get(),
        )
    }

    internal fun getValidEpisodeDirNames(episodeName: String, episodeScanlator: String?): List<String> {
        val episodeDirName = getEpisodeDirName(episodeName, episodeScanlator)
        return listOf(episodeDirName)
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
    }
}
