package eu.kanade.tachiyomi.ui.player.sentenceaudio

import eu.kanade.tachiyomi.BuildConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentenceAudioDiagnosticsTest {

    @Test
    fun `logger is enabled only for debug builds`() {
        assertEquals(
            BuildConfig.DEBUG,
            createSentenceAudioDiagnosticLogger() is LogcatSentenceAudioDiagnosticLogger,
        )
    }

    @Test
    fun `redact removes URLs credentials and local paths from native diagnostics`() {
        val raw = """
            https://example.test/video.m3u8?token=secret
            Authorization: Bearer secret-value
            /data/user/0/app.chimahon.dev/cache/source.mp4
            C:\\Users\\teera\\AppData\\Local\\cache\\source.mp4
        """.trimIndent()

        val redacted = SentenceAudioDiagnosticJournal.redact(raw)

        assertTrue(redacted.contains("<redacted-url>"))
        assertTrue(redacted.contains("Authorization: <redacted>"))
        assertTrue(redacted.contains("<redacted-path>"))
        assertFalse(redacted.contains("example.test"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("source.mp4"))
        assertFalse(redacted.contains("C:\\Users"))
    }
}
