package eu.kanade.tachiyomi.ui.player.sentenceaudio

import chimahon.anki.AnkiSentenceAudioFailure
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal enum class SentenceAudioInputKind { LOCAL_FILE, CONTENT_URI, REMOTE_HTTP }

internal enum class SentenceAudioInputOrigin { ORIGINAL_VIDEO, PLAYABLE_VIDEO, EXTERNAL_AUDIO }

internal data class SentenceAudioInputSpec(
    val value: String,
    val kind: SentenceAudioInputKind,
    val headers: List<Pair<String, String>>,
    val audioStreamIndex: Int? = null,
    val origin: SentenceAudioInputOrigin,
)

internal data class SentenceAudioInputSnapshot(
    val originalVideoValue: String,
    val playableValue: String?,
    val headers: List<Pair<String, String>>,
    val ffmpegStreamArgs: List<Pair<String, String>>,
    val ffmpegVideoArgs: List<Pair<String, String>>,
    val seekable: Boolean?,
    val selectedAudioId: Int?,
    val audioTrackCount: Int,
    val selectedAudioFfmpegIndex: Int?,
    val selectedAudioIsExternal: Boolean,
    val selectedExternalAudioValue: String?,
)

internal sealed interface SentenceAudioInputResolution {
    data class Available(val input: SentenceAudioInputSpec) : SentenceAudioInputResolution
    data class Unavailable(val failure: AnkiSentenceAudioFailure) : SentenceAudioInputResolution
}

internal sealed interface SentenceAudioPlayableFallbackResolution {
    data class Available(val input: SentenceAudioInputSpec) : SentenceAudioPlayableFallbackResolution
    data object Missing : SentenceAudioPlayableFallbackResolution
    data object SameAsOriginal : SentenceAudioPlayableFallbackResolution
    data object Unavailable : SentenceAudioPlayableFallbackResolution
}

internal fun resolveSeekability(mpvSeekable: Boolean?, originalVideoValue: String): Boolean =
    mpvSeekable ?: stableLocalFile(originalVideoValue)

private fun stableLocalFile(value: String): Boolean {
    val fileUri = runCatching { URI(value) }.getOrNull()
        ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
    return if (fileUri != null) {
        runCatching { File(fileUri).isFile }.getOrDefault(false)
    } else {
        File(value).isFile
    }
}

internal object SentenceAudioInputResolver {
    fun resolve(snapshot: SentenceAudioInputSnapshot): SentenceAudioInputSpec? =
        when (val resolution = resolveForCapture(snapshot)) {
            is SentenceAudioInputResolution.Available -> resolution.input
            is SentenceAudioInputResolution.Unavailable -> null
        }

    fun resolveForCapture(snapshot: SentenceAudioInputSnapshot): SentenceAudioInputResolution {
        if (snapshot.selectedAudioIsExternal) {
            return resolveExternalAudio(snapshot)
        }
        if (snapshot.selectedAudioId != null && snapshot.selectedAudioFfmpegIndex == null && snapshot.audioTrackCount != 1) {
            return SentenceAudioInputResolution.Unavailable(AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE)
        }
        return resolveOriginalVideo(snapshot)
    }

    fun resolvePlayableFallback(
        snapshot: SentenceAudioInputSnapshot,
        original: SentenceAudioInputSpec,
    ): SentenceAudioPlayableFallbackResolution {
        if (snapshot.playableValue.isNullOrBlank()) return SentenceAudioPlayableFallbackResolution.Missing
        val playable = resolveValue(
            value = snapshot.playableValue,
            snapshot = snapshot,
            origin = SentenceAudioInputOrigin.PLAYABLE_VIDEO,
        ) ?: return SentenceAudioPlayableFallbackResolution.Unavailable
        return if (playable.value == original.value && playable.kind == original.kind && playable.headers == original.headers) {
            SentenceAudioPlayableFallbackResolution.SameAsOriginal
        } else {
            SentenceAudioPlayableFallbackResolution.Available(playable)
        }
    }

    private fun resolveOriginalVideo(snapshot: SentenceAudioInputSnapshot): SentenceAudioInputResolution {
        if (snapshot.seekable != true || snapshot.ffmpegStreamArgs.isNotEmpty() || snapshot.ffmpegVideoArgs.isNotEmpty()) {
            return SentenceAudioInputResolution.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
        }
        val original = snapshot.originalVideoValue.takeIf(String::isNotBlank)
        val value = original ?: snapshot.playableValue
        val origin = if (original != null) SentenceAudioInputOrigin.ORIGINAL_VIDEO else SentenceAudioInputOrigin.PLAYABLE_VIDEO
        return resolveValue(value, snapshot, origin)?.let(SentenceAudioInputResolution::Available)
            ?: SentenceAudioInputResolution.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
    }

