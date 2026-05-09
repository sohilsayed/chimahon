package eu.kanade.tachiyomi.data.animedownload

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.util.storage.DiskUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.Request
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.animesource.service.AnimeSourceManager
import tachiyomi.domain.episode.model.Episode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.RandomAccessFile

class AnimeDownloader(
    private val context: Context,
    private val provider: AnimeDownloadProvider,
    private val cache: AnimeDownloadCache,
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = AnimeDownloadStore(context)
    private val notifier by lazy { AnimeDownloadNotifier(context) }
    private val downloadClient by lazy {
        networkHelper.client.newBuilder()
            .callTimeout(java.time.Duration.ofMinutes(10))
            .readTimeout(java.time.Duration.ofMinutes(5))
            .build()
    }

    private val _queueState = MutableStateFlow<List<AnimeDownload>>(emptyList())
    private val _stateVersion = MutableStateFlow(0L)

    val queueState = _queueState.asStateFlow()
    val stateVersion = _stateVersion.asStateFlow()

    private fun notifyQueueChanged() {
        _stateVersion.update { it + 1 }
    }

    private var downloaderJob: Job? = null

    val isRunning: Boolean
        get() = downloaderJob?.isActive == true

    fun start(): Boolean {
        if (downloaderJob?.isActive == true) {
            logcat(LogPriority.INFO) { "AnimeDownloader: already running" }
            return true
        }

        val queuedDownloads = _queueState.value.filter { it.status == AnimeDownload.State.QUEUE }
        if (queuedDownloads.isEmpty()) {
            val restored = store.restore()
            if (restored.isEmpty()) {
                logcat(LogPriority.INFO) { "AnimeDownloader: nothing to start (queue empty, no restore)" }
                return false
            }
            restored.forEach { it.status = AnimeDownload.State.QUEUE }
            addAllToQueue(restored)
        }

        logcat(LogPriority.INFO) { "AnimeDownloader: starting processQueue with ${_queueState.value.size} items" }
        downloaderJob = scope.launchIO {
            processQueue()
        }
        return true
    }

    fun stop(reason: String? = null) {
        downloaderJob?.cancel()
        downloaderJob = null

        if (_queueState.value.isEmpty()) {
            notifier.onComplete()
            return
        }

        if (reason != null) {
            notifier.onPaused()
        } else {
            notifier.onComplete()
        }

        _queueState.value
            .filter { it.status == AnimeDownload.State.DOWNLOADING }
            .forEach { it.status = AnimeDownload.State.QUEUE }
        notifyQueueChanged()
    }

    fun pause() {
        downloaderJob?.cancel()
        downloaderJob = null
        _queueState.value
            .filter { it.status == AnimeDownload.State.DOWNLOADING }
            .forEach { it.status = AnimeDownload.State.QUEUE }
        notifyQueueChanged()
        notifier.onPaused()
    }

    fun clearQueue() {
        store.clear()
        _queueState.update { emptyList() }
        notifier.dismissProgress()
    }

    fun queueEpisodes(anime: Anime, episodes: List<Episode>, videos: List<Video?>, autoStart: Boolean) {
        val source = animeSourceManager.get(anime.source) as? AnimeHttpSource
        if (source == null) {
            logcat(LogPriority.ERROR) { "AnimeDownloader: source ${anime.source} not found or not AnimeHttpSource" }
            return
        }

        val episodesToQueue = episodes.zip(videos).filter { (episode, _) ->
            val isDuplicate = _queueState.value.any { it.episode.id == episode.id }
            val isDownloaded = provider.findEpisodeDir(
                episode.name,
                episode.scanlator,
                anime.title,
                source,
            ) != null
            !isDuplicate && !isDownloaded
        }

        if (episodesToQueue.isEmpty()) {
            logcat(LogPriority.WARN) { "AnimeDownloader: nothing to queue (duplicate or already downloaded)" }
            return
        }

        val downloads = episodesToQueue.map { (episode, video) ->
            AnimeDownload(source, anime, episode, video)
        }

        downloads.forEach { it.status = AnimeDownload.State.QUEUE }
        addAllToQueue(downloads)
        store.addAll(downloads)
        logcat(LogPriority.INFO) { "AnimeDownloader: queued ${downloads.size} episode(s)" }

        if (autoStart) {
            AnimeDownloadJob.start(context)
        }
    }

    fun removeFromQueue(episodes: List<Episode>) {
        val episodeIds = episodes.map { it.id }.toSet()
        val removed = _queueState.value.filter { it.episode.id in episodeIds }
        store.removeAll(removed)
        _queueState.update { queue -> queue.filter { it.episode.id !in episodeIds } }
    }

    fun removeFromQueue(anime: Anime) {
        val removed = _queueState.value.filter { it.anime.id == anime.id }
        store.removeAll(removed)
        _queueState.update { queue -> queue.filter { it.anime.id != anime.id } }
    }

    private fun addAllToQueue(downloads: List<AnimeDownload>) {
        _queueState.update { it + downloads }
    }

    private fun removeFromQueueState(download: AnimeDownload) {
        _queueState.update { queue -> queue.filter { it.episode.id != download.episode.id } }
    }

    private suspend fun processQueue() {
        logcat(LogPriority.INFO) { "AnimeDownloader: processQueue started" }
        supervisorScope {
            while (true) {
                val download = _queueState.value.firstOrNull { it.status == AnimeDownload.State.QUEUE }
                    ?: break

                logcat(LogPriority.INFO) { "AnimeDownloader: downloading ${download.episode.name}" }
                try {
                    downloadEpisode(download)
                    store.remove(download)
                    removeFromQueueState(download)
                    logcat(LogPriority.INFO) { "AnimeDownloader: completed ${download.episode.name}" }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to download ${download.episode.name}" }
                    download.status = AnimeDownload.State.ERROR
                    notifyQueueChanged()
                    notifier.onError(e.message, download.episode.name, download.anime.title)
                    store.remove(download)
                }
            }
        }

        notifier.onComplete()
        stop()
    }

    private suspend fun downloadEpisode(download: AnimeDownload) {
        val video = download.video
            ?: throw IllegalStateException("Video not resolved for episode ${download.episode.name}")

        val videoUrl = video.videoUrl
        if (videoUrl.isBlank()) {
            throw IllegalStateException("Video URL is blank for episode ${download.episode.name}")
        }

        if (isStreamingUrl(videoUrl)) {
            throw IllegalStateException(
                "Cannot download streaming content (DASH/HLS). URL: ${videoUrl.take(80)}",
            )
        }

        download.status = AnimeDownload.State.DOWNLOADING
        notifyQueueChanged()
        notifier.onProgressChange(download)

        val animeDir = provider.getAnimeDir(download.anime.title, download.source).getOrThrow()
        val episodeDirName = provider.getEpisodeDirName(download.episode.name, download.episode.scanlator)
        val tmpDirName = episodeDirName + AnimeDownloadProvider.TMP_DIR_SUFFIX
        val tmpDir = animeDir.createDirectory(tmpDirName)
            ?: throw IllegalStateException("Failed to create temp directory")

        try {
            DiskUtil.createNoMediaFile(tmpDir, context)

            downloadVideoFile(video, tmpDir, download)

            downloadSubtitles(video, tmpDir)

            writeMetadata(video, tmpDir)

            // Rename tmp → final
            val finalDir = animeDir.findFile(episodeDirName)
            finalDir?.delete()
            tmpDir.renameTo(episodeDirName)

            download.status = AnimeDownload.State.DOWNLOADED
            download.progress = 100
            cache.addEpisode(episodeDirName, animeDir, download.anime)
        } catch (e: Exception) {
            tmpDir.delete()
            throw e
        }
    }

    private suspend fun downloadVideoFile(video: Video, tmpDir: UniFile, download: AnimeDownload) {
        withContext(Dispatchers.IO) {
            val extension = guessVideoExtension(video.videoUrl)
            val videoFileName = "video.$extension"
            val tmpFile = File(context.cacheDir, "anime_dl_${download.episode.id}.$extension")

            try {
                val headers = video.headers
                    ?: download.source.headers
                    ?: Headers.Builder().build()

                val progressListener = object : ProgressListener {
                    private var lastNotify = 0L
                    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                        download.downloadedBytes = bytesRead
                        if (download.totalBytes <= 0 && contentLength > 0) {
                            download.totalBytes = contentLength
                        }
                        val total = if (contentLength > 0) contentLength else download.totalBytes
                        if (total > 0) {
                            download.progress = ((bytesRead * 100) / total).toInt().coerceIn(0, 99)
                            val now = System.currentTimeMillis()
                            if (now - lastNotify > 1000) {
                                lastNotify = now
                                notifier.onProgressChange(download)
                                notifyQueueChanged()
                            }
                        }
                    }
                }

                downloadWithResume(video.videoUrl, headers, tmpFile, progressListener)

                val videoFile = tmpDir.createFile(videoFileName)
                    ?: throw IllegalStateException("Failed to create video file in episode dir")
                videoFile.openOutputStream().use { output ->
                    tmpFile.inputStream().use { input ->
                        input.copyTo(output, bufferSize = 256 * 1024)
                    }
                }
            } finally {
                tmpFile.delete()
            }
        }
    }

    private suspend fun downloadWithResume(url: String, headers: Headers, outputFile: File, progressListener: ProgressListener) {
        var attempt = 0
        val maxRetries = 3

        while (attempt < maxRetries) {
            try {
                val downloadedBytes = outputFile.length()

                val requestHeaders = headers.newBuilder()
                    .set("Range", "bytes=$downloadedBytes-")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .headers(requestHeaders)
                    .build()

                downloadClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 206) {
                        if (response.code == 200 && downloadedBytes > 0) {
                            outputFile.delete()
                        }

                        val body = response.body
                        val contentLength = body.contentLength() + (if (response.code == 206) downloadedBytes else 0)

                        RandomAccessFile(outputFile, "rw").use { raf ->
                            val startPos = if (response.code == 206) downloadedBytes else 0
                            raf.seek(startPos)

                            val buffer = ByteArray(8192)
                            var totalRead = startPos
                            body.byteStream().use { stream ->
                                var read: Int
                                while (stream.read(buffer).also { read = it } != -1) {
                                    raf.write(buffer, 0, read)
                                    totalRead += read
                                    progressListener.update(totalRead, contentLength, false)
                                }
                            }
                        }

                        progressListener.update(contentLength, contentLength, true)
                        return
                    } else if (response.code == 416) {
                        outputFile.delete()
                        attempt++
                    } else {
                        attempt++
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Download attempt $attempt failed" }
                attempt++
                if (attempt < maxRetries) {
                    delay((1000L * (1 shl attempt)).coerceAtMost(8000))
                }
            }
        }

        throw java.io.IOException("Failed to download video after $maxRetries attempts")
    }

    private suspend fun downloadSubtitles(video: Video, tmpDir: UniFile) {
        if (video.subtitleTracks.isEmpty()) return

        val subtitleDir = tmpDir.createDirectory("subtitles") ?: return

        withContext(Dispatchers.IO) {
            supervisorScope {
                video.subtitleTracks.map { track ->
                    async {
                        try {
                            val fileName = "${track.lang}.${subtitleExtensionFromUrl(track.url)}"
                            val request = Request.Builder()
                                .url(track.url)
                                .build()

                            networkHelper.client.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val subFile = subtitleDir.createFile(fileName) ?: return@use
                                    subFile.openOutputStream().use { output ->
                                        response.body.byteStream().copyTo(output)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.WARN, e) { "Failed to download subtitle: ${track.lang}" }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun writeMetadata(video: Video, tmpDir: UniFile) {
        val metadata = listOf(video).serialize()
        val metadataFile = tmpDir.createFile("metadata.json") ?: return
        metadataFile.openOutputStream().use { output ->
            output.write(metadata.toByteArray(Charsets.UTF_8))
        }
    }

    private fun guessVideoExtension(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "").lowercase().take(5)
        return when {
            ext in setOf("mp4", "mkv", "webm", "avi", "m4v", "ts") -> ext
            else -> "mp4"
        }
    }

    private fun isStreamingUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".mpd") ||
            path.endsWith(".m3u8") ||
            path.endsWith(".m3u") ||
            path.contains("/manifest") && (path.endsWith(".mpd") || path.contains("dash"))
    }
}

internal fun subtitleExtensionFromUrl(url: String): String =
    url.substringAfterLast('.').substringBefore('?').take(5).ifBlank { "srt" }
