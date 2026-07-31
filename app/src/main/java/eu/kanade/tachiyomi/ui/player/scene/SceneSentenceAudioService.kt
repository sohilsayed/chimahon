package eu.kanade.tachiyomi.ui.player.scene

import android.content.Context
import chimahon.anki.AnkiMediaSource
import chimahon.anki.AnkiSentenceAudioDiagnostic
import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioInputSource
import chimahon.anki.AnkiSentenceAudioPlayableFallback
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
    private val diagnosticLogger: SentenceAudioDiagnosticLogger = NoOpSentenceAudioDiagnosticLogger,
) : SceneSentenceAudioService {
    constructor(context: Context) : this(
        cacheDirectory = context.cacheDir,
        inputAcquirer = AndroidSceneInputAcquirer(context),
        commandExecutor = FfmpegKitSceneCommandExecutor(),
        diagnosticLogger = createSentenceAudioDiagnosticLogger(),
    )

    override suspend fun prepare(request: SceneCaptureRequest): AnkiSentenceAudioPreparation {
        val input = request.sentenceAudioInput ?: run {
            val failure = request.sentenceAudioFailure ?: AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE
            diagnosticLogger.record(
                SentenceAudioDiagnosticEvent(
                    stage = SentenceAudioDiagnosticStage.REQUEST_VALIDATION,
                    input = null,
                    fallback = SentenceAudioDiagnosticFallback.NOT_APPLICABLE,
                    failure = failure,
                ),
            )
            return unavailable(failure)
        }
        val range = request.resolvedTiming?.audioRange ?: run {
            diagnosticLogger.record(
                SentenceAudioDiagnosticEvent(
                    stage = SentenceAudioDiagnosticStage.REQUEST_VALIDATION,
                    input = input,
                    fallback = input.toDiagnosticFallback(),
                    failure = AnkiSentenceAudioFailure.TIMING_UNAVAILABLE,
                ),
            )
            return unavailable(AnkiSentenceAudioFailure.TIMING_UNAVAILABLE)
        }
        val preparation = withTimeoutOrNull(timeoutMillis) {
            withContext(Dispatchers.IO) {
                val resolvedInput = when (
                    val resolution = resolveAudioInput(
                        input = input,
                        fallbackInput = request.sentenceAudioFallbackInput,
                        fallbackStatus = request.sentenceAudioFallbackStatus,
                    )
                ) {
                    is AudioInputResolution.Ready -> resolution.input
                    is AudioInputResolution.Unavailable -> {
                        return@withContext unavailable(resolution.failure, resolution.diagnostic)
                    }
                }
                val lease = inputAcquirer.acquire(resolvedInput)
                    ?: run {
                        diagnosticLogger.record(
                            SentenceAudioDiagnosticEvent(
                                stage = SentenceAudioDiagnosticStage.AUDIO_EXTRACTION,
                                input = resolvedInput,
                                fallback = resolvedInput.toDiagnosticFallback(),
                                failure = AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE,
                            ),
                        )
                        return@withContext diagnosticUnavailable(
                            resolvedInput,
                            AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE,
                        ).toPreparation()
                    }
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
                    diagnosticLogger.record(
                        SentenceAudioDiagnosticEvent(
                            stage = SentenceAudioDiagnosticStage.AUDIO_EXTRACTION,
                            input = resolvedInput,
                            fallback = resolvedInput.toDiagnosticFallback(),
                            failure = result.toSentenceAudioFailure()
                                .takeIf { result !is SceneCommandResult.Success },
                            result = result,
                        ),
                    )
                    if (result !is SceneCommandResult.Success) {
                        return@withContext unavailable(
                            failure = result.toSentenceAudioFailure(),
                            diagnostic = result.toSentenceAudioDiagnostic(resolvedInput),
                        )
                    }
                    if (!output.isFile || output.length() == 0L) {
                        diagnosticLogger.record(
                            SentenceAudioDiagnosticEvent(
                                stage = SentenceAudioDiagnosticStage.OUTPUT_VALIDATION,
                                input = resolvedInput,
                                fallback = resolvedInput.toDiagnosticFallback(),
                                failure = AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_MISSING,
                                result = result,
                            ),
                        )
                        return@withContext unavailable(
                            failure = AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_MISSING,
                            diagnostic = resolvedInput.toDiagnostic(),
                        )
                    }
                    val bytes = try {
                        output.readBytes()
                    } catch (e: Exception) {
                        diagnosticLogger.record(
                            SentenceAudioDiagnosticEvent(
                                stage = SentenceAudioDiagnosticStage.OUTPUT_READ,
                                input = resolvedInput,
                                fallback = resolvedInput.toDiagnosticFallback(),
                                failure = AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_READ_FAILED,
                                result = result,
                                exceptionType = e.javaClass.name,
                            ),
                        )
                        return@withContext unavailable(
                            failure = AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_READ_FAILED,
                            diagnostic = resolvedInput.toDiagnostic(),
                        )
                    }
                    AnkiSentenceAudioPreparation.Ready(
                        AnkiMediaSource.Bytes(
                            data = bytes,
                            preferredBaseName = "chimahon_sentence_${bytes.sha256()}",
                            extension = "m4a",
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    diagnosticLogger.record(
                        SentenceAudioDiagnosticEvent(
                            stage = SentenceAudioDiagnosticStage.AUDIO_EXTRACTION,
                            input = resolvedInput,
                            fallback = resolvedInput.toDiagnosticFallback(),
                            failure = AnkiSentenceAudioFailure.EXTRACTION_FAILED,
                            exceptionType = e.javaClass.name,
                        ),
                    )
                    unavailable(
                        failure = AnkiSentenceAudioFailure.EXTRACTION_FAILED,
                        diagnostic = resolvedInput.toDiagnostic(),
                    )
                } finally {
                    inputCleanup.release()
                    outputCleanup.release()
                }
            }
        }
        return preparation ?: run {
            diagnosticLogger.record(
                SentenceAudioDiagnosticEvent(
                    stage = SentenceAudioDiagnosticStage.AUDIO_EXTRACTION,
                    input = input,
                    fallback = input.toDiagnosticFallback(),
                    failure = AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT,
                ),
            )
            unavailable(AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT)
        }
    }

    private suspend fun resolveAudioInput(
        input: SceneVideoInputSpec,
        fallbackInput: SceneVideoInputSpec?,
        fallbackStatus: AnkiSentenceAudioPlayableFallback?,
    ): AudioInputResolution {
        val resolution = resolveSingleAudioInput(input)
        if (
            resolution !is AudioInputResolution.Unavailable ||
            !resolution.failure.isPlayableFallbackRetryable() ||
            input.origin != SceneVideoInputOrigin.ORIGINAL_VIDEO
        ) {
            return resolution
        }
        val playableFallback = fallbackInput?.takeIf {
            it.origin == SceneVideoInputOrigin.PLAYABLE_VIDEO && it.value != input.value
        }
        if (playableFallback == null) {
            val effectiveFallbackStatus = fallbackStatus ?: when {
                fallbackInput?.value == input.value -> AnkiSentenceAudioPlayableFallback.SAME_AS_ORIGINAL
                else -> null
            }
            diagnosticLogger.record(
                SentenceAudioDiagnosticEvent(
                    stage = SentenceAudioDiagnosticStage.FALLBACK_DECISION,
                    input = input,
                    fallback = effectiveFallbackStatus.toDiagnosticFallback(),
                    failure = resolution.failure,
                ),
            )
            return resolution.withFallbackStatus(input, effectiveFallbackStatus)
        }
        diagnosticLogger.record(
            SentenceAudioDiagnosticEvent(
                stage = SentenceAudioDiagnosticStage.FALLBACK_DECISION,
                input = input,
                fallback = SentenceAudioDiagnosticFallback.ATTEMPTED,
                failure = resolution.failure,
            ),
        )
        return resolveSingleAudioInput(playableFallback)
    }

    private suspend fun resolveSingleAudioInput(input: SceneVideoInputSpec): AudioInputResolution {
        return when (val probe = executeAudioProbe(input, AudioProbeMode.SELECTED_RESTRICTED)) {
            AudioProbeResult.SourceUnavailable -> {
                diagnosticUnavailable(input, AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
            }
            AudioProbeResult.ExecutionFailed -> {
                diagnosticUnavailable(input, AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED)
            }
            is AudioProbeResult.Success -> {
                when (val inspection = SceneMediaProbe.inspectSelectedAudio(probe.output)) {
                    SceneMediaProbe.AudioInspection.Readable -> AudioInputResolution.Ready(input)
                    SceneMediaProbe.AudioInspection.Protected -> {
                        AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED)
                    }
                    SceneMediaProbe.AudioInspection.StreamMissing,
                    SceneMediaProbe.AudioInspection.NotAudio -> {
                        resolveFallbackAudioInput(input)
                    }
                }
            }
        }
    }

    private suspend fun resolveFallbackAudioInput(
        input: SceneVideoInputSpec,
    ): AudioInputResolution {
        return when (val probe = executeAudioProbe(input, AudioProbeMode.ALL_RESTRICTED)) {
            AudioProbeResult.SourceUnavailable -> {
                diagnosticUnavailable(input, AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
            }
            AudioProbeResult.ExecutionFailed -> {
                diagnosticUnavailable(input, AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED)
            }
            is AudioProbeResult.Success -> {
                val streams = SceneMediaProbe.audioStreams(probe.output)
                val onlyStream = streams.singleOrNull()
                when {
                    onlyStream?.index != null && !onlyStream.protected -> {
                        AudioInputResolution.Ready(input.copy(audioStreamIndex = onlyStream.index))
                    }
                    streams.size > 1 -> {
                        AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE)
                    }
                    onlyStream?.protected == true -> {
                        AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED)
                    }
                    onlyStream != null -> {
                        AudioInputResolution.Unavailable(AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE)
                    }
                    else -> {
                        resolveEmptyAudioInventory(input)
                    }
                }
            }
        }
    }

    private suspend fun resolveEmptyAudioInventory(input: SceneVideoInputSpec): AudioInputResolution {
        return when (val probe = executeAudioProbe(input, AudioProbeMode.ALL_UNRESTRICTED_DISCOVERY)) {
            AudioProbeResult.SourceUnavailable -> {
                diagnosticUnavailable(input, AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
            }
            AudioProbeResult.ExecutionFailed -> {
                diagnosticUnavailable(input, AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED)
            }
            is AudioProbeResult.Success -> {
                val streams = SceneMediaProbe.audioStreams(probe.output)
                val failure = if (streams.isNotEmpty()) {
                    AnkiSentenceAudioFailure.AUDIO_CODEC_RESTRICTED
                } else if (
                    input.kind == SceneVideoInputKind.REMOTE_HTTP &&
                    input.audioStreamIndex != null &&
                    input.origin == SceneVideoInputOrigin.ORIGINAL_VIDEO
                ) {
                    return AudioInputResolution.Ready(input)
                } else {
                    AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND
                }
                diagnosticUnavailable(input, failure)
            }
        }
    }

    private suspend fun executeAudioProbe(
        input: SceneVideoInputSpec,
        mode: AudioProbeMode,
    ): AudioProbeResult {
        val stage = mode.toDiagnosticStage()
        val fallback = input.toDiagnosticFallback()
        val lease = inputAcquirer.acquire(input) ?: run {
            diagnosticLogger.record(
                SentenceAudioDiagnosticEvent(
                    stage = stage,
                    input = input,
                    fallback = fallback,
                    failure = AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE,
                ),
            )
            return AudioProbeResult.SourceUnavailable
        }
        val cleanup = SceneNativeCleanup(lease::close)
        return try {
            val probe = commandExecutor.executeFfprobe(
                when (mode) {
                    AudioProbeMode.SELECTED_RESTRICTED -> {
                        SceneFfmpegArguments.audioProbe(input, lease.ffmpegValue, lease.tlsCaFile)
                    }
                    AudioProbeMode.ALL_RESTRICTED -> {
                        SceneFfmpegArguments.allAudioProbe(input, lease.ffmpegValue, lease.tlsCaFile)
                    }
                    AudioProbeMode.ALL_UNRESTRICTED_DISCOVERY -> {
                        SceneFfmpegArguments.audioDiscoveryProbe(input, lease.ffmpegValue, lease.tlsCaFile)
                    }
                },
                cleanup::nativeFinished,
            )
            diagnosticLogger.record(
                SentenceAudioDiagnosticEvent(
                    stage = stage,
                    input = input,
                    fallback = fallback,
                    failure = AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED
                        .takeIf { probe !is SceneCommandResult.Success },
                    result = probe,
                ),
            )
            when (probe) {
                SceneCommandResult.Failed,
                is SceneCommandResult.FfmpegFailed -> AudioProbeResult.ExecutionFailed
                is SceneCommandResult.Success -> AudioProbeResult.Success(probe.output)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnosticLogger.record(
                SentenceAudioDiagnosticEvent(
                    stage = stage,
                    input = input,
                    fallback = fallback,
                    failure = AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED,
                    exceptionType = e.javaClass.name,
                ),
            )
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
            val diagnostic: AnkiSentenceAudioDiagnostic? = null,
        ) : AudioInputResolution
    }

    private enum class AudioProbeMode {
        SELECTED_RESTRICTED,
        ALL_RESTRICTED,
        ALL_UNRESTRICTED_DISCOVERY,
    }

    private sealed interface AudioProbeResult {
        data object SourceUnavailable : AudioProbeResult

        data object ExecutionFailed : AudioProbeResult

        data class Success(
            val output: String,
        ) : AudioProbeResult
    }

    private fun diagnosticUnavailable(
        input: SceneVideoInputSpec,
        failure: AnkiSentenceAudioFailure,
    ) = AudioInputResolution.Unavailable(failure, input.toDiagnostic())

    private fun AudioInputResolution.Unavailable.withFallbackStatus(
        input: SceneVideoInputSpec,
        fallbackStatus: AnkiSentenceAudioPlayableFallback?,
    ): AudioInputResolution.Unavailable {
        return fallbackStatus?.let { copy(diagnostic = input.toDiagnostic(it)) } ?: this
    }

    private fun AudioInputResolution.Unavailable.toPreparation(): AnkiSentenceAudioPreparation {
        return unavailable(failure, diagnostic)
    }

    private fun unavailable(
        failure: AnkiSentenceAudioFailure,
        diagnostic: AnkiSentenceAudioDiagnostic? = null,
    ) = AnkiSentenceAudioPreparation.Unavailable(failure, diagnostic)

    private fun SceneVideoInputSpec.toDiagnostic(
        playableFallback: AnkiSentenceAudioPlayableFallback? = null,
    ): AnkiSentenceAudioDiagnostic {
        return AnkiSentenceAudioDiagnostic(
            inputSource = when (origin) {
                SceneVideoInputOrigin.ORIGINAL_VIDEO -> AnkiSentenceAudioInputSource.ORIGINAL_VIDEO
                SceneVideoInputOrigin.PLAYABLE_VIDEO -> AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO
                SceneVideoInputOrigin.EXTERNAL_AUDIO -> AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO
            },
            playableFallback = playableFallback,
        )
    }

    private fun AudioProbeMode.toDiagnosticStage(): SentenceAudioDiagnosticStage {
        return when (this) {
            AudioProbeMode.SELECTED_RESTRICTED -> SentenceAudioDiagnosticStage.SELECTED_AUDIO_PROBE
            AudioProbeMode.ALL_RESTRICTED -> SentenceAudioDiagnosticStage.ALL_AUDIO_PROBE
            AudioProbeMode.ALL_UNRESTRICTED_DISCOVERY -> SentenceAudioDiagnosticStage.AUDIO_DISCOVERY_PROBE
        }
    }

    private fun SceneVideoInputSpec.toDiagnosticFallback(): SentenceAudioDiagnosticFallback {
        return if (origin == SceneVideoInputOrigin.PLAYABLE_VIDEO) {
            SentenceAudioDiagnosticFallback.ATTEMPTED
        } else {
            SentenceAudioDiagnosticFallback.NOT_APPLICABLE
        }
    }

    private fun AnkiSentenceAudioPlayableFallback?.toDiagnosticFallback(): SentenceAudioDiagnosticFallback {
        return when (this) {
            AnkiSentenceAudioPlayableFallback.MISSING -> SentenceAudioDiagnosticFallback.MISSING
            AnkiSentenceAudioPlayableFallback.SAME_AS_ORIGINAL -> {
                SentenceAudioDiagnosticFallback.SAME_AS_ORIGINAL
            }
            AnkiSentenceAudioPlayableFallback.UNAVAILABLE -> SentenceAudioDiagnosticFallback.UNAVAILABLE
            null -> SentenceAudioDiagnosticFallback.NOT_APPLICABLE
        }
    }

    private fun AnkiSentenceAudioFailure.isPlayableFallbackRetryable(): Boolean {
        return this == AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE ||
            this == AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED ||
            this == AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND
    }

    private fun SceneCommandResult.toSentenceAudioFailure(): AnkiSentenceAudioFailure {
        return when (this) {
            is SceneCommandResult.Success,
            SceneCommandResult.Failed -> AnkiSentenceAudioFailure.EXTRACTION_FAILED
            is SceneCommandResult.FfmpegFailed -> when (failure) {
                SceneFfmpegFailure.STREAM_MAPPING -> {
                    AnkiSentenceAudioFailure.EXTRACTION_STREAM_MAPPING_FAILED
                }
                SceneFfmpegFailure.SOURCE_READ -> {
                    AnkiSentenceAudioFailure.EXTRACTION_SOURCE_READ_FAILED
                }
                SceneFfmpegFailure.SEEK -> AnkiSentenceAudioFailure.EXTRACTION_SEEK_FAILED
                SceneFfmpegFailure.OUTPUT_WRITE -> {
                    AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_WRITE_FAILED
                }
                SceneFfmpegFailure.UNKNOWN -> AnkiSentenceAudioFailure.EXTRACTION_FAILED
            }
        }
    }

    private fun SceneCommandResult.toSentenceAudioDiagnostic(
        input: SceneVideoInputSpec,
    ): AnkiSentenceAudioDiagnostic? {
        return (this as? SceneCommandResult.FfmpegFailed)
            ?.takeUnless { it.failure == SceneFfmpegFailure.UNKNOWN }
            ?.let { input.toDiagnostic() }
    }

    private fun ByteArray.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val AUDIO_TIMEOUT_MILLIS = 60_000L
    }
}