    private fun resolveExternalAudio(snapshot: SentenceAudioInputSnapshot): SentenceAudioInputResolution {
        if (snapshot.seekable != true || snapshot.ffmpegStreamArgs.isNotEmpty() || snapshot.ffmpegVideoArgs.isNotEmpty()) {
            return SentenceAudioInputResolution.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
        }
        return resolveValue(snapshot.selectedExternalAudioValue ?: snapshot.originalVideoValue, snapshot, SentenceAudioInputOrigin.EXTERNAL_AUDIO)
            ?.let(SentenceAudioInputResolution::Available)
            ?: SentenceAudioInputResolution.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
    }

    private fun resolveValue(
        value: String?,
        snapshot: SentenceAudioInputSnapshot,
        origin: SentenceAudioInputOrigin,
    ): SentenceAudioInputSpec? {
        val raw = value?.takeIf(String::isNotBlank) ?: return null
        if (isDash(raw) || isTransient(raw)) return null
        val normalized = normalizeInput(raw) ?: return null
        val headers = when (normalized.second) {
            SentenceAudioInputKind.REMOTE_HTTP -> validateRemoteInput(normalized.first, snapshot.headers) ?: return null
            SentenceAudioInputKind.LOCAL_FILE, SentenceAudioInputKind.CONTENT_URI -> emptyList()
        }
        return SentenceAudioInputSpec(
            value = normalized.first,
            kind = normalized.second,
            headers = headers,
            audioStreamIndex = snapshot.selectedAudioFfmpegIndex?.takeIf { it >= 0 },
            origin = origin,
        )
    }

    private fun validateRemoteInput(value: String, headers: List<Pair<String, String>>): List<Pair<String, String>>? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.userInfo.isNullOrBlank() || uri.host.isNullOrBlank() || hasRejectedValidationQuery(uri.rawQuery.orEmpty())) return null
        return headers.takeIf { it.all(::isAllowedHeader) }
    }

    private fun normalizeInput(value: String): Pair<String, SentenceAudioInputKind>? = when {
        value.startsWith("content://", ignoreCase = true) -> value to SentenceAudioInputKind.CONTENT_URI
        value.startsWith("file:", ignoreCase = true) -> runCatching { File(URI(value)).absolutePath }.getOrNull()
            ?.takeIf(String::isNotBlank)?.let { it to SentenceAudioInputKind.LOCAL_FILE }
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value to SentenceAudioInputKind.REMOTE_HTTP
        value.startsWith("/") || File(value).isAbsolute -> value to SentenceAudioInputKind.LOCAL_FILE
        else -> null
    }

    private fun isAllowedHeader(header: Pair<String, String>): Boolean {
        val (name, value) = header
        return name.lowercase(Locale.ROOT) in allowedHttpHeaders && value.length <= maxHeaderValueLength &&
            value.none { it == '\u0000' || it == '\r' || it == '\n' || (it.code < 0x20 && it != '\t') }
    }

    private fun hasRejectedValidationQuery(query: String): Boolean = query.split('&').any { parameter ->
        if (parameter.isBlank()) return@any false
        val name = runCatching { URLDecoder.decode(parameter.substringBefore('='), StandardCharsets.UTF_8.name()).lowercase(Locale.ROOT) }.getOrNull()
            ?: return true
        name in rejectedValidationQueryNames || sensitiveQueryPrefixes.any(name::startsWith)
    }

    fun sanitizeForLog(url: String): String {
        if (url.isBlank()) return url
        val queryStart = url.indexOf('?')
        val pathPart = if (queryStart >= 0) url.substring(0, queryStart) else url
        val queryPart = if (queryStart >= 0) url.substring(queryStart + 1) else null

        val redactedQuery = queryPart?.split('&')?.joinToString("&") { param ->
            val key = param.substringBefore('=')
            val decodedKey = runCatching {
                URLDecoder.decode(key, StandardCharsets.UTF_8.name()).lowercase(Locale.ROOT)
            }.getOrDefault(key.lowercase(Locale.ROOT))
            if (decodedKey in sensitiveLogQueryNames || sensitiveQueryPrefixes.any(decodedKey::startsWith)) {
                "$key=[REDACTED]"
            } else {
                param
            }
        }

        return if (redactedQuery != null) "$pathPart?$redactedQuery" else pathPart
    }

    private fun isDash(value: String) = value.substringBefore('?').endsWith(".mpd", ignoreCase = true) || value.startsWith("dash://", ignoreCase = true)
    private fun isTransient(value: String): Boolean {
        val scheme = value.substringBefore("://", "").lowercase(Locale.ROOT)
        return scheme in transientSchemes || value.startsWith("magnet:", ignoreCase = true) || value.substringBefore('?').endsWith(".torrent", ignoreCase = true)
    }

    private val allowedHttpHeaders = setOf("user-agent", "accept", "accept-encoding", "accept-language", "cache-control", "origin", "pragma", "referer")
    private val rejectedValidationQueryNames = setOf("access_token", "api_key", "auth", "authorization", "credential", "credentials", "key", "policy", "token")
    private val sensitiveLogQueryNames = setOf("access_token", "api_key", "auth", "authorization", "credential", "credentials", "key", "policy", "signature", "signed", "sig", "token")
    private val sensitiveQueryPrefixes = setOf("x-amz-", "x-goog-")
    private val transientSchemes = setOf("blob", "data", "fd", "fdclose", "edl", "memory", "lavf", "ytdl")
    private const val maxHeaderValueLength = 8_192
}

