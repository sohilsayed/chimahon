package eu.kanade.tachiyomi.ui.reader.viewer

import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioPreparation
import chimahon.anki.AnkiSentenceAudioSource
import chimahon.anki.AnkiMediaRequest
import chimahon.anki.LazyAnkiSentenceAudioProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnkiPopupMediaPreloadTest {

    @Test
    fun `hidden popup is not eligible for media preload`() {
        assertFalse(
            planPopupAnkiMediaPreload(
                popupVisible = false,
                duplicateCheckCompleted = true,
                hasNewExpression = true,
                duplicateCheckEnabled = true,
                duplicateAction = "prevent",
                ankiEnabled = true,
                screenshotFieldMapped = true,
                sentenceAudioFieldMapped = true,
                cropMode = "full",
            ).shouldStart,
        )
    }

    @Test
    fun `duplicate expression preloads when duplicate checking is disabled`() {
        assertEquals(
            true,
            planPopupAnkiMediaPreload(
                popupVisible = true,
                duplicateCheckCompleted = true,
                hasNewExpression = false,
                duplicateCheckEnabled = false,
                duplicateAction = "prevent",
                ankiEnabled = true,
                screenshotFieldMapped = true,
                sentenceAudioFieldMapped = true,
                cropMode = "full",
            ).shouldStart,
        )
    }

    @Test
    fun `duplicate expression preloads when duplicate action overwrites`() {
        assertEquals(
            true,
            planPopupAnkiMediaPreload(
                popupVisible = true,
                duplicateCheckCompleted = true,
                hasNewExpression = false,
                duplicateCheckEnabled = true,
                duplicateAction = "overwrite",
                ankiEnabled = true,
                screenshotFieldMapped = true,
                sentenceAudioFieldMapped = true,
                cropMode = "full",
            ).shouldStart,
        )
    }

    @Test
    fun `duplicate expression skips preload when duplicate action prevents add`() {
        assertFalse(
            planPopupAnkiMediaPreload(
                popupVisible = true,
                duplicateCheckCompleted = true,
                hasNewExpression = false,
                duplicateCheckEnabled = true,
                duplicateAction = "prevent",
                ankiEnabled = true,
                screenshotFieldMapped = true,
                sentenceAudioFieldMapped = true,
                cropMode = "full",
            ).shouldStart,
        )
    }

    @Test
    fun `crop mode preloads sentence audio without preloading a screenshot`() {
        val plan = planPopupAnkiMediaPreload(
            popupVisible = true,
            duplicateCheckCompleted = true,
            hasNewExpression = true,
            duplicateCheckEnabled = true,
            duplicateAction = "prevent",
            ankiEnabled = true,
            screenshotFieldMapped = true,
            sentenceAudioFieldMapped = true,
            cropMode = "crop",
        )

        assertTrue(plan.shouldStart)
        assertFalse(plan.prepareScreenshot)
        assertTrue(plan.prepareSentenceAudio)
    }

    @Test
    fun `no screenshot mode skips a screenshot-only preload`() {
        val plan = planPopupAnkiMediaPreload(
            popupVisible = true,
            duplicateCheckCompleted = true,
            hasNewExpression = true,
            duplicateCheckEnabled = true,
            duplicateAction = "prevent",
            ankiEnabled = true,
            screenshotFieldMapped = true,
            sentenceAudioFieldMapped = false,
            cropMode = "no_screenshot",
        )

        assertFalse(plan.shouldStart)
    }

    @Test
    fun `cancelling a pending preload before its delay prevents native capture`() = runTest {
        val nativeCaptureStarted = CompletableDeferred<Unit>()
        val result = async {
            delay(POPUP_ANKI_MEDIA_PRELOAD_DELAY_MS)
            nativeCaptureStarted.complete(Unit)
            null
        }
        val pending = PendingPopupAnkiMediaPreload(
            frameId = "frame",
            nativeCaptureStarted = nativeCaptureStarted,
            result = result,
        )
        runCurrent()

        val prepared = takePopupAnkiMediaForAdd(
            cachedMedia = null,
            pendingPreload = pending,
        )

        assertEquals(null, prepared)
        assertTrue(result.isCancelled)
        assertFalse(nativeCaptureStarted.isCompleted)
    }

    @Test
    fun `add reuses a pending preload after native capture starts`() = runTest {
        val expected = PopupPreparedAnkiMedia(
            frameId = "frame",
            screenshotBytes = byteArrayOf(1),
            sentenceAudio = null,
        )
        val nativeCaptureStarted = CompletableDeferred<Unit>().apply { complete(Unit) }
        val pending = PendingPopupAnkiMediaPreload(
            frameId = "frame",
            nativeCaptureStarted = nativeCaptureStarted,
            result = async { expected },
        )

        val prepared = takePopupAnkiMediaForAdd(
            cachedMedia = null,
            pendingPreload = pending,
        )

        assertEquals(expected, prepared)
    }

    @Test
    fun `popup media retains an unavailable sentence-audio preparation`() {
        val unavailable = AnkiSentenceAudioPreparation.Unavailable(
            AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND,
        )

        val media = PopupPreparedAnkiMedia(
            frameId = "frame",
            screenshotBytes = null,
            sentenceAudio = unavailable,
        )

        assertEquals(unavailable, media.sentenceAudio)
    }

    @Test
    fun `sentence-audio provider waits for active popup media preparation`() = runTest {
        val gate = SerializedAnkiMediaPreloadGate()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val providerStarted = CompletableDeferred<Unit>()
        val request = AnkiMediaRequest(
            sentenceAudioProvider = LazyAnkiSentenceAudioProvider {
                providerStarted.complete(Unit)
                AnkiSentenceAudioPreparation.Ready(
                    AnkiSentenceAudioSource.fromBytes(byteArrayOf(1), "m4a"),
                )
            },
        ).withSerializedSentenceAudioPreparation(gate)

        val first = launch {
            gate.run {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        firstStarted.await()

        val preparation = async { request.sentenceAudioProvider?.prepare() }
        runCurrent()

        assertFalse(providerStarted.isCompleted)

        releaseFirst.complete(Unit)

        val prepared = preparation.await() as? AnkiSentenceAudioPreparation.Ready
        assertEquals(listOf<Byte>(1), prepared?.source?.data?.toList())
        first.join()
    }

    @Test
    fun `next preload waits until the active preload releases the shared gate`() = runTest {
        val gate = SerializedAnkiMediaPreloadGate()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()

        val first = launch {
            gate.run {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        firstStarted.await()

        val second = async {
            gate.run {
                secondStarted.complete(Unit)
                "second"
            }
        }
        runCurrent()

        assertFalse(secondStarted.isCompleted)

        releaseFirst.complete(Unit)

        assertEquals("second", second.await())
        first.join()
    }
}
