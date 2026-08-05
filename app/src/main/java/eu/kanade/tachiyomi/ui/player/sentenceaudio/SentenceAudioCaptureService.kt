package eu.kanade.tachiyomi.ui.player.sentenceaudio

import chimahon.anki.AnkiSentenceAudioDiagnostic
import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioInputSource
import chimahon.anki.AnkiSentenceAudioPlayableFallback
import chimahon.anki.AnkiSentenceAudioPreparation
import chimahon.anki.AnkiSentenceAudioSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File

/** Immutable inputs captured while OCR is selected; this service never reads live player state. */
internal data class SentenceAudioCaptureRequest(
    val inputSnapshot: SentenceAudioInputSnapshot?,
    val startSeconds: Double?,
    val endSeconds: Double?,
    val inputFailure: AnkiSentenceAudioFailure? = null,
)

internal interface SentenceAudioInputLease : Closeable {
    val ffmpegValue: String
    val tlsCaFile: String?
}

internal fun interface SentenceAudioInputAcquirer {
    suspend fun acquire(input: SentenceAudioInputSpec): SentenceAudioInputLease?
}

internal class SentenceAudioCaptureService(
    private val cacheDirectory: File,
    private val inputAcquirer: SentenceAudioInputAcquirer,
    private val commandExecutor: SentenceAudioCommandExecutor,
    private val timeoutMillis: Long = 60_000L,
    private val diagnosticLogger: SentenceAudioDiagnosticLogger = NoOpSentenceAudioDiagnosticLogger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun prepare(request: SentenceAudioCaptureRequest): AnkiSentenceAudioPreparation {
        val snapshot = request.inputSnapshot ?: return unavailable(
            request.inputFailure ?: AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE,
            null,
            SentenceAudioDiagnosticStage.REQUEST_VALIDATION,
        )
        val range = request.validRangeOrNull() ?: return unavailable(
            AnkiSentenceAudioFailure.TIMING_UNAVAILABLE,
            null,
            SentenceAudioDiagnosticStage.REQUEST_VALIDATION,
        )
        val resolved = SentenceAudioInputResolver.resolveForCapture(snapshot)
        val original = when (resolved) {
            is SentenceAudioInputResolution.Available -> resolved.input
            is SentenceAudioInputResolution.Unavailable -> return unavailable(
                resolved.failure,
                null,
                SentenceAudioDiagnosticStage.REQUEST_VALIDATION,
            )
        }
        return withTimeoutOrNull(timeoutMillis) {
            withContext(ioDispatcher) {
                when (val result = resolveInput(snapshot, original)) {
                    is Resolution.Ready -> extract(result.input, range)
                    is Resolution.Unavailable -> AnkiSentenceAudioPreparation.Unavailable(result.failure, result.diagnostic)
                }
            }
        } ?: unavailable(
            AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT,
            original,
            SentenceAudioDiagnosticStage.AUDIO_EXTRACTION,
        )
    }

    private fun SentenceAudioCaptureRequest.validRangeOrNull(): ClosedFloatingPointRange<Double>? {
        val start = startSeconds ?: return null
        val end = endSeconds ?: return null
        return start.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { validStart -> end.takeIf { it.isFinite() && it > validStart }?.let { validStart..it } }
    }

    private suspend fun resolveInput(snapshot: SentenceAudioInputSnapshot, input: SentenceAudioInputSpec): Resolution {
        var current = resolveOne(input)
        if (current is Resolution.Ready) {
            return current
        }
        val currentFailure = (current as? Resolution.Unavailable)?.failure ?: AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE

        if (input.origin == SentenceAudioInputOrigin.EXTERNAL_AUDIO) {
            if (input.audioStreamIndex != null) {
                val unindexedExternal = input.copy(audioStreamIndex = null)
                val retryExternal = resolveOne(unindexedExternal)
                if (retryExternal is Resolution.Ready) return retryExternal
                if (retryExternal is Resolution.Unavailable) current = retryExternal
            }

            val originalFallback = SentenceAudioInputResolver.resolveOriginalVideoSpec(snapshot)
                ?.copy(audioStreamIndex = null)
            if (originalFallback != null && originalFallback.value != input.value) {
                val fallbackFailure = (current as? Resolution.Unavailable)?.failure ?: currentFailure
                record(SentenceAudioDiagnosticStage.FALLBACK_DECISION, input, SentenceAudioDiagnosticFallback.ATTEMPTED, fallbackFailure)
                val second = resolveOne(originalFallback)
                if (second is Resolution.Ready) return second
                if (second is Resolution.Unavailable) current = second
            }

            return current
        }

        if (current is Resolution.Unavailable && !current.failure.isPlayableFallbackRetryable()) {
            return current
        }

        val activeFailure = (current as? Resolution.Unavailable)?.failure ?: currentFailure

        return when (val fallback = SentenceAudioInputResolver.resolvePlayableFallback(snapshot, input)) {
            is SentenceAudioPlayableFallbackResolution.Available -> {
                record(SentenceAudioDiagnosticStage.FALLBACK_DECISION, input, SentenceAudioDiagnosticFallback.ATTEMPTED, activeFailure)
                resolveOne(fallback.input)
            }
            SentenceAudioPlayableFallbackResolution.Missing -> unavailableResolution(activeFailure, input, AnkiSentenceAudioPlayableFallback.MISSING)
            SentenceAudioPlayableFallbackResolution.SameAsOriginal -> unavailableResolution(activeFailure, input, AnkiSentenceAudioPlayableFallback.SAME_AS_ORIGINAL)
            SentenceAudioPlayableFallbackResolution.Unavailable -> unavailableResolution(activeFailure, input, AnkiSentenceAudioPlayableFallback.UNAVAILABLE)
        }
    }

    private fun unavailableResolution(
        failure: AnkiSentenceAudioFailure,
        input: SentenceAudioInputSpec,
        fallback: AnkiSentenceAudioPlayableFallback,
    ): Resolution.Unavailable {
        record(SentenceAudioDiagnosticStage.FALLBACK_DECISION, input, fallback.toDiagnosticFallback(), failure)
        return Resolution.Unavailable(failure, input.diagnostic(fallback))
    }

    private suspend fun resolveOne(input: SentenceAudioInputSpec): Resolution {
        return when (val selected = probe(input, ProbeMode.SELECTED_RESTRICTED)) {
            ProbeResult.SourceUnavailable -> Resolution.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE, input.diagnostic())
            ProbeResult.Failed -> Resolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED, input.diagnostic())
            is ProbeResult.Success -> when (SentenceAudioMediaProbe.inspectSelectedAudio(selected.output)) {
                SentenceAudioMediaProbe.AudioInspection.Readable -> Resolution.Ready(input)
                SentenceAudioMediaProbe.AudioInspection.Protected -> Resolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED, input.diagnostic())
                SentenceAudioMediaProbe.AudioInspection.StreamMissing,
                SentenceAudioMediaProbe.AudioInspection.NotAudio,
                -> resolveInventory(input)
            }
        }
    }

    private suspend fun resolveInventory(input: SentenceAudioInputSpec): Resolution {
        return when (val inventory = probe(input, ProbeMode.ALL_RESTRICTED)) {
            ProbeResult.SourceUnavailable -> Resolution.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE, input.diagnostic())
            ProbeResult.Failed -> Resolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED, input.diagnostic())
            is ProbeResult.Success -> {
                val streams = SentenceAudioMediaProbe.audioStreams(inventory.output)
                val only = streams.singleOrNull()
                when {
                    only?.index != null && !only.protected -> Resolution.Ready(input.copy(audioStreamIndex = only.index))
                    streams.size > 1 -> Resolution.Unavailable(AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE, input.diagnostic())
                    only?.protected == true -> Resolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED, input.diagnostic())
                    only != null -> Resolution.Unavailable(AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE, input.diagnostic())
                    else -> resolveDiscovery(input)
                }
            }
        }
    }

    private suspend fun resolveDiscovery(input: SentenceAudioInputSpec): Resolution {
        return when (val discovery = probe(input, ProbeMode.ALL_UNRESTRICTED_DISCOVERY)) {
            ProbeResult.SourceUnavailable -> Resolution.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE, input.diagnostic())
            ProbeResult.Failed -> Resolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED, input.diagnostic())
            is ProbeResult.Success -> when {
                SentenceAudioMediaProbe.audioStreams(discovery.output).isNotEmpty() -> Resolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_CODEC_RESTRICTED, input.diagnostic())
                input.kind == SentenceAudioInputKind.REMOTE_HTTP && input.audioStreamIndex != null -> Resolution.Ready(input)
                else -> Resolution.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND, input.diagnostic())
            }
        }
    }

    private suspend fun probe(input: SentenceAudioInputSpec, mode: ProbeMode): ProbeResult {
        val stage = mode.stage
        val lease = inputAcquirer.acquire(input) ?: run {
            record(stage, input, input.fallback(), AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE)
            return ProbeResult.SourceUnavailable
        }
        val cleanup = NativeCleanup(lease::close)
        return try {
            val result = commandExecutor.executeFfprobe(
                when (mode) {
                    ProbeMode.SELECTED_RESTRICTED -> SentenceAudioFfmpegArguments.audioProbe(input, lease.ffmpegValue, lease.tlsCaFile)
                    ProbeMode.ALL_RESTRICTED -> SentenceAudioFfmpegArguments.allAudioProbe(input, lease.ffmpegValue, lease.tlsCaFile)
                    ProbeMode.ALL_UNRESTRICTED_DISCOVERY -> SentenceAudioFfmpegArguments.audioDiscoveryProbe(input, lease.ffmpegValue, lease.tlsCaFile)
                },
                cleanup::nativeFinished,
            )
            record(stage, input, input.fallback(), AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED.takeIf { result !is SentenceAudioCommandResult.Success }, result)
            when (result) {
                is SentenceAudioCommandResult.Success -> ProbeResult.Success(result.output)
                else -> ProbeResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cleanup.nativeFinished()
            record(stage, input, input.fallback(), AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED, exceptionType = e.javaClass.name)
            ProbeResult.Failed
        } finally {
            cleanup.release()
        }
    }

    private suspend fun extract(input: SentenceAudioInputSpec, range: ClosedFloatingPointRange<Double>): AnkiSentenceAudioPreparation {
        val primary = executeOnce(input, range)
        if (primary is AnkiSentenceAudioPreparation.Ready) return primary

        if (primary is AnkiSentenceAudioPreparation.Unavailable &&
            primary.failure == AnkiSentenceAudioFailure.EXTRACTION_STREAM_MAPPING_FAILED &&
            input.audioStreamIndex != null
        ) {
            val fallbackInput = input.copy(audioStreamIndex = null)
            val retry = executeOnce(fallbackInput, range)
            if (retry is AnkiSentenceAudioPreparation.Ready) return retry
        }

        return primary
    }

    private suspend fun executeOnce(input: SentenceAudioInputSpec, range: ClosedFloatingPointRange<Double>): AnkiSentenceAudioPreparation {
        val lease = inputAcquirer.acquire(input) ?: return unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE, input, SentenceAudioDiagnosticStage.AUDIO_EXTRACTION)
        val output = File(cacheDirectory, "chimahon_sentence_audio_${System.nanoTime()}.m4a")
        val inputCleanup = NativeCleanup(lease::close)
        val outputCleanup = NativeCleanup(output::delete)
        return try {
            output.delete()
            val result = commandExecutor.executeFfmpeg(
                SentenceAudioFfmpegArguments.sentenceAudio(input, lease.ffmpegValue, range.start, range.endInclusive, output.absolutePath, lease.tlsCaFile),
            ) { inputCleanup.nativeFinished(); outputCleanup.nativeFinished() }
            val failure = result.extractionFailure()
            record(SentenceAudioDiagnosticStage.AUDIO_EXTRACTION, input, input.fallback(), failure, result)
            if (failure != null) return unavailable(failure, input)
            if (!output.isFile || output.length() == 0L) return unavailable(AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_MISSING, input, SentenceAudioDiagnosticStage.OUTPUT_VALIDATION, result)
            val bytes = try { output.readBytes() } catch (e: Exception) {
                return unavailable(AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_READ_FAILED, input, SentenceAudioDiagnosticStage.OUTPUT_READ, result, e.javaClass.name)
            }
            AnkiSentenceAudioPreparation.Ready(AnkiSentenceAudioSource.fromBytes(bytes, "m4a"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            inputCleanup.nativeFinished()
            outputCleanup.nativeFinished()
            unavailable(AnkiSentenceAudioFailure.EXTRACTION_FAILED, input, SentenceAudioDiagnosticStage.AUDIO_EXTRACTION, exceptionType = e.javaClass.name)
        } finally {
            inputCleanup.release()
            outputCleanup.release()
        }
    }

    private fun SentenceAudioCommandResult.extractionFailure(): AnkiSentenceAudioFailure? = when (this) {
        is SentenceAudioCommandResult.Success -> null
        SentenceAudioCommandResult.Failed -> AnkiSentenceAudioFailure.EXTRACTION_FAILED
        is SentenceAudioCommandResult.FfmpegFailed -> when (failure) {
            SentenceAudioFfmpegFailure.STREAM_MAPPING -> AnkiSentenceAudioFailure.EXTRACTION_STREAM_MAPPING_FAILED
            SentenceAudioFfmpegFailure.SOURCE_READ -> AnkiSentenceAudioFailure.EXTRACTION_SOURCE_READ_FAILED
            SentenceAudioFfmpegFailure.SEEK -> AnkiSentenceAudioFailure.EXTRACTION_SEEK_FAILED
            SentenceAudioFfmpegFailure.OUTPUT_WRITE -> AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_WRITE_FAILED
            SentenceAudioFfmpegFailure.UNKNOWN -> AnkiSentenceAudioFailure.EXTRACTION_FAILED
        }
    }

    private fun unavailable(
        failure: AnkiSentenceAudioFailure,
        input: SentenceAudioInputSpec?,
        stage: SentenceAudioDiagnosticStage? = null,
        result: SentenceAudioCommandResult? = null,
        exceptionType: String? = null,
    ): AnkiSentenceAudioPreparation.Unavailable {
        stage?.let { record(it, input, input?.fallback() ?: SentenceAudioDiagnosticFallback.NOT_APPLICABLE, failure, result, exceptionType) }
        return AnkiSentenceAudioPreparation.Unavailable(failure, input?.diagnostic())
    }

    private fun record(stage: SentenceAudioDiagnosticStage, input: SentenceAudioInputSpec?, fallback: SentenceAudioDiagnosticFallback, failure: AnkiSentenceAudioFailure?, result: SentenceAudioCommandResult? = null, exceptionType: String? = null) {
        diagnosticLogger.record(SentenceAudioDiagnosticEvent(stage, input, fallback, failure, result, exceptionType))
    }

    private fun SentenceAudioInputSpec.diagnostic(fallback: AnkiSentenceAudioPlayableFallback? = null) = AnkiSentenceAudioDiagnostic(
        inputSource = when (origin) {
            SentenceAudioInputOrigin.ORIGINAL_VIDEO -> AnkiSentenceAudioInputSource.ORIGINAL_VIDEO
            SentenceAudioInputOrigin.PLAYABLE_VIDEO -> AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO
            SentenceAudioInputOrigin.EXTERNAL_AUDIO -> AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO
        },
        playableFallback = fallback,
    )
    private fun SentenceAudioInputSpec.fallback() = if (origin == SentenceAudioInputOrigin.PLAYABLE_VIDEO) SentenceAudioDiagnosticFallback.ATTEMPTED else SentenceAudioDiagnosticFallback.NOT_APPLICABLE
    private fun AnkiSentenceAudioPlayableFallback.toDiagnosticFallback() = when (this) {
        AnkiSentenceAudioPlayableFallback.MISSING -> SentenceAudioDiagnosticFallback.MISSING
        AnkiSentenceAudioPlayableFallback.SAME_AS_ORIGINAL -> SentenceAudioDiagnosticFallback.SAME_AS_ORIGINAL
        AnkiSentenceAudioPlayableFallback.UNAVAILABLE -> SentenceAudioDiagnosticFallback.UNAVAILABLE
    }
    private fun AnkiSentenceAudioFailure.isPlayableFallbackRetryable() = this == AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE || this == AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED || this == AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND

    private sealed interface Resolution {
        data class Ready(val input: SentenceAudioInputSpec) : Resolution
        data class Unavailable(val failure: AnkiSentenceAudioFailure, val diagnostic: AnkiSentenceAudioDiagnostic?) : Resolution
    }
    private enum class ProbeMode(val stage: SentenceAudioDiagnosticStage) {
        SELECTED_RESTRICTED(SentenceAudioDiagnosticStage.SELECTED_AUDIO_PROBE),
        ALL_RESTRICTED(SentenceAudioDiagnosticStage.ALL_AUDIO_PROBE),
        ALL_UNRESTRICTED_DISCOVERY(SentenceAudioDiagnosticStage.AUDIO_DISCOVERY_PROBE),
    }
    private sealed interface ProbeResult { data object SourceUnavailable : ProbeResult; data object Failed : ProbeResult; data class Success(val output: String) : ProbeResult }
}

/** Defers a resource release until FFmpegKit reports that its native work is no longer using it. */
private class NativeCleanup(private val action: () -> Unit) {
    private val lock = Any()
    private var nativeFinished = false
    private var releaseRequested = false
    private var released = false
    fun nativeFinished() = synchronized(lock) { nativeFinished = true; releaseIfReady() }
    fun release() = synchronized(lock) { releaseRequested = true; releaseIfReady() }
    private fun releaseIfReady() { if (nativeFinished && releaseRequested && !released) { released = true; action() } }
}