internal object SentenceAudioFfmpegArguments {
    fun audioProbe(input: SentenceAudioInputSpec, acquiredInputValue: String, tlsCaFile: String? = null): Array<String> = probe(input, acquiredInputValue, input.audioStreamIndex?.toString() ?: "a:0", "stream=codec_type,codec_name:stream_side_data", tlsCaFile, true)
    fun allAudioProbe(input: SentenceAudioInputSpec, acquiredInputValue: String, tlsCaFile: String? = null): Array<String> = probe(input, acquiredInputValue, "a", "stream=index,codec_type,codec_name:stream_side_data", tlsCaFile, true)
    fun audioDiscoveryProbe(input: SentenceAudioInputSpec, acquiredInputValue: String, tlsCaFile: String? = null): Array<String> = probe(input, acquiredInputValue, "a", "stream=index,codec_type,codec_name:stream_side_data", tlsCaFile, false)
    private fun probe(input: SentenceAudioInputSpec, acquired: String, selector: String, entries: String, ca: String?, restrict: Boolean) = buildList {
        addInputOptions(input, ca, restrict); add("-v"); add("error"); add("-select_streams"); add(selector); add("-show_entries"); add(entries); add("-of"); add("default=noprint_wrappers=1"); add(acquired)
    }.toTypedArray()
    fun sentenceAudio(input: SentenceAudioInputSpec, acquired: String, start: Double, end: Double, output: String, tlsCaFile: String? = null) = buildList {
        addInputOptions(input, tlsCaFile); add("-ss"); add(start.seconds()); add("-i"); add(acquired); add("-map"); add(input.audioStreamIndex?.let { "0:$it" } ?: "0:a:0"); add("-vn"); add("-sn"); add("-dn"); add("-t"); add((end - start).seconds()); add("-c:a"); add("aac"); add("-b:a"); add("128k"); add("-y"); add(output)
    }.toTypedArray()
    private fun MutableList<String>.addInputOptions(input: SentenceAudioInputSpec, tlsCaFile: String?, restrict: Boolean = true) {
        if (restrict) { add("-codec_whitelist"); add(ALLOWED_INPUT_DECODERS) }
        if (input.kind == SentenceAudioInputKind.REMOTE_HTTP) { require(!tlsCaFile.isNullOrBlank()); add("-protocol_whitelist"); add("http,https,tls,tcp,crypto"); add("-rw_timeout"); add("15000000") }
        if (input.headers.isNotEmpty()) { add("-headers"); add(input.headers.joinToString("") { "${it.first}: ${it.second}\r\n" }) }
    }
    private fun Double.seconds() = String.format(Locale.ROOT, "%.6f", this).trimEnd('0').trimEnd('.')
    internal const val ALLOWED_INPUT_DECODERS = "aac,ac3,alac,av1,dca,eac3,ffv1,flac,h263,h264,hevc,libdav1d,mjpeg,mp3,mp3float,mpeg1video,mpeg2video,mpeg4,opus,pcm_f32le,pcm_s16le,pcm_s24le,pcm_s32le,png,prores,theora,truehd,vorbis,vp8,vp9"
}
