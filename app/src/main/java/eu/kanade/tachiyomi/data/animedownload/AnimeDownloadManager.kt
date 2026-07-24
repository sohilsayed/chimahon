package eu.kanade.tachiyomi.data.animedownload

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import eu.kanade.tachiyomi.util.size
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import tachiyomi.source.local.io.ArchiveAnime
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDownloadManager(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
    private val provider: AnimeDownloadProvider = Injekt.get(),
    private val cache: AnimeDownloadCache = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {

    private val downloader = AnimeDownloader(context, provider, cache)

    val isRunning: Boolean
        get() = downloader.isRunning

    private val pendingDeleter = AnimeDownloadPendingDeleter(context)

    val queueState
        get() = downloader.queueState

    fun downloaderStart() = downloader.start()
    fun downloaderStop(reason: String? = null) = downloader.stop(reason)

    val isDownloaderRunning
        get() = AnimeDownloadJob.isRunningFlow(context)

    fun startDownloads() {
        if (downloader.isRunning) return
        if (AnimeDownloadJob.isRunning(context)) {
            downloader.start()
        } else {
            AnimeDownloadJob.start(context)
        }
    }

    fun pauseDownloads() {
        downloader.stop()
    }

    fun clearQueue() {
        downloader.clearQueue()
        downloader.stop()
    }

    fun getQueuedDownloadOrNull(episodeId: Long): AnimeDownload? {
        return queueState.value.find { it.episode.id == episodeId }
    }

    fun startDownloadNow(episodeId: Long) {
        val existingDownload = getQueuedDownloadOrNull(episodeId)
        val toAdd = existingDownload ?: runBlocking { AnimeDownload.fromEpisodeId(episodeId) } ?: return
        queueState.value.toMutableList().apply {
            existingDownload?.let { remove(it) }
            add(0, toAdd)
            reorderQueue(this)
        }
        startDownloads()
    }

    fun reorderQueue(downloads: List<AnimeDownload>) {
        downloader.updateQueue(downloads)
    }

    fun downloadEpisodes(
        anime: Anime,
        episodes: List<Episode>,
        autoStart: Boolean = true,
        alt: Boolean = false,
        video: Video? = null,
    ) {
        val filteredEpisodes = getEpisodesToDownload(episodes)
        downloader.queueEpisodes(anime, filteredEpisodes, autoStart, alt, video)
    }

    fun addDownloadsToStartOfQueue(downloads: List<AnimeDownload>) {
        if (downloads.isEmpty()) return
        queueState.value.toMutableList().apply {
            addAll(0, downloads)
            reorderQueue(this)
        }
        if (!AnimeDownloadJob.isRunning(context)) startDownloads()
    }

    fun buildVideo(source: AnimeSource, anime: Anime, episode: Episode): Video {
        val episodeDir = provider.findEpisodeDir(episode.name, episode.scanlator, anime.title, source)
        val files = episodeDir?.listFiles().orEmpty()
            .filter { "video" in it.type.orEmpty() }

        if (files.isEmpty()) {
            throw Exception(context.stringResource(MR.strings.page_list_empty_error))
        }

        val file = files[0]

        return Video(
            videoUrl = file.uri.toString(),
            videoTitle = "download: " + file.uri.toString(),
            initialized = true,
        ).apply { status = Video.State.READY }
    }

    fun isEpisodeDownloaded(
        episodeName: String,
        episodeScanlator: String?,
        animeTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return cache.isEpisodeDownloaded(episodeName, episodeScanlator, animeTitle, sourceId, skipCache)
    }

    fun getDownloadCount(): Int {
        return cache.getTotalDownloadCount()
    }

    fun getDownloadCount(anime: Anime): Int {
        return if (anime.source == LocalAnimeSource.ID) {
            LocalAnimeSourceFileSystem(storageManager).getFilesInAnimeDirectory(anime.url)
                .count { ArchiveAnime.isSupported(it) }
        } else {
            cache.getDownloadCount(anime)
        }
    }

    fun getDownloadSize(): Long {
        return cache.getTotalDownloadSize()
    }

    fun getDownloadSize(anime: Anime): Long {
        return if (anime.source == LocalAnimeSource.ID) {
            LocalAnimeSourceFileSystem(storageManager).getAnimeDirectory(anime.url)?.size() ?: 0L
        } else {
            cache.getDownloadSize(anime)
        }
    }

    fun cancelQueuedDownloads(downloads: List<AnimeDownload>) {
        removeFromDownloadQueue(downloads.map { it.episode })
    }

    fun deleteEpisodes(episodes: List<Episode>, anime: Anime, source: AnimeSource) {
        launchIO {
            val filteredEpisodes = getEpisodesToDelete(episodes, anime)
            if (filteredEpisodes.isEmpty()) return@launchIO

            removeFromDownloadQueue(filteredEpisodes)
            val (animeDir, episodeDirs) = provider.findEpisodeDirs(filteredEpisodes, anime, source)
            episodeDirs.forEach { it.delete() }
            cache.removeEpisodes(filteredEpisodes, anime)

            if (animeDir?.listFiles()?.isEmpty() == true) {
                deleteAnime(anime, source, removeQueued = false)
            }
        }
    }

    fun deleteAnime(anime: Anime, source: AnimeSource, removeQueued: Boolean = true) {
        launchIO {
            if (removeQueued) {
                downloader.removeFromQueue(anime)
            }
            provider.findAnimeDir(anime.title, source)?.delete()
            cache.removeAnime(anime)

            val sourceDir = provider.findSourceDir(source)
            if (sourceDir?.listFiles()?.isEmpty() == true) {
                sourceDir.delete()
                cache.removeSource(source)
            }
        }
    }

    private fun removeFromDownloadQueue(episodes: List<Episode>) {
        val wasRunning = downloader.isRunning
        if (wasRunning) {
            downloader.pause()
        }

        downloader.removeFromQueue(episodes)

        if (wasRunning) {
            if (queueState.value.isEmpty()) {
                downloader.stop()
            } else if (queueState.value.isNotEmpty()) {
                downloader.start()
            }
        }
    }

    suspend fun enqueueEpisodesToDelete(episodes: List<Episode>, anime: Anime) {
        pendingDeleter.addEpisodes(getEpisodesToDelete(episodes, anime), anime)
    }

    fun deletePendingEpisodes() {
        val pendingEpisodes = pendingDeleter.getPendingEpisodes()
        for ((anime, episodes) in pendingEpisodes) {
            val source = sourceManager.get(anime.source) ?: continue
            deleteEpisodes(episodes, anime, source)
        }
    }

    fun renameSource(oldSource: AnimeSource, newSource: AnimeSource) {
        val oldFolder = provider.findSourceDir(oldSource) ?: return
        val newName = provider.getSourceDirName(newSource)

        if (oldFolder.name == newName) return

        val capitalizationChanged = oldFolder.name.equals(newName, ignoreCase = true)
        if (capitalizationChanged) {
            val tempName = newName + AnimeDownloadProvider.TMP_DIR_SUFFIX
            if (!oldFolder.renameTo(tempName)) {
                logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
                return
            }
        }

        if (!oldFolder.renameTo(newName)) {
            logcat(LogPriority.ERROR) { "Failed to rename source download folder: ${oldFolder.name}" }
        }
    }

    suspend fun renameEpisode(source: AnimeSource, anime: Anime, oldEpisode: Episode, newEpisode: Episode) {
        val oldNames = provider.getValidEpisodeDirNames(oldEpisode.name, oldEpisode.scanlator)
        val animeDir = provider.getAnimeDir(anime.title, source)

        val oldFolder = oldNames.asSequence()
            .mapNotNull { animeDir.findFile(it) }
            .firstOrNull()

        val newName = provider.getEpisodeDirName(newEpisode.name, newEpisode.scanlator)

        if (oldFolder?.name == newName) return

        if (oldFolder?.renameTo(newName) == true) {
            cache.removeEpisode(oldEpisode, anime)
            cache.addEpisode(newName, animeDir, anime)
        } else {
            logcat(LogPriority.ERROR) { "Could not rename downloaded episode: ${oldNames.joinToString()}" }
        }
    }

    private suspend fun getEpisodesToDelete(episodes: List<Episode>, anime: Anime): List<Episode> {
        val categoriesToExclude = downloadPreferences.removeExcludeAnimeCategories().get().map(String::toLong)
        val categoriesForAnime = getCategories.await(anime.id)
            .map { it.id }
            .ifEmpty { listOf(0) }
        val filteredCategoryAnime = if (categoriesForAnime.intersect(categoriesToExclude).isNotEmpty()) {
            episodes.filterNot { it.seen }
        } else {
            episodes
        }

        return if (!downloadPreferences.removeBookmarkedChapters().get()) {
            filteredCategoryAnime.filterNot { it.bookmark }
        } else {
            filteredCategoryAnime
        }
    }

    private fun getEpisodesToDownload(episodes: List<Episode>): List<Episode> {
        return if (!downloadPreferences.downloadFillermarkedItems().get()) {
            episodes.filterNot { it.fillermark }
        } else {
            episodes
        }
    }

    fun statusFlow(): Flow<AnimeDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == AnimeDownload.State.DOWNLOADING }
                    .asFlow(),
            )
        }

    fun progressFlow(): Flow<AnimeDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == AnimeDownload.State.DOWNLOADING }
                    .asFlow(),
            )
        }
}
