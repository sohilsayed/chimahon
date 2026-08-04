package eu.kanade.tachiyomi.ui.player.controls

import chimahon.anki.AnkiMediaWarning
import chimahon.anki.AnkiSentenceAudioDiagnostic
import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioInputSource
import chimahon.anki.AnkiSentenceAudioPlayableFallback
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerSentenceAudioWarningsTest {

    @Test
    fun `original source with missing playable fallback uses the specific warning key`() {
        val warning = AnkiMediaWarning.SentenceAudioGenerationFailed(
            AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND,
            AnkiSentenceAudioDiagnostic(
                AnkiSentenceAudioInputSource.ORIGINAL_VIDEO,
                AnkiSentenceAudioPlayableFallback.MISSING,
            ),
        )

        assertEquals(
            PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_MISSING,
            warning.toPlayerSentenceAudioWarningKey(),
        )
    }

    @Test
    fun `input-specific failures retain their source diagnostic`() {
        assertEquals(
            PlayerSentenceAudioWarningKey.PROBE_FAILED_PLAYABLE,
            warning(AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED, AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO)
                .toPlayerSentenceAudioWarningKey(),
        )
        assertEquals(
            PlayerSentenceAudioWarningKey.CODEC_RESTRICTED_EXTERNAL,
            warning(AnkiSentenceAudioFailure.AUDIO_CODEC_RESTRICTED, AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO)
                .toPlayerSentenceAudioWarningKey(),
        )
        assertEquals(
            PlayerSentenceAudioWarningKey.EXTRACTION_SOURCE_READ_FAILED_ORIGINAL,
            warning(AnkiSentenceAudioFailure.EXTRACTION_SOURCE_READ_FAILED, AnkiSentenceAudioInputSource.ORIGINAL_VIDEO)
                .toPlayerSentenceAudioWarningKey(),
        )
    }

    @Test
    fun `every sentence audio failure and storage failure maps exhaustively`() {
        AnkiSentenceAudioFailure.entries.forEach { failure ->
            warning(failure, null).toPlayerSentenceAudioWarningKey()
        }
        assertEquals(
            PlayerSentenceAudioWarningKey.STORAGE_FAILED,
            AnkiMediaWarning.SentenceAudioStorageFailed.toPlayerSentenceAudioWarningKey(),
        )
    }

    private fun warning(
        failure: AnkiSentenceAudioFailure,
        source: AnkiSentenceAudioInputSource?,
    ) = AnkiMediaWarning.SentenceAudioGenerationFailed(
        failure,
        source?.let { AnkiSentenceAudioDiagnostic(it) },
    )
}
