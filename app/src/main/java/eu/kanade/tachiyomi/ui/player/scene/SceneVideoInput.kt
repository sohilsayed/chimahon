package eu.kanade.tachiyomi.ui.player.scene

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal enum class SceneVideoInputKind {
    LOCAL_FILE,
    CONTENT_URI,
    REMOTE_HTTP,
}

internal enum class SceneVideoInputOrigin {
    ORIGINAL_VIDEO,
    PLAYABLE_VIDEO,
    EXTERNAL_AUDIO,
}

internal data class SceneVideoInputSpec(
    val value: String,
    val kind: SceneVideoInputKind,
    val headers: List<Pair<String, String>>,
    val videoStreamIndex: Int? = null,
    val audioStreamIndex: Int? = null,
    val origin: SceneVideoInputOrigin = SceneVideoInputOrigin.ORIGINAL_VIDEO,
)

internal data class SceneVideoInputSnapshot(
    val originalVideoValue: String,
    val playableValue: String?,
    val headers: List<Pair<String, String>>,
    val ffmpegStreamArgs: List<Pair<String, String>>,
    val ffmpegVideoArgs: List<Pair<String, String>>,
    val seekable: Boolean?,
    val videoStreamIndex: Int? = null,
    val audioStreamIndex: Int? = null,
    val isExternalAudio: Boolean = false,
)

internal object SceneVideoInputResolver {
    fun resolve(snapshot: SceneVideoInputSnapshot): SceneVideoInputSpec? {
        if (snapshot.originalVideoValue.isBlank() && snapshot.playableValue.isNullOrBlank()) {
            return null
        }
        if (isDash(snapshot.originalVideoValue) || isDash(snapshot.playableValue)) return null
        if (snapshot.ffmpegStreamArgs.isNotEmpty() || snapshot.ffmpegVideoArgs.isNotEmpty()) {
            return null
        }
        if (snapshot.seekable != true) return null

        val original = snapshot.originalVideoValue.takeIf(String::isNotBlank)
        if (original != null && isTransient(original)) return null
        val normalizedOriginal = original?.let(::normalizeInput)
        val normalized = normalizedOriginal
            ?: snapshot.playableValue?.takeIf(String::isNotBlank)?.let { playable ->
                if (isTransient(playable)) return null
                normalizeInput(playable)
            }
            ?: return null
        val origin = when {
            snapshot.isExternalAudio -> SceneVideoInputOrigin.EXTERNAL_AUDIO
            normalizedOriginal != null -> SceneVideoInputOrigin.ORIGINAL_VIDEO
            else -> SceneVideoInputOrigin.PLAYABLE_VIDEO
        }

        val headers = when (normalized.second) {
            SceneVideoInputKind.REMOTE_HTTP -> validateRemoteInput(normalized.first, snapshot.headers)
                ?: return null
            SceneVideoInputKind.LOCAL_FILE,
            SceneVideoInputKind.CONTENT_URI,
            -> emptyList()
        }

        return SceneVideoInputSpec(
            value = normalized.first,
            kind = normalized.second,
            headers = headers,
            videoStreamIndex = snapshot.videoStreamIndex?.takeIf { it >= 0 },
            audioStreamIndex = snapshot.audioStreamIndex?.takeIf { it >= 0 },
            origin = origin,
        )
    }

