package eu.kanade.tachiyomi.ui.youtube

import android.net.Uri
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class YoutubeResolver {

    companion object {
        @Volatile
        private var initialized = false
        private val initLock = Any()
        private val resolveMutex = Mutex()
        private val client = OkHttpClient()

        private fun ensureInitialized() {
            if (initialized) return
            synchronized(initLock) {
                if (initialized) return
                NewPipe.init(object : Downloader() {
                    override fun execute(request: Request): Response {
                        val req = okhttp3.Request.Builder()
                            .url(request.url())
                            .method(
                                request.httpMethod(),
                                request.dataToSend()?.let { it.toRequestBody(null) },
                            )
                            .apply {
                                request.headers().forEach { (key, values) ->
                                    values.forEach { addHeader(key, it) }
                                }
                            }
                            .build()
                        val res = client.newCall(req).execute()
                        val body = res.body.string()
                        return Response(
                            res.code,
                            res.message,
                            res.headers.toMultimap(),
                            body,
                            request.url(),
                        )
                    }
                })
                initialized = true
            }
        }

        suspend fun resolveAllStreams(
            videoUrl: String,
            preferredQuality: String = YouTubePreferences.DEFAULT_QUALITY,
        ): List<Video> = resolveMutex.withLock {
            withContext(Dispatchers.IO) {
                ensureInitialized()
                val videoId = extractVideoId(videoUrl)
                    ?: throw IllegalArgumentException("Could not find YouTube video id")
                val linkHandler = ServiceList.YouTube.getStreamLHFactory().fromId(videoId)
                val extractor = ServiceList.YouTube.getStreamExtractor(linkHandler)
                extractor.fetchPage()
                val subtitleTracks = listOf(
                    runCatching { extractor.subtitlesDefault }.getOrDefault(emptyList()),
                    runCatching { extractor.getSubtitles(MediaFormat.VTT) }.getOrDefault(emptyList()),
                    runCatching { extractor.getSubtitles(MediaFormat.SRT) }.getOrDefault(emptyList()),
                    runCatching { extractor.getSubtitles(MediaFormat.TTML) }.getOrDefault(emptyList()),
                )
                    .flatten()
                    .filter { it.content.isNotBlank() }
                    .map { sub ->
                        Track(
                            url = sub.content,
                            lang = sub.displayLanguageName
                                ?: sub.languageTag
                                ?: sub.locale?.displayLanguage
                                ?: "",
                        )
                    }
                    .distinctBy { it.url to it.lang }

                val audioTracks = runCatching {
                    extractor.audioStreams
                        .filter { it.content.isNotBlank() }
                        .sortedWith(
                            compareByDescending<org.schabi.newpipe.extractor.stream.AudioStream> {
                                it.audioTrackName?.contains("original", ignoreCase = true) == true ||
                                    it.audioLocale?.displayName?.contains("original", ignoreCase = true) == true ||
                                    it.quality?.contains("original", ignoreCase = true) == true
                            }.thenByDescending { it.averageBitrate },
                        )
                        .map { audio ->
                            Track(
                                url = audio.content,
                                lang = audio.audioTrackName
                                    ?: audio.audioLocale?.displayLanguage
                                    ?: audio.quality
                                    ?: "Audio",
                            )
                        }
                        .distinctBy { it.url to it.lang }
                }.getOrDefault(emptyList())

                val streams = (extractor.videoStreams + extractor.videoOnlyStreams)
                    .filter { it.content.isNotBlank() }
                    .filter { !it.isVideoOnly() || audioTracks.isNotEmpty() }
                    .sortedWith(
                        compareByDescending<org.schabi.newpipe.extractor.stream.VideoStream> {
                            parseResolution(it.getResolution().ifBlank { it.quality.orEmpty() })
                        }.thenBy { it.isVideoOnly() },
                    )
                    .distinctBy { stream ->
                        listOf(
                            parseResolution(stream.getResolution().ifBlank { stream.quality.orEmpty() }),
                            stream.content,
                        )
                    }

                val streamVideos = streams.mapNotNull { stream ->
                    val resolution = stream.getResolution().ifBlank { stream.quality ?: "Video" }
                    val needsExternalAudio = stream.isVideoOnly()

                    Video(
                        videoUrl = stream.content,
                        videoTitle = resolution,
                        resolution = parseResolution(resolution).takeIf { it > 0 },
                        subtitleTracks = subtitleTracks,
                        audioTracks = if (needsExternalAudio) audioTracks else emptyList(),
                        initialized = true,
                        videoPageUrl = videoUrl,
                    ).withoutExternalSubtitleLookup()
                }

                val videos = streamVideos.distinctBy { it.videoTitle to it.videoUrl }
                val preferred = selectPreferredVideo(videos, preferredQuality)
                videos.map { video ->
                    video.copy(preferred = video == preferred)
                }
            }
        }

        private fun extractVideoId(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.isYouTubeVideoId()) return trimmed

            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            val host = uri.host.orEmpty().lowercase().removePrefix("www.")
            val pathSegments = uri.pathSegments.orEmpty()

            if (host.contains("youtu.be")) {
                pathSegments.firstOrNull()?.takeIf { it.isYouTubeVideoId() }?.let { return it }
            }

            if (host.contains("youtube.com") || host.contains("youtube-nocookie.com")) {
                when (pathSegments.firstOrNull()) {
                    "watch" -> uri.getQueryParameter("v")
                    "embed", "shorts", "live", "v" -> pathSegments.getOrNull(1)
                    else -> null
                }
                    ?.takeIf { it.isYouTubeVideoId() }
                    ?.let { return it }
            }

            return null
        }

        private fun String.isYouTubeVideoId(): Boolean {
            return length == 11 && all { it.isLetterOrDigit() || it == '_' || it == '-' }
        }

        fun parseResolution(res: String): Int {
            val normalized = res.lowercase()
            return when {
                "8k" in normalized -> 4320
                "4k" in normalized -> 2160
                else -> Regex("""(\d{3,4})\s*p?""")
                    .find(normalized)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0
            }
        }

        private fun selectPreferredVideo(
            videos: List<Video>,
            preferredQuality: String,
        ): Video? {
            if (videos.isEmpty()) return null

            val targetPixels = parseResolution(preferredQuality)
            val qualityVideos = videos.filter { parseResolution(it.videoTitle) > 0 }
            return qualityVideos.firstOrNull { video ->
                parseResolution(video.videoTitle) <= targetPixels
            } ?: qualityVideos.lastOrNull() ?: videos.first()
        }
    }
}
