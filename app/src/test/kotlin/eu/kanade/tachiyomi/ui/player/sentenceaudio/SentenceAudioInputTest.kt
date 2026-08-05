package eu.kanade.tachiyomi.ui.player.sentenceaudio

import chimahon.anki.AnkiSentenceAudioFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI

class SentenceAudioInputTest {
    @TempDir
    lateinit var temporaryDirectory: File

    @Test
    fun `local file SAF and public HLS are supported`() {
        assertNotNull(resolve(snapshot("/video/episode.mkv")))
        assertNotNull(resolve(snapshot("file:///video/episode.mkv")))
        assertNotNull(resolve(snapshot("content://media/external/video/1")))
        val hls = requireNotNull(resolve(snapshot("https://media.example/episode.m3u8?keyframe=1", headers = allowedHeaders)))
        assertEquals(SentenceAudioInputKind.REMOTE_HTTP, hls.kind)
        assertEquals(allowedHeaders, hls.headers)
    }

    @Test
    fun `sensitive request metadata and credential query parameters are rejected`() {
        listOf(
            snapshot("https://media.example/video.mp4", headers = listOf("Authorization" to "Bearer secret")),
            snapshot("https://media.example/video.mp4?access_token=secret"),
            snapshot("https://media.example/video.mp4?api_key=secret"),
            snapshot("https://user:password@media.example/video.mp4"),
        ).forEach { assertNull(resolve(it)) }
    }

    @Test
    fun `media URL signature parameters are supported only for YouTube CDN`() {
        val input = resolve(snapshot("https://googlevideo.com/videoplayback?expire=123&sig=signatureValue&id=abc"))
        assertNotNull(input)
        assertEquals(SentenceAudioInputKind.REMOTE_HTTP, input?.kind)
        assertNull(resolve(snapshot("https://media.example/video.mp4?sig=signatureValue")))
    }

    @Test
    fun `sanitizeForLog redacts sensitive tokens and preserves path`() {
        val url = "https://googlevideo.com/videoplayback?expire=123&sig=secretKey123&id=abc"
        val sanitized = SentenceAudioInputResolver.sanitizeForLog(url)
        assertTrue(sanitized.contains("expire=123"))
        assertTrue(sanitized.contains("sig=[REDACTED]"))
        assertFalse(sanitized.contains("secretKey123"))
    }

    @Test
    fun `DASH transient and unsafe inputs are rejected`() {
        listOf(
            snapshot("https://media.example/manifest.mpd"),
            snapshot("dash://manifest"),
            snapshot("blob:opaque"),
            snapshot("data:audio/aac;base64,abc"),
            snapshot("https://media.example/video.mp4", ffmpegStreamArgs = listOf("-referer" to "https://private.example")),
            snapshot("https://media.example/video.mp4", ffmpegVideoArgs = listOf("-headers" to "private")),
        ).forEach { assertNull(resolve(it)) }
    }

    @Test
    fun `resolved inputs retain whether export uses original playable or external audio`() {
        assertEquals(SentenceAudioInputOrigin.ORIGINAL_VIDEO, requireNotNull(resolve(snapshot("/video/original.mkv", playableValue = "/video/playable.mkv"))).origin)
        assertEquals(SentenceAudioInputOrigin.PLAYABLE_VIDEO, requireNotNull(resolve(snapshot("", playableValue = "/video/playable.mkv"))).origin)
        assertEquals(SentenceAudioInputOrigin.EXTERNAL_AUDIO, requireNotNull(resolve(snapshot("/video/original.mkv", selectedAudioIsExternal = true, selectedExternalAudioValue = "/audio/external.m4a"))).origin)
        assertEquals(SentenceAudioInputOrigin.ORIGINAL_VIDEO, requireNotNull(resolve(snapshot("/video/original.mkv", selectedAudioIsExternal = true, selectedExternalAudioValue = null))).origin)
    }

    @Test
    fun `playable fallback resolution distinguishes usable missing same and unsafe sources`() {
        val originalValue = "https://media.example/original.m3u8"
        val original = requireNotNull(resolve(snapshot(originalValue)))
        val available = SentenceAudioInputResolver.resolvePlayableFallback(snapshot(originalValue, playableValue = "https://media.example/playable.m3u8"), original)
        val missing = SentenceAudioInputResolver.resolvePlayableFallback(snapshot(originalValue, playableValue = ""), original)
        val same = SentenceAudioInputResolver.resolvePlayableFallback(snapshot(originalValue, playableValue = originalValue), original)
        val unsafe = SentenceAudioInputResolver.resolvePlayableFallback(snapshot(originalValue, playableValue = "https://media.example/playable.m3u8?token=secret"), original)
        assertEquals("https://media.example/playable.m3u8", (available as SentenceAudioPlayableFallbackResolution.Available).input.value)
        assertEquals(SentenceAudioPlayableFallbackResolution.Missing, missing)
        assertEquals(SentenceAudioPlayableFallbackResolution.SameAsOriginal, same)
        assertEquals(SentenceAudioPlayableFallbackResolution.Unavailable, unsafe)
    }

    @Test
    fun `selected track without FFmpeg index uses default only when one audio track exists`() {
        val input = SentenceAudioInputResolver.resolveForCapture(snapshot("/video/episode.mkv", selectedAudioId = 7, audioTrackCount = 1))
        assertTrue(input is SentenceAudioInputResolution.Available)
        assertNull((input as SentenceAudioInputResolution.Available).input.audioStreamIndex)
    }