    private fun validateRemoteInput(
        value: String,
        headers: List<Pair<String, String>>,
    ): List<Pair<String, String>>? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.userInfo.isNullOrBlank() || uri.host.isNullOrBlank()) return null
        if (hasSensitiveQuery(uri.rawQuery.orEmpty())) return null
        if (!headers.all(::isAllowedHeader)) return null
        return headers
    }

    private fun normalizeInput(value: String): Pair<String, SceneVideoInputKind>? {
        return when {
            value.startsWith("content://", ignoreCase = true) -> {
                value to SceneVideoInputKind.CONTENT_URI
            }
            value.startsWith("file://", ignoreCase = true) -> {
                val path = runCatching { URI(value).path }.getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: return null
                path to SceneVideoInputKind.LOCAL_FILE
            }
            value.startsWith("/") -> value to SceneVideoInputKind.LOCAL_FILE
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> {
                value to SceneVideoInputKind.REMOTE_HTTP
            }
            else -> null
        }
    }

    private fun isAllowedHeader(header: Pair<String, String>): Boolean {
        val (name, value) = header
        return name.lowercase(Locale.ROOT) in ALLOWED_HTTP_HEADERS &&
            value.length <= MAX_HEADER_VALUE_LENGTH &&
            value.none { it == '\u0000' || it == '\r' || it == '\n' || (it.code < 0x20 && it != '\t') }
    }

    private fun hasSensitiveQuery(query: String): Boolean {
        query.split('&').forEach { parameter ->
            val name = runCatching {
                URLDecoder.decode(parameter.substringBefore('='), StandardCharsets.UTF_8.name())
                    .lowercase(Locale.ROOT)
            }.getOrNull() ?: return true
            if (name in SENSITIVE_QUERY_NAMES || SENSITIVE_QUERY_PREFIXES.any(name::startsWith)) {
                return true
            }
        }
        return false
    }

    private fun isDash(value: String?): Boolean {
        val path = value?.substringBefore('?')?.lowercase(Locale.ROOT).orEmpty()
        return path.endsWith(".mpd") || value?.startsWith("dash://", ignoreCase = true) == true
    }

    private fun isTransient(value: String): Boolean {
        val scheme = value.substringBefore("://", missingDelimiterValue = "").lowercase(Locale.ROOT)
        return scheme in TRANSIENT_SCHEMES ||
            value.startsWith("magnet:", ignoreCase = true) ||
            value.substringBefore('?').endsWith(".torrent", ignoreCase = true)
    }

    private val ALLOWED_HTTP_HEADERS = setOf(
        "user-agent",
        "accept",
        "accept-encoding",
        "accept-language",
        "cache-control",
        "origin",
        "pragma",
        "referer",
    )
    private val SENSITIVE_QUERY_NAMES = setOf(
        "access_token",
        "api_key",
        "auth",
        "authorization",
        "credential",
        "credentials",
        "key",
        "policy",
        "signature",
        "signed",
        "sig",
        "token",
    )
    private val SENSITIVE_QUERY_PREFIXES = setOf(
        "x-amz-",
        "x-goog-",
    )
    private val TRANSIENT_SCHEMES = setOf("blob", "data", "fd", "fdclose", "edl", "memory", "lavf", "ytdl")
    private const val MAX_HEADER_VALUE_LENGTH = 8_192
}

internal object SceneFfmpegArguments {
    fun animatedAvif(
        input: SceneVideoInputSpec,
        acquiredInputValue: String,
        range: SceneTimeRange,
        outputFile: String,
        encoderName: String,
        tlsCaFile: String? = null,
    ): Array<String> {
        require(encoderName.isNotBlank()) { "AV1 encoder name must not be blank" }
        return buildList {
            addInputOptions(input, tlsCaFile)
            add("-ss")
            add(range.startSeconds.toFfmpegSeconds())
            add("-i")
            add(acquiredInputValue)
            add("-map")
            add(input.videoMapSelector())
            add("-an")
            add("-sn")
            add("-dn")
            add("-t")
            add(range.durationSeconds.toFfmpegSeconds())
            add("-vf")
            add(FRAME_FILTER)
            add("-frames:v")
            add(MAX_FRAME_COUNT.toString())
            add("-c:v")
            add("av1_mediacodec")
            add("-codec_name")
            add(encoderName)
            add("-bitrate_mode")
            add("cq")
            add("-global_quality")
            add("35")
            add("-ndk_codec")
            add("1")
            add("-pix_fmt")
            add("yuv420p")
            add("-loop")
            add("0")
            add("-f")
            add("avif")
            add("-y")
            add(outputFile)
        }.toTypedArray()
    }

    fun videoProbe(
        input: SceneVideoInputSpec,
        acquiredInputValue: String,
        tlsCaFile: String? = null,
    ): Array<String> {
        return buildList {
            addInputOptions(input, tlsCaFile)
            add("-v")
            add("error")
            add("-select_streams")
            add(input.videoProbeSelector())
            add("-show_entries")
            add("stream=pix_fmt,color_transfer,color_primaries,bits_per_raw_sample,profile:stream_side_data")
            add("-of")
            add("default=noprint_wrappers=1")
            add(acquiredInputValue)
        }.toTypedArray()
    }

    fun audioProbe(
        input: SceneVideoInputSpec,
        acquiredInputValue: String,
        tlsCaFile: String? = null,
    ): Array<String> {
        return buildList {
            addInputOptions(input, tlsCaFile)
            add("-v")
            add("error")
            add("-select_streams")
            add(input.audioProbeSelector())
            add("-show_entries")
            add("stream=codec_type,codec_name:stream_side_data")
            add("-of")
            add("default=noprint_wrappers=1")
            add(acquiredInputValue)
        }.toTypedArray()
    }

