package chimahon.anki

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

enum class AnkiScreenshotMode(val storageValue: String) {
    FULL("full"),
    CROP("crop"),
    NONE("no_screenshot"),
    ANIMATED_SCENE("animated_scene"),
    ;

    companion object {
        fun fromStorageValue(value: String): AnkiScreenshotMode =
            entries.firstOrNull { it.storageValue == value } ?: FULL
    }
}

sealed interface AnkiMediaSource {
    val preferredBaseName: String
    val extension: String

    data class Bytes(
        val data: ByteArray,
        override val preferredBaseName: String,
        override val extension: String,
    ) : AnkiMediaSource

    /**
     * A temporary app-owned file. Once a provider returns it, AnkiCardCreator
     * deletes it after the card attempt, whether storage succeeds or fails.
     */
    data class FileSource(
        val file: File,
        override val preferredBaseName: String,
        override val extension: String,
    ) : AnkiMediaSource
}

enum class AnkiSentenceAudioFailure {
    TRACK_MAPPING_UNAVAILABLE,
    SOURCE_UNAVAILABLE,
    TIMING_UNAVAILABLE,
    AUDIO_PROBE_FAILED,
    AUDIO_STREAM_INDEX_UNAVAILABLE,
    AUDIO_STREAM_NOT_AUDIO,
    AUDIO_STREAM_PROTECTED,
    AUDIO_STREAM_UNREADABLE,
    EXTRACTION_FAILED,
    EXTRACTION_TIMED_OUT,
    UNKNOWN,
}

sealed interface AnkiSentenceAudioPreparation {
    data class Ready(
        val source: AnkiMediaSource,
    ) : AnkiSentenceAudioPreparation

    data class Unavailable(
        val failure: AnkiSentenceAudioFailure,
    ) : AnkiSentenceAudioPreparation
}

sealed interface AnkiMediaWarning {
    data object SceneGenerationFailed : AnkiMediaWarning
    data object AnimatedStorageFailed : AnkiMediaWarning
    data object StillStorageFailed : AnkiMediaWarning
    data class SentenceAudioGenerationFailed(
        val failure: AnkiSentenceAudioFailure,
    ) : AnkiMediaWarning
    data object SentenceAudioStorageFailed : AnkiMediaWarning
}

sealed interface AnkiScreenshotPreparation {
    data class Animated(
        val animation: AnkiMediaSource.FileSource,
        val stillFallback: AnkiMediaSource.Bytes?,
    ) : AnkiScreenshotPreparation

    data class Still(
        val still: AnkiMediaSource.Bytes?,
    ) : AnkiScreenshotPreparation

    data class Failed(
        val stillFallback: AnkiMediaSource.Bytes?,
    ) : AnkiScreenshotPreparation
}

fun interface LazyAnkiScreenshotProvider {
    suspend fun prepare(): AnkiScreenshotPreparation
}

fun interface LazyAnkiMediaProvider {
    suspend fun prepare(): AnkiSentenceAudioPreparation
}

data class AnkiMediaRequest(
    val screenshotProvider: LazyAnkiScreenshotProvider? = null,
    val sentenceAudioProvider: LazyAnkiMediaProvider? = null,
    val onCommitStarted: () -> Unit = {},
)

internal data class StoredScreenshotMedia(
    val filename: String?,
    val warnings: List<AnkiMediaWarning>,
)

internal class AnkiScreenshotMediaCommitter(
    private val store: suspend (AnkiMediaSource) -> String,
) {
    suspend fun store(preparation: AnkiScreenshotPreparation?): StoredScreenshotMedia =
        when (preparation) {
            null -> StoredScreenshotMedia(filename = null, warnings = emptyList())

            is AnkiScreenshotPreparation.Animated -> storeAnimated(preparation)
            is AnkiScreenshotPreparation.Still -> storeStill(preparation.still, emptyList())
            is AnkiScreenshotPreparation.Failed -> storeStill(
                preparation.stillFallback,
                listOf(AnkiMediaWarning.SceneGenerationFailed),
            )
        }

    private suspend fun storeAnimated(
        preparation: AnkiScreenshotPreparation.Animated,
    ): StoredScreenshotMedia {
        return try {
            require(preparation.animation.extension.equals(AVIF_EXTENSION, ignoreCase = true)) {
                "Animated scene media must use AVIF"
            }
            val filename = store(preparation.animation)
            require(filename.substringAfterLast('.', "").equals(AVIF_EXTENSION, ignoreCase = true)) {
                "AnkiDroid did not return an AVIF media filename"
            }
            StoredScreenshotMedia(filename = filename, warnings = emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            storeStill(
                preparation.stillFallback,
                listOf(AnkiMediaWarning.AnimatedStorageFailed),
            )
        }
    }

    private suspend fun storeStill(
        still: AnkiMediaSource.Bytes?,
        warnings: List<AnkiMediaWarning>,
    ): StoredScreenshotMedia {
        if (still == null) {
            return StoredScreenshotMedia(filename = null, warnings = warnings)
        }
        return try {
            StoredScreenshotMedia(filename = store(still), warnings = warnings)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            StoredScreenshotMedia(
                filename = null,
                warnings = warnings + AnkiMediaWarning.StillStorageFailed,
            )
        }
    }

    private companion object {
        const val AVIF_EXTENSION = "avif"
    }
}

object AnkiMediaNaming {
    suspend fun sceneFileSource(file: File): AnkiMediaSource.FileSource {
        val digest = sha256(file)
        return AnkiMediaSource.FileSource(
            file = file,
            preferredBaseName = "chimahon_scene_$digest",
            extension = "avif",
        )
    }

    suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        require(file.isFile && file.canRead()) { "Media file is not readable" }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHex()
    }

    fun safeExtension(value: String, fallback: String): String =
        value
            .substringBefore('?')
            .substringAfterLast('.', value)
            .replace(Regex("[^A-Za-z0-9]"), "")
            .ifBlank { fallback }
            .lowercase(Locale.ROOT)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
