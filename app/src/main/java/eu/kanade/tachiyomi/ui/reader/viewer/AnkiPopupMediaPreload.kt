package eu.kanade.tachiyomi.ui.reader.viewer

import chimahon.anki.AnkiMediaRequest
import chimahon.anki.AnkiSentenceAudioPreparation
import chimahon.anki.LazyAnkiSentenceAudioProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val POPUP_ANKI_MEDIA_PRELOAD_DELAY_MS = 500L

/**
 * Serializes media preparation across replacement dictionary popups. A new popup can wait for
 * cancellation cleanup of the previous native capture before starting its own capture.
 */
internal class SerializedAnkiMediaPreloadGate(
    private val mutex: Mutex = Mutex(),
) {
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}

internal val ankiMediaPreloadGate = SerializedAnkiMediaPreloadGate()

internal data class PopupPreparedAnkiMedia(
    val frameId: String,
    val screenshotBytes: ByteArray?,
    val sentenceAudio: AnkiSentenceAudioPreparation?,
)

internal data class PendingPopupAnkiMediaPreload(
    val frameId: String,
    val nativeCaptureStarted: CompletableDeferred<Unit>,
    val result: Deferred<PopupPreparedAnkiMedia?>,
)

internal data class PopupAnkiMediaPreloadPlan(
    val prepareScreenshot: Boolean,
    val prepareSentenceAudio: Boolean,
) {
    val shouldStart: Boolean
        get() = prepareScreenshot || prepareSentenceAudio
}

internal fun planPopupAnkiMediaPreload(
    popupVisible: Boolean,
    duplicateCheckCompleted: Boolean,
    hasNewExpression: Boolean,
    duplicateCheckEnabled: Boolean,
    duplicateAction: String,
    ankiEnabled: Boolean,
    screenshotFieldMapped: Boolean,
    sentenceAudioFieldMapped: Boolean,
    cropMode: String,
): PopupAnkiMediaPreloadPlan {
    val mayAddCard =
        popupVisible &&
            duplicateCheckCompleted &&
            ankiEnabled &&
            (hasNewExpression || !duplicateCheckEnabled || duplicateAction == "overwrite")
    if (!mayAddCard) return PopupAnkiMediaPreloadPlan(false, false)

    return PopupAnkiMediaPreloadPlan(
        prepareScreenshot = screenshotFieldMapped && cropMode != "crop" && cropMode != "no_screenshot",
        prepareSentenceAudio = sentenceAudioFieldMapped,
    )
}

internal suspend fun cancelPopupAnkiMediaPreload(preload: PendingPopupAnkiMediaPreload?) {
    preload?.result?.cancelAndJoin()
}

internal suspend fun takePopupAnkiMediaForAdd(
    cachedMedia: PopupPreparedAnkiMedia?,
    pendingPreload: PendingPopupAnkiMediaPreload?,
): PopupPreparedAnkiMedia? {
    if (cachedMedia != null) return cachedMedia
    val pending = pendingPreload ?: return null
    if (!pending.nativeCaptureStarted.isCompleted) {
        pending.result.cancelAndJoin()
        return null
    }
    return try {
        pending.result.await()
    } catch (_: kotlinx.coroutines.CancellationException) {
        null
    }
}

internal fun AnkiMediaRequest.withSerializedSentenceAudioPreparation(
    gate: SerializedAnkiMediaPreloadGate,
): AnkiMediaRequest = copy(
    sentenceAudioProvider = sentenceAudioProvider?.let { provider ->
        LazyAnkiSentenceAudioProvider {
            gate.run { provider.prepare() }
        }
    },
)