    fun allAudioProbe(
        input: SceneVideoInputSpec,
        acquiredInputValue: String,
        tlsCaFile: String? = null,
    ): Array<String> {
        return buildList {
            addInputOptions(input, tlsCaFile)
            add("-v")
            add("error")
            add("-select_streams")
            add("a")
            add("-show_entries")
            add("stream=index,codec_type,codec_name:stream_side_data")
            add("-of")
            add("default=noprint_wrappers=0")
            add(acquiredInputValue)
        }.toTypedArray()
    }

    fun audioDiscoveryProbe(
        input: SceneVideoInputSpec,
        acquiredInputValue: String,
        tlsCaFile: String? = null,
    ): Array<String> {
        return buildList {
            addInputOptions(input, tlsCaFile, restrictDecoders = false)
            add("-v")
            add("error")
            add("-select_streams")
            add("a")
            add("-show_entries")
            add("stream=index,codec_type,codec_name:stream_side_data")
            add("-of")
            add("default=noprint_wrappers=0")
            add(acquiredInputValue)
        }.toTypedArray()
    }

    fun sentenceAudio(
        input: SceneVideoInputSpec,
        acquiredInputValue: String,
        range: SceneTimeRange,
        outputFile: String,
        tlsCaFile: String? = null,
    ): Array<String> {
        return buildList {
            addInputOptions(input, tlsCaFile)
            add("-ss")
            add(range.startSeconds.toFfmpegSeconds())
            add("-i")
            add(acquiredInputValue)
            add("-map")
            add(input.audioSelector())
            add("-vn")
            add("-sn")
            add("-dn")
            add("-t")
            add(range.durationSeconds.toFfmpegSeconds())
            add("-c:a")
            add("aac")
            add("-b:a")
            add("128k")
            add("-y")
            add(outputFile)
        }.toTypedArray()
    }

    private fun MutableList<String>.addInputOptions(
        input: SceneVideoInputSpec,
        tlsCaFile: String?,
        restrictDecoders: Boolean = true,
    ) {
        if (restrictDecoders) {
            add("-codec_whitelist")
            add(ALLOWED_INPUT_DECODERS)
        }
        if (input.kind == SceneVideoInputKind.REMOTE_HTTP) {
            require(!tlsCaFile.isNullOrBlank()) { "Remote scene input requires a CA bundle" }
            add("-tls_verify")
            add("1")
            add("-ca_file")
            add(tlsCaFile)
            add("-protocol_whitelist")
            add(REMOTE_PROTOCOLS)
            add("-rw_timeout")
            add(REMOTE_IO_TIMEOUT_MICROSECONDS)
        }
        if (input.headers.isNotEmpty()) {
            add("-headers")
            add(input.headers.joinToString(separator = "") { (name, value) -> "$name: $value\r\n" })
        }
    }

    private fun SceneVideoInputSpec.videoMapSelector(): String {
        return videoStreamIndex?.let { "0:$it" } ?: "0:v:0"
    }

    private fun SceneVideoInputSpec.videoProbeSelector(): String {
        return videoStreamIndex?.toString() ?: "v:0"
    }

    private fun SceneVideoInputSpec.audioSelector(): String {
        return audioStreamIndex?.let { "0:$it" } ?: "0:a:0"
    }

    private fun SceneVideoInputSpec.audioProbeSelector(): String {
        return audioStreamIndex?.toString() ?: "a:0"
    }

    private fun Double.toFfmpegSeconds(): String {
        return String.format(Locale.ROOT, "%.6f", this).trimEnd('0').trimEnd('.')
    }

    internal const val FRAME_FILTER =
        "fps=8,scale=w='min(640,iw)':h='min(640,ih)':force_original_aspect_ratio=decrease:force_divisible_by=16,setsar=1"
    internal const val FRAME_RATE = 8.0
    internal const val MAX_FRAME_COUNT = 80
    private const val REMOTE_PROTOCOLS = "http,https,tls,tcp,crypto"
    private const val REMOTE_IO_TIMEOUT_MICROSECONDS = "15000000"
    internal const val ALLOWED_INPUT_DECODERS =
        "aac,ac3,alac,av1,dca,eac3,ffv1,flac,h263,h264,hevc,libdav1d,mjpeg,mp3,mp3float,mpeg1video," +
            "mpeg2video,mpeg4,opus,pcm_f32le,pcm_s16le,pcm_s24le,pcm_s32le,png,prores,theora,truehd," +
            "vorbis,vp8,vp9"
}
