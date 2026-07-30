package eu.kanade.tachiyomi.ui.player.scene

import android.content.Context
import chimahon.anki.AnkiMediaSource
import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioPreparation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal fun interface SceneSentenceAudioService {
    suspend fun prepare(request: SceneCaptureRequest): AnkiSentenceAudioPreparation
}

internal class FrozenSceneSentenceAudioService internal constructor(
    private val cacheDirectory: File,
    private val inputAcquirer: SceneInputAcquirer,
    private val commandExecutor: SceneCommandExecutor,
    private val timeoutMillis: Long = AUDIO_TIMEOUT_MILLIS,
) : SceneSentenceAudioService {
    constructor(context: Context) : this(
        cacheDirectory = context.cacheDir,
        inputAcquirer = AndroidSceneInputAcquirer(context),
        commandExecutor = FfmpegKitSceneCommandExecutor(),
    )

    override suspend fun prepare(request: SceneCaptureRequest): AnkiSentenceAudioPreparation {
        val input = request.sentenceAudioInput
            ?: return unavailable(request.sentenceAudioFailure ?: AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
        val range = request.resolvedTiming?.audioRange
            ?: return unavailable(AnkiSentenceAudioFailure.TIMING_UNAVAILABLE)
        return withTimeoutOrNull(timeoutMillis) {
            withContext(Dispatchers.IO) {
                inspectAudio(input)?.let { return@withContext unavailable(it) }
                val lease = inputAcquirer.acquire(input)
                    ?: return@withContext unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
                val output = File(cacheDirectory, "chimahon_sentence_audio_${UUID.randomUUID()}.m4a")
                val inputCleanup = SceneNativeCleanup(lease::close)
                val outputCleanup = SceneNativeCleanup(output::delete)
                try {
                    output.delete()
                    val result = commandExecutor.executeFfmpeg(
                        SceneFfmpegArguments.sentenceAudio(
                            input = input,
                            acquiredInputValue = lease.ffmpegValue,
                            range = range,
                            outputFile = output.absolutePath,
                            tlsCaFile = lease.tlsCaFile,
                        ),
                    ) {
                        inputCleanup.nativeFinished()
                        outputCleanup.nativeFinished()
                    }
                    if (result !is SceneCommandResult.Success || !output.isFile || output.length() == 0L) {
                        return@withContext unavailable(AnkiSentenceAudioFailure.EXTRACTION_FAILED)
                    }
                    val bytes = output.readBytes()
                    AnkiSentenceAudioPreparation.Ready(
                        AnkiMediaSource.Bytes(
                            data = bytes,
                            preferredBaseName = "chimahon_sentence_${bytes.sha256()}",
                            extension = "m4a",
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    unavailable(AnkiSentenceAudioFailure.EXTRACTION_FAILED)
                } finally {
                    inputCleanup.release()
                    outputCleanup.release()
                }
            }
        } ?: unavailable(AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT)
    }

    private suspend fun inspectAudio(input: SceneVideoInputSpec): AnkiSentenceAudioFailure? {
        val lease = inputAcquirer.acquire(input) ?: return AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE
        val cleanup = SceneNativeCleanup(lease::close)
        return try {
            val probe = commandExecutor.executeFfprobe(
                SceneFfmpegArguments.audioProbe(input, lease.ffmpegValue, lease.tlsCaFile),
                cleanup::nativeFinished,
            )
            when (probe) {
                SceneCommandResult.Failed -> AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED
                is SceneCommandResult.Success -> {
                    if (SceneMediaProbe.inspectAudio(probe.output)) {
                        null
                    } else {
                        AnkiSentenceAudioFailure.AUDIO_STREAM_UNREADABLE
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED
        } finally {
            cleanup.release()
        }
    }

    private fun unavailable(failure: AnkiSentenceAudioFailure) =
        AnkiSentenceAudioPreparation.Unavailable(failure)

    private fun ByteArray.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val AUDIO_TIMEOUT_MILLIS = 60_000L
    }
}
