package eu.kanade.tachiyomi.ui.player.scene

import chimahon.anki.AnkiSentenceAudioFailure
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentenceAudioDiagnosticLogTest {
    @Test
    fun `records a redacted native failure with its sentence audio stage`() {
        val input = SceneVideoInputSpec(
            value = "https://media.example/episode.m3u8?token=secret",
            kind = SceneVideoInputKind.REMOTE_HTTP,
            headers = listOf("Authorization" to "Bearer secret"),
            audioStreamIndex = 1,
            origin = SceneVideoInputOrigin.PLAYABLE_VIDEO,
        )

        val text = SentenceAudioDiagnosticJournal.render(
            SentenceAudioDiagnosticEvent(
                stage = SentenceAudioDiagnosticStage.AUDIO_EXTRACTION,
                input = input,
                fallback = SentenceAudioDiagnosticFallback.ATTEMPTED,
                failure = AnkiSentenceAudioFailure.EXTRACTION_SOURCE_READ_FAILED,
                result = SceneCommandResult.FfmpegFailed(
                    failure = SceneFfmpegFailure.SOURCE_READ,
                    nativeDiagnostics = SceneNativeFailureDiagnostics(
                        returnCode = 1,
                        failStackTrace = null,
                        logs = "HTTP error 403 for https://media.example/episode.m3u8?token=secret\n" +
                            "Authorization: Bearer secret",
                    ),
                ),
            ),
        )

        assertTrue(text.contains("stage=AUDIO_EXTRACTION"))
        assertTrue(text.contains("input_source=PLAYABLE_VIDEO"))
        assertTrue(text.contains("input_kind=REMOTE_HTTP"))
        assertTrue(text.contains("audio_stream_index=1"))
        assertTrue(text.contains("fallback=ATTEMPTED"))
        assertTrue(text.contains("failure=EXTRACTION_SOURCE_READ_FAILED"))
        assertTrue(text.contains("return_code=1"))
        assertTrue(text.contains("HTTP error 403"))
        assertFalse(text.contains(input.value))
        assertFalse(text.contains("token=secret"))
        assertFalse(text.contains("Bearer secret"))
    }

    @Test
    fun `keeps the newest entries within its byte budget`() {
        var bytes = ByteArray(0)

        repeat(8) {
            bytes = SentenceAudioDiagnosticJournal.retain(
                existing = bytes,
                entry = SentenceAudioDiagnosticJournal.render(
                    SentenceAudioDiagnosticEvent(
                        stage = SentenceAudioDiagnosticStage.SELECTED_AUDIO_PROBE,
                        input = null,
                        fallback = SentenceAudioDiagnosticFallback.NOT_APPLICABLE,
                    ),
                ).encodeToByteArray(),
                maxBytes = 512,
            )
        }
        bytes = SentenceAudioDiagnosticJournal.retain(
            existing = bytes,
            entry = SentenceAudioDiagnosticJournal.render(
                SentenceAudioDiagnosticEvent(
                    stage = SentenceAudioDiagnosticStage.AUDIO_EXTRACTION,
                    input = null,
                    fallback = SentenceAudioDiagnosticFallback.NOT_APPLICABLE,
                ),
            ).encodeToByteArray(),
            maxBytes = 512,
        )

        assertTrue(bytes.size <= 512)
        assertTrue(bytes.decodeToString().contains("stage=AUDIO_EXTRACTION"))
    }

    @Test
    fun `reports a file writer failure instead of discarding it`() {
        var writeFailure: Throwable? = null
        val logger = StorageFolderSentenceAudioDiagnosticLogger(
            directory = { null },
            onWriteFailure = { writeFailure = it },
        )

        logger.record(
            SentenceAudioDiagnosticEvent(
                stage = SentenceAudioDiagnosticStage.REQUEST_VALIDATION,
                input = null,
                fallback = SentenceAudioDiagnosticFallback.NOT_APPLICABLE,
            ),
        )

        assertNotNull(writeFailure)
    }
}
