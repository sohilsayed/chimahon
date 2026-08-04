package chimahon.anki

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AnkiCardMediaPreparationTest {

    private enum class FailingStore { SCREENSHOT, WORD_AUDIO, DICTIONARY_MEDIA, SENTENCE_AUDIO }

    @Test
    fun `generic and typed store failures keep the card media commit non fatal`() = runTest {
        FailingStore.entries.forEach { failure ->
            val prepared = PreparedAnkiCardMedia(
                screenshot = PreparedAnkiMediaPayload("screenshot.webp", byteArrayOf(1)),
                wordAudio = PreparedAnkiMediaPayload("word.mp3", byteArrayOf(2)),
                dictionaryMedia = listOf(
                    PreparedDictionaryMedia("dict.png", PreparedAnkiMediaPayload("dict.png", byteArrayOf(3))),
                ),
                sentenceAudio = AnkiSentenceAudioPreparation.Ready(
                    AnkiSentenceAudioSource.fromBytes(byteArrayOf(4), "m4a"),
                ),
            )
            val committed = commitPreparedAnkiCardMedia(
                prepared = prepared,
                storeBytes = { filename, _ ->
                    if ((filename == "screenshot.webp" && failure == FailingStore.SCREENSHOT) ||
                        (filename == "word.mp3" && failure == FailingStore.WORD_AUDIO) ||
                        (filename == "dict.png" && failure == FailingStore.DICTIONARY_MEDIA)
                    ) error("store failure")
                    filename
                },
                storeSentenceAudio = {
                    if (failure == FailingStore.SENTENCE_AUDIO) error("store failure")
                    "sentence.m4a"
                },
            )

            when (failure) {
                FailingStore.SCREENSHOT -> assertNull(committed.screenshotFilename)
                FailingStore.WORD_AUDIO -> assertNull(committed.wordAudioFilename)
                FailingStore.DICTIONARY_MEDIA -> assertEquals(
                    TRANSPARENT_IMAGE_DATA_URI,
                    committed.dictionaryReplacementByPlaceholder.getValue("dict.png"),
                )
                FailingStore.SENTENCE_AUDIO -> {
                    assertNull(committed.sentenceAudio.filename)
                    assertEquals(listOf(AnkiMediaWarning.SentenceAudioStorageFailed), committed.sentenceAudio.warnings)
                }
            }
        }
    }
}