    @Test
    fun `selected track without FFmpeg index and multiple tracks is mapping unavailable`() {
        assertEquals(
            SentenceAudioInputResolution.Unavailable(AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE),
            SentenceAudioInputResolver.resolveForCapture(snapshot("/video/episode.mkv", selectedAudioId = 7, audioTrackCount = 2)),
        )
    }

    @Test
    fun `existing local path is seekable when MPV seekable is null`() {
        val file = File(temporaryDirectory, "episode.mkv").apply { createNewFile() }
        assertTrue(resolveSeekability(null, file.absolutePath))
        assertFalse(resolveSeekability(null, File(temporaryDirectory, "missing.mkv").absolutePath))
    }

    @Test
    fun `file URI from File toURI is seekable when MPV seekable is null`() {
        val file = File(temporaryDirectory, "episode with spaces.mkv").apply { createNewFile() }
        val uri = file.toURI()
        assertTrue(uri.toString().startsWith("file:/"))
        assertTrue(resolveSeekability(null, uri.toString()))
    }

    @Test
    fun `triple slash file URI is seekable when MPV seekable is null including external audio`() {
        val file = File(temporaryDirectory, "episode.mkv").apply { createNewFile() }
        val fileUriFromFile = file.toURI()
        val tripleSlashFileUri = URI("file:///" + fileUriFromFile.rawPath.removePrefix("/"))
        assertTrue(fileUriFromFile.toString().startsWith("file:/"))
        assertTrue(tripleSlashFileUri.toString().startsWith("file:///"))
        assertTrue(resolveSeekability(null, file.absolutePath))
        assertTrue(resolveSeekability(null, fileUriFromFile.toString()))
        assertTrue(resolveSeekability(null, tripleSlashFileUri.toString()))
        assertNotNull(resolve(snapshot(file.absolutePath, seekable = resolveSeekability(null, file.absolutePath))))
        assertNotNull(resolve(snapshot(file.absolutePath, seekable = resolveSeekability(null, file.absolutePath), selectedAudioIsExternal = true, selectedExternalAudioValue = tripleSlashFileUri.toString())))
    }

    @Test
    fun `all audio commands restrict decoders without breaking ordinary media`() {
        val input = requireNotNull(resolve(snapshot("https://media.example/video.mp4")))
        val commands = listOf(
            SentenceAudioFfmpegArguments.audioProbe(input, input.value, "/files/cacert.pem"),
            SentenceAudioFfmpegArguments.allAudioProbe(input, input.value, "/files/cacert.pem"),
            SentenceAudioFfmpegArguments.sentenceAudio(input, input.value, 1.25, 2.25, "/cache/audio.m4a", "/files/cacert.pem"),
        )
        commands.forEach { command ->
            val args = command.toList()
            val whitelist = args[args.indexOf("-codec_whitelist") + 1].split(',')
            assertTrue(whitelist.containsAll(listOf("h264", "hevc", "aac", "av1", "libdav1d")))
            assertFalse("magicyuv" in whitelist)
            assertTrue(args.indexOf("-codec_whitelist") < args.lastIndex)
        }
    }

    @Test
    fun `audio discovery probe omits the decoder whitelist and only reads stream metadata`() {
        val input = requireNotNull(resolve(snapshot("https://media.example/video.mp4")))
        val arguments = SentenceAudioFfmpegArguments.audioDiscoveryProbe(input, input.value, "/files/cacert.pem").toList()
        assertFalse("-codec_whitelist" in arguments)
        assertEquals("a", arguments[arguments.indexOf("-select_streams") + 1])
        assertEquals("stream=index,codec_type,codec_name:stream_side_data", arguments[arguments.indexOf("-show_entries") + 1])
    }

    @Test
    fun `sentence audio maps the frozen selected stream`() {
        val input = requireNotNull(resolve(snapshot("https://media.example/video.mp4", selectedAudioFfmpegIndex = 3)))
        val arguments = SentenceAudioFfmpegArguments.sentenceAudio(input, input.value, 1.25, 2.25, "/cache/audio.m4a", "/files/cacert.pem").toList()
        assertEquals("0:3", arguments[arguments.indexOf("-map") + 1])
    }

    private fun resolve(snapshot: SentenceAudioInputSnapshot): SentenceAudioInputSpec? = SentenceAudioInputResolver.resolve(snapshot)

    private fun snapshot(
        value: String,
        playableValue: String? = value,
        headers: List<Pair<String, String>> = emptyList(),
        ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
        ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
        seekable: Boolean? = true,
        selectedAudioId: Int? = null,
        audioTrackCount: Int = 1,
        selectedAudioFfmpegIndex: Int? = null,
        selectedAudioIsExternal: Boolean = false,
        selectedExternalAudioValue: String? = null,
    ) = SentenceAudioInputSnapshot(value, playableValue, headers, ffmpegStreamArgs, ffmpegVideoArgs, seekable, selectedAudioId, audioTrackCount, selectedAudioFfmpegIndex, selectedAudioIsExternal, selectedExternalAudioValue)

    private companion object {
        val allowedHeaders = listOf("User-Agent" to "Chimahon", "Accept" to "*/*")
    }
}
