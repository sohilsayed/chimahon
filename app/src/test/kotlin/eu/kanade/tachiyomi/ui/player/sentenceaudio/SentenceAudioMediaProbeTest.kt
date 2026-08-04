package eu.kanade.tachiyomi.ui.player.sentenceaudio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SentenceAudioMediaProbeTest {
    @Test
    fun `inspects selected audio without guessing`() {
        assertEquals(SentenceAudioMediaProbe.AudioInspection.Readable, SentenceAudioMediaProbe.inspectSelectedAudio("codec_type=audio\ncodec_name=aac"))
        assertEquals(SentenceAudioMediaProbe.AudioInspection.StreamMissing, SentenceAudioMediaProbe.inspectSelectedAudio(""))
        assertEquals(SentenceAudioMediaProbe.AudioInspection.NotAudio, SentenceAudioMediaProbe.inspectSelectedAudio("codec_type=video"))
        assertEquals(SentenceAudioMediaProbe.AudioInspection.Protected, SentenceAudioMediaProbe.inspectSelectedAudio("codec_type=audio\nside_data_type=Encryption"))
    }

    @Test
    fun `returns only audio inventory and keeps protection`() {
        val output = """
            [STREAM]
            index=2
            codec_type=audio
            [/STREAM]
            [STREAM]
            index=3
            codec_type=video
            [/STREAM]
            [STREAM]
            index=4
            codec_type=audio
            side_data_type=cenc
            [/STREAM]
        """.trimIndent()
        assertEquals(
            listOf(
                SentenceAudioMediaProbe.AudioStream(2, false),
                SentenceAudioMediaProbe.AudioStream(4, true),
            ),
            SentenceAudioMediaProbe.audioStreams(output),
        )
    }
}
