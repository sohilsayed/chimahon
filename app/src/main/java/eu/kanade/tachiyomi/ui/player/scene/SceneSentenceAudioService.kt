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
                val resolvedInput = when (val resolution = resolveAudioInput(input)) {
                    is AudioInputResolution.Ready -> resolution.input
                    is AudioInputResolution.Unavailable -> return@withContext unavailable(resolution.failure)
                }
                val lease = inputAcquirer.acquire(resolvedInput)
                    ?: return@withContext unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
                val output = File(cacheDirectory, "chimahon_sentence_audio_${UUID.randomUUID()}.m4a")
                val inputCleanup = SceneNativeCleanup(lease::close)
                val outputCleanup = SceneNativeCleanup(output::delete)
                try {
                    output.delete()
                    val result = commandExecutor.executeFfmpeg(
                        SceneFfmpegArguments.sentenceAudio(
                            input = resolvedInput,
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

    private suspend fun resolveAudioInput(input: SceneVideoInputSpec): AudioInputResolution {
        return when (val probe = executeAudioProbe(input, allAudioStreams = false)) {
            AudioProbeResult.SourceUnavailable -> {
                AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
            }
            AudioProbeResult.ExecutionFailed -> {
                AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED)
            }
            is AudioProbeResult.Success -> {
                when (val inspection = SceneMediaProbe.inspectSelectedAudio(probe.output)) {
                    SceneMediaProbe.AudioInspection.Readable -> AudioInputResolution.Ready(input)
                    SceneMediaProbe.AudioInspection.Protected -> {
                        AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED)
                    }
                    SceneMediaProbe.AudioInspection.StreamMissing,
                    SceneMediaProbe.AudioInspection.NotAudio -> {
                        resolveFallbackAudioInput(input, inspection)
                    }
                }
            }
        }
    }

    private suspend fun resolveFallbackAudioInput(
        input: SceneVideoInputSpec,
        selectedInspection: SceneMediaProbe.AudioInspection,
    ): AudioInputResolution {
        return when (val probe = executeAudioProbe(input, allAudioStreams = true)) {
            AudioProbeResult.SourceUnavailable -> {
                AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
            }
            AudioProbeResult.ExecutionFailed -> {
                AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED)
            }
            is AudioProbeResult.Success -> {
                val streams = SceneMediaProbe.audioStreams(probe.output)
                val onlyStream = streams.singleOrNull()
                when {
                    onlyStream?.index != null && !onlyStream.protected -> {
                        AudioInputResolution.Ready(input.copy(audioStreamIndex = onlyStream.index))
                    }
                    streams.size > 1 -> {
                        AudioInputResolution.Unavailable(
                            AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE,
                        )
                    }
                    onlyStream?.protected == true -> {
                        AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED)
                    }
                    onlyStream != null -> {
                        AudioInputResolution.Unavailable(
                            AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE,
                        )
                    }
                    selectedInspection is SceneMediaProbe.AudioInspection.StreamMissing -> {
                        AudioInputResolution.Unavailable(
                            AnkiSentenceAudioFailure.AUDIO_STREAM_INDEX_UNAVAILABLE,
                        )
                    }
                    else -> {
                        AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAM_NOT_AUDIO)
                    }
                }
            }
        }
    }

    private suspend fun executeAudioProbe(
        input: SceneVideoInputSpec,
        allAudioStreams: Boolean,
    ): AudioProbeResult {
        val lease = inputAcquirer.acquire(input) ?: return AudioProbeResult.SourceUnavailable
        val cleanup = SceneNativeCleanup(lease::close)
        return try {
            val probe = commandExecutor.executeFfprobe(
                if (allAudioStreams) {
                    SceneFfmpegArguments.allAudioProbe(input, lease.ffmpegValue, lease.tlsCaFile)
                } else {
                    SceneFfmpegArguments.audioProbe(input, lease.ffmpegValue, lease.tlsCaFile)
                },
                cleanup::nativeFinished,
            )
            when (probe) {
                SceneCommandResult.Failed -> AudioProbeResult.ExecutionFailed
                is SceneCommandResult.Success -> AudioProbeResult.Success(probe.output)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AudioProbeResult.ExecutionFailed
        } finally {
            cleanup.release()
        }
    }

    private sealed interface AudioInputResolution {
        data class Ready(
            val input: SceneVideoInputSpec,
        ) : AudioInputResolution

        data class Unavailable(
            val failure: AnkiSentenceAudioFailure,
        ) : AudioInputResolution
    }

    private sealed interface AudioProbeResult {
        data object SourceUnavailable : AudioProbeResult

        data object ExecutionFailed : AudioProbeResult

        data class Success(
            val output: String,
        ) : AudioProbeResult
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
