package eu.kanade.tachiyomi.ui.player

import android.content.Context
import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioPreparation
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.sentenceaudio.SentenceAudioCaptureRequest
import eu.kanade.tachiyomi.ui.player.sentenceaudio.SentenceAudioMpvSnapshot
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerMediaCaptureServiceTest {

    @Test
    fun `OCR request freezes selection time padding and full snapshot before provider runs`() = runTest {
        var time = 10.0
        var padding = 2.0
        var video = video("https://media.example/original.m3u8", "original")
        var mpv = snapshot("https://media.example/playable.m3u8", 7, 2, 3, false, null, true)
        var received: SentenceAudioCaptureRequest? = null
        val service = PlayerMediaCaptureService(
            context = mockk<Context>(relaxed = true),
            cachePath = "cache",
            getVideo = { video },
            getSource = { null },
            getTimeSeconds = { time },
            getOcrPaddingSeconds = { padding },
            readMpvSnapshot = { mpv },
            prepareSentenceAudioOverride = { request ->
                received = request
                AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.UNKNOWN)
            },
        )

        val request = service.createVideoOcrAudioMediaRequest()

        time = 99.0
        padding = 20.0
        video = video("https://media.example/changed.m3u8", "changed")
        mpv = snapshot("https://media.example/changed-playable.m3u8", 9, 4, 8, true, "https://media.example/external.m4a", false)
        request.sentenceAudioProvider!!.prepare()

        assertEquals(
            SentenceAudioCaptureRequest(
                inputSnapshot = eu.kanade.tachiyomi.ui.player.sentenceaudio.SentenceAudioInputSnapshot(
                    originalVideoValue = "https://media.example/original.m3u8",
                    playableValue = "https://media.example/playable.m3u8",
                    headers = listOf("Referer" to "original"),
                    ffmpegStreamArgs = listOf("-user_agent" to "original-agent"),
                    ffmpegVideoArgs = listOf("-rw_timeout" to "1000"),
                    seekable = true,
                    selectedAudioId = 7,
                    audioTrackCount = 2,
                    selectedAudioFfmpegIndex = 3,
                    selectedAudioIsExternal = false,
                    selectedExternalAudioValue = null,
                ),
                startSeconds = 8.0,
                endSeconds = 12.0,
            ),
            received,
        )
    }

    @Test
    fun `OCR animated scene request freezes selection time before capture runs`() {
        var time = 10.0
        var padding = 2.0
        var video = video("https://media.example/original.m3u8", "original")
        var mpvPath = "https://media.example/playable.m3u8"
        val service = PlayerMediaCaptureService(
            context = mockk<Context>(relaxed = true),
            cachePath = "cache",
            getVideo = { video },
            getSource = { null },
            getTimeSeconds = { time },
            getOcrPaddingSeconds = { padding },
            readMpvSnapshot = { snapshot("https://media.example/playable.m3u8", 7, 1, 3, false, null, true) },
            readMpvVideoPath = { mpvPath },
        )

        val request = service.createVideoOcrAnimatedSceneRequest()

        time = 99.0
        padding = 20.0
        video = video("https://media.example/changed.m3u8", "changed")
        mpvPath = "https://media.example/changed-playable.m3u8"

        assertEquals(8.0, request.startSeconds)
        assertEquals(12.0, request.endSeconds)
        assertEquals("https://media.example/playable.m3u8", request.input?.source)
        assertEquals("https://media.example/original.m3u8", request.input?.videoUrl)
        assertEquals(listOf("Referer" to "original"), request.input?.headers)
    }

    @Test
    fun `subtitle request preserves missing timing for typed provider diagnostics`() {
        val request = service().createSubtitleAudioMediaRequest(null, 12.0)

        assertNotNull(request.sentenceAudioProvider)
    }

    @Test
    fun `subtitle request preserves ambiguous selected track snapshot`() = runTest {
        var received: SentenceAudioCaptureRequest? = null
        val request = service(
            mpv = snapshot("https://media.example/playable.m3u8", 7, 2, null, false, null, true),
            prepare = { received = it; AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.UNKNOWN) },
        ).createSubtitleAudioMediaRequest(1.0, 2.0)

        request.sentenceAudioProvider!!.prepare()

        assertEquals(7, received?.inputSnapshot?.selectedAudioId)
        assertEquals(2, received?.inputSnapshot?.audioTrackCount)
        assertNull(received?.inputSnapshot?.selectedAudioFfmpegIndex)
    }

    private fun service(
        mpv: SentenceAudioMpvSnapshot = snapshot("https://media.example/playable.m3u8", 7, 1, 3, false, null, true),
        prepare: suspend (SentenceAudioCaptureRequest) -> AnkiSentenceAudioPreparation = {
            AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.UNKNOWN)
        },
    ) = PlayerMediaCaptureService(
        context = mockk<Context>(relaxed = true),
        cachePath = "cache",
        getVideo = { video("https://media.example/original.m3u8", "original") },
        getSource = { null },
        getTimeSeconds = { 10.0 },
        getOcrPaddingSeconds = { 2.0 },
        readMpvSnapshot = { mpv },
        prepareSentenceAudioOverride = prepare,
    )

    private fun video(url: String, referer: String) = Video(
        videoUrl = url,
        headers = headersOf("Referer", referer),
        ffmpegStreamArgs = listOf("-user_agent" to "$referer-agent"),
        ffmpegVideoArgs = listOf("-rw_timeout" to "1000"),
    )

    private fun snapshot(
        playable: String,
        selectedAudioId: Int,
        trackCount: Int,
        ffmpegIndex: Int?,
        external: Boolean,
        externalValue: String?,
        seekable: Boolean?,
    ) = SentenceAudioMpvSnapshot(
        playableValue = playable,
        selectedAudioId = selectedAudioId,
        selectedExternalAudioValue = externalValue,
        selectedAudioIsExternal = external,
        audioTrackCount = trackCount,
        selectedAudioFfmpegIndex = ffmpegIndex,
        seekable = seekable,
    )
}
