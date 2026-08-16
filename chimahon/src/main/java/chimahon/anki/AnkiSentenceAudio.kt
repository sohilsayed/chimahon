package chimahon.anki

import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.util.Locale

/** A byte-backed audio file with the filename frozen before it reaches Anki. */
data class AnkiSentenceAudioSource(
    val data: ByteArray,
    val preferredBaseName: String,
    val extension: String,
) {
    companion object {
        fun fromBytes(data: ByteArray, extension: String): AnkiSentenceAudioSource {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(data)
                .joinToString("") { "%02x".format(it) }
            return AnkiSentenceAudioSource(
                data = data,
                preferredBaseName = "chimahon_sentence_$hash",
                extension = safeExtension(extension, "m4a"),
            )
        }

        fun safeExtension(value: String, fallback: String): String {
            val candidate = value.substringBefore('?')
            return candidate
                .substringAfterLast('.', candidate)
                .replace(Regex("[^A-Za-z0-9]"), "")
                .ifBlank { fallback }
                .lowercase(Locale.ROOT)
        }
    }
}

enum class AnkiSentenceAudioFailure {
    TRACK_MAPPING_UNAVAILABLE,
    SOURCE_UNAVAILABLE,
    TIMING_UNAVAILABLE,
    AUDIO_PROBE_FAILED,
    AUDIO_STREAMS_NOT_FOUND,
    AUDIO_CODEC_RESTRICTED,
    AUDIO_STREAM_INDEX_UNAVAILABLE,
    AUDIO_STREAM_NOT_AUDIO,
    AUDIO_STREAM_PROTECTED,
    AUDIO_STREAM_UNREADABLE,
    EXTRACTION_FAILED,
    EXTRACTION_OUTPUT_MISSING,
    EXTRACTION_OUTPUT_READ_FAILED,
    EXTRACTION_STREAM_MAPPING_FAILED,
    EXTRACTION_SOURCE_READ_FAILED,
    EXTRACTION_SEEK_FAILED,
    EXTRACTION_OUTPUT_WRITE_FAILED,
    EXTRACTION_TIMED_OUT,
    UNKNOWN,
}

enum class AnkiSentenceAudioInputSource {
    ORIGINAL_VIDEO,
    MPV_PLAYABLE_VIDEO,
    MPV_EXTERNAL_AUDIO,
}

enum class AnkiSentenceAudioPlayableFallback {
    MISSING,
    SAME_AS_ORIGINAL,
    UNAVAILABLE,
}

data class AnkiSentenceAudioDiagnostic(
    val inputSource: AnkiSentenceAudioInputSource,
    val playableFallback: AnkiSentenceAudioPlayableFallback? = null,
)

sealed interface AnkiSentenceAudioPreparation {
    data class Ready(val source: AnkiSentenceAudioSource) : AnkiSentenceAudioPreparation

    data class Unavailable(
        val failure: AnkiSentenceAudioFailure,
        val diagnostic: AnkiSentenceAudioDiagnostic? = null,
    ) : AnkiSentenceAudioPreparation
}

fun interface LazyAnkiSentenceAudioProvider {
    suspend fun prepare(): AnkiSentenceAudioPreparation
}

data class AnkiMediaRequest(
    val sentenceAudioProvider: LazyAnkiSentenceAudioProvider? = null,
    val preparedSentenceAudio: AnkiSentenceAudioPreparation? = null,
)

sealed interface AnkiMediaWarning {
    data class SentenceAudioGenerationFailed(
        val failure: AnkiSentenceAudioFailure,
        val diagnostic: AnkiSentenceAudioDiagnostic? = null,
    ) : AnkiMediaWarning

    data object SentenceAudioStorageFailed : AnkiMediaWarning
}

internal data class StoredSentenceAudio(
    val filename: String?,
    val warnings: List<AnkiMediaWarning>,
)

internal class AnkiSentenceAudioCommitter(
    private val store: suspend (AnkiSentenceAudioSource) -> String,
) {
    suspend fun store(preparation: AnkiSentenceAudioPreparation?): StoredSentenceAudio =
        when (preparation) {
            null -> StoredSentenceAudio(null, emptyList())
            is AnkiSentenceAudioPreparation.Unavailable -> StoredSentenceAudio(
                null,
                listOf(
                    AnkiMediaWarning.SentenceAudioGenerationFailed(
                        preparation.failure,
                        preparation.diagnostic,
                    ),
                ),
            )
            is AnkiSentenceAudioPreparation.Ready -> try {
                StoredSentenceAudio(store(preparation.source), emptyList())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                StoredSentenceAudio(null, listOf(AnkiMediaWarning.SentenceAudioStorageFailed))
            }
        }
}

suspend fun prepareSentenceAudioForMarker(
    hasSentenceAudioMarker: Boolean,
    provider: LazyAnkiSentenceAudioProvider?,
    prepared: AnkiSentenceAudioPreparation? = null,
): AnkiSentenceAudioPreparation? {
    if (!hasSentenceAudioMarker) return null
    if (prepared != null) return prepared
    if (provider == null) return null
    return try {
        provider.prepare()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.UNKNOWN)
    }
}
