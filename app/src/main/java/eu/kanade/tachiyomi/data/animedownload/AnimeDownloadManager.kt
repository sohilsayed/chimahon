package eu.kanade.tachiyomi.data.animedownload

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.toVideoList
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import logcat.LogPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.animesource.service.AnimeSourceManager
import tachiyomi.domain.episode.model.Episode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDownloadManager(
    private val context: Context,
    private val provider: AnimeDownloadProvider = Injekt.get(),
    private val cache: AnimeDownloadCache = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloader = AnimeDownloader(context, provider, cache)
    private val pendingDeleter = AnimeDownloadPendingDeleter(context)

    val isRunning: Boolean
        get() = downloader.isRunning

    val isDownloaderRunning
        get() = AnimeDownloadJob.isRunningFlow(context)

    val queueState
        get() = downloader.queueState

    val stateVersion
        get() = downloader.stateVersion

    suspend fun downloaderStart() = downloader.start()
    fun downloaderStop(reason: String? = null) = downloader.stop(reason)

    fun startDownloads() {
        if (downloader.isRunning) return
        if (AnimeDownloadJob.isRunning(context)) {
            scope.launch { downloader.start() }
        } else {
            AnimeDownloadJob.start(context)
        }
    }

    fun pauseDownloads() {
        downloader.pause()
        AnimeDownloadJob.stop(context)
    }

    fun clearQueue() {
        downloader.clearQueue()
        AnimeDownloadJob.stop(context)
    }

    fun cancelEpisodeDownload(download: AnimeDownload) {
        downloader.removeFromQueue(listOf(download.episode))
    }

    fun downloadEpisodes(anime: Anime, episodes: List<Episode>, videos: List<Video?>, autoStart: Boolean = true) {
        downloader.queueEpisodes(anime, episodes, videos, autoStart)
    }

    fun isEpisodeDownloaded(episodeName: String, scanlator: String?, animeTitle: String, sourceId: Long): Boolean {
        return cache.isEpisodeDownloaded(episodeName, scanlator, animeTitle, sourceId)
    }

    fun getDownloadCount(anime: Anime): Int {
        return cache.getDownloadCount(anime)
    }

    suspend fun deleteEpisodes(episodes: List<Episode>, anime: Anime, source: AnimeSource) {
        val (_, episodeDirs) = provider.findEpisodeDirs(episodes, anime, source)
        episodeDirs.forEach { it.delete() }
        cache.removeEpisodes(episodes, anime)
        downloader.removeFromQueue(episodes)
    }

    suspend fun deleteAnime(anime: Anime, source: AnimeSource) {
        val animeDir = provider.findAnimeDir(anime.title, source)
        animeDir?.delete()
        cache.removeAnime(anime)
        downloader.removeFromQueue(anime)
    }

    fun buildVideoForPlayer(anime: Anime, episode: Episode, source: AnimeSource): Video? {
        val episodeDir = provider.findEpisodeDir(
            episode.name,
            episode.scanlator,
            anime.title,
            source,
        ) ?: return null

        val videoFile = episodeDir.listFiles()?.firstOrNull { file ->
            val name = file.name ?: return@firstOrNull false
            !file.isDirectory &&
                name != "metadata.json" &&
                name != ".nomedia" &&
                !name.startsWith(".")
        } ?: return null

        val metadataFile = episodeDir.findFile("metadata.json")
        var subtitleTracks = emptyList<Track>()
        var timestamps = emptyList<eu.kanade.tachiyomi.animesource.model.TimeStamp>()
        var videoTitle = ""

        if (metadataFile != null) {
            try {
                val json = metadataFile.openInputStream().use { it.bufferedReader().readText() }
                val videos = json.toVideoList()
                videos.firstOrNull()?.let { v ->
                    videoTitle = v.videoTitle
                    timestamps = v.timestamps
                    // Remap subtitle tracks to local paths
                    val subtitleDir = episodeDir.findFile("subtitles")
                    subtitleTracks = v.subtitleTracks.map { track ->
                        val localFile = subtitleDir?.findFile("${track.lang}.${subtitleExtensionFromUrl(track.url)}")
                        Track(
                            url = localFile?.uri?.toString() ?: track.url,
                            lang = track.lang,
                        )
                    }
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to parse metadata for downloaded episode" }
            }
        }

        return Video(
            videoUrl = videoFile.uri.toString(),
            videoTitle = videoTitle,
            subtitleTracks = subtitleTracks,
            timestamps = timestamps,
        )
    }

    fun statusFlow(): Flow<AnimeDownload?> {
        return queueState.map { queue ->
            queue.firstOrNull { it.status == AnimeDownload.State.DOWNLOADING }
                ?: queue.firstOrNull()
        }
    }
}
