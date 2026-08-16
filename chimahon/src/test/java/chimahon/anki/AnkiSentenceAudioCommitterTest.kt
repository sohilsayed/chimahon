package chimahon.anki

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AnkiSentenceAudioCommitterTest {

    @Test
    fun `unavailable preparation stores no file and returns its generation warning`() = runTest {
        val outcome = AnkiSentenceAudioCommitter { error("must not store") }.store(
            AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND),
        )

        assertNull(outcome.filename)
        assertEquals(
            listOf(AnkiMediaWarning.SentenceAudioGenerationFailed(AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND)),
            outcome.warnings,
        )
    }

    @Test
    fun `ready preparation stores source with its stable name and extension`() = runTest {
        val source = AnkiSentenceAudioSource.fromBytes(byteArrayOf(1, 2, 3), "M4A?ignored")
        var stored: AnkiSentenceAudioSource? = null

        val outcome = AnkiSentenceAudioCommitter { value ->
            stored = value
            "${value.preferredBaseName}.${value.extension}"
        }.store(AnkiSentenceAudioPreparation.Ready(source))

        assertEquals(source, stored)
        assertEquals("${source.preferredBaseName}.m4a", outcome.filename)
        assertFalse(outcome.warnings.isNotEmpty())
    }

    @Test
    fun `storage failure becomes warning without cancelling card creation`() = runTest {
        val outcome = AnkiSentenceAudioCommitter { error("storage") }.store(
            AnkiSentenceAudioPreparation.Ready(AnkiSentenceAudioSource.fromBytes(byteArrayOf(1), "m4a")),
        )

        assertNull(outcome.filename)
        assertEquals(listOf(AnkiMediaWarning.SentenceAudioStorageFailed), outcome.warnings)
    }

    @Test
    fun `no sentence audio marker does not invoke provider`() = runTest {
        var invoked = false
        val preparation = prepareSentenceAudioForMarker(false, LazyAnkiSentenceAudioProvider {
            invoked = true
            error("must not be invoked")
        })

        assertNull(preparation)
        assertFalse(invoked)
    }

    @Test
    fun `prepared sentence audio is reused without invoking its provider`() = runTest {
        var invoked = false
        val prepared = AnkiSentenceAudioPreparation.Ready(
            AnkiSentenceAudioSource.fromBytes(byteArrayOf(9), "m4a"),
        )

        val actual = prepareSentenceAudioForMarker(
            hasSentenceAudioMarker = true,
            provider = LazyAnkiSentenceAudioProvider {
                invoked = true
                error("must not be invoked")
            },
            prepared = prepared,
        )

        assertEquals(prepared, actual)
        assertFalse(invoked)
    }

    @Test
    fun `provider exception becomes unavailable unknown`() = runTest {
        val preparation = prepareSentenceAudioForMarker(true, LazyAnkiSentenceAudioProvider {
            error("native failure")
        })

        assertEquals(
            AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.UNKNOWN),
            preparation,
        )
    }

    @Test
    fun `provider cancellation propagates`() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                prepareSentenceAudioForMarker(true, LazyAnkiSentenceAudioProvider {
                    throw CancellationException("cancel")
                })
            }
        }
    }
}
