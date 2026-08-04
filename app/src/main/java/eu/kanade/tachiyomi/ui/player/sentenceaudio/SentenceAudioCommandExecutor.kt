package eu.kanade.tachiyomi.ui.player.sentenceaudio

import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeSession
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.LogRedirectionStrategy
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.StatisticsCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

internal sealed interface SentenceAudioCommandResult {
    data class Success(val output: String = "") : SentenceAudioCommandResult
    data class FfmpegFailed(val failure: SentenceAudioFfmpegFailure, val nativeDiagnostics: SentenceAudioNativeFailureDiagnostics? = null) : SentenceAudioCommandResult
    data object Failed : SentenceAudioCommandResult
}

internal data class SentenceAudioNativeFailureDiagnostics(val returnCode: Int?, val failStackTrace: String?, val logs: String?)
internal enum class SentenceAudioFfmpegFailure { STREAM_MAPPING, SOURCE_READ, SEEK, OUTPUT_WRITE, UNKNOWN }

internal fun classifySentenceAudioFfmpegFailure(failStackTrace: String?, logs: String?): SentenceAudioFfmpegFailure {
    val detail = sequenceOf(failStackTrace, logs).filterNotNull().joinToString("\n").lowercase()
    return when {
        "stream map" in detail && "matches no streams" in detail -> SentenceAudioFfmpegFailure.STREAM_MAPPING
        "could not seek" in detail || "failed to seek" in detail || "invalid seek" in detail -> SentenceAudioFfmpegFailure.SEEK
        "could not write header for output" in detail || "error opening output" in detail || "failed to avio_open" in detail || "error writing trailer" in detail || "error muxing a packet" in detail -> SentenceAudioFfmpegFailure.OUTPUT_WRITE
        "http error" in detail || "server returned" in detail || "failed to open segment" in detail || "error when loading first segment" in detail || "unable to open resource" in detail || "connection refused" in detail || "connection reset by peer" in detail || "network is unreachable" in detail || "connection timed out" in detail -> SentenceAudioFfmpegFailure.SOURCE_READ
        else -> SentenceAudioFfmpegFailure.UNKNOWN
    }
}

internal interface SentenceAudioCommandExecutor {
    suspend fun executeFfmpeg(arguments: Array<String>, onNativeFinished: () -> Unit = {}): SentenceAudioCommandResult
    suspend fun executeFfprobe(arguments: Array<String>, onNativeFinished: () -> Unit = {}): SentenceAudioCommandResult
}

internal class FfmpegKitSentenceAudioCommandExecutor : SentenceAudioCommandExecutor {
    override suspend fun executeFfmpeg(arguments: Array<String>, onNativeFinished: () -> Unit): SentenceAudioCommandResult =
        executeSession(
            createSession = { FFmpegSession.create(arguments, {}, discardLogCallback, discardStatisticsCallback, LogRedirectionStrategy.NEVER_PRINT_LOGS) },
            runSession = FFmpegKitConfig::ffmpegExecute,
            cancelSession = FFmpegSession::cancel,
            resultFor = { session -> result(session.returnCode, session.failStackTrace, session.getAllLogsAsString(failureLogWaitMillis)) },
            onNativeFinished = onNativeFinished,
        )

    override suspend fun executeFfprobe(arguments: Array<String>, onNativeFinished: () -> Unit): SentenceAudioCommandResult =
        executeSession(
            createSession = { FFprobeSession.create(arguments, {}, discardLogCallback, LogRedirectionStrategy.NEVER_PRINT_LOGS) },
            runSession = FFmpegKitConfig::ffprobeExecute,
            cancelSession = FFprobeSession::cancel,
            resultFor = { session ->
                if (ReturnCode.isSuccess(session.returnCode)) SentenceAudioCommandResult.Success(session.output.orEmpty())
                else result(session.returnCode, session.failStackTrace, session.getAllLogsAsString(failureLogWaitMillis))
            },
            onNativeFinished = onNativeFinished,
        )

    private fun result(returnCode: ReturnCode?, trace: String?, logs: String?): SentenceAudioCommandResult =
        if (ReturnCode.isSuccess(returnCode)) SentenceAudioCommandResult.Success() else {
            val diagnostics = SentenceAudioNativeFailureDiagnostics(returnCode?.value, trace, logs)
            SentenceAudioCommandResult.FfmpegFailed(classifySentenceAudioFfmpegFailure(trace, logs), diagnostics)
        }

    private suspend fun <Session : Any> executeSession(
        createSession: () -> Session,
        runSession: (Session) -> Unit,
        cancelSession: (Session) -> Unit,
        resultFor: (Session) -> SentenceAudioCommandResult,
        onNativeFinished: () -> Unit,
    ): SentenceAudioCommandResult = suspendCancellableCoroutine { continuation ->
        val state = AtomicReference(State.QUEUED)
        val finishCalled = AtomicBoolean(false)
        fun finish() { if (finishCalled.compareAndSet(false, true)) runCatching(onNativeFinished) }
        val session = try { createSession() } catch (_: Exception) {
            state.set(State.FINISHED); finish(); continuation.resume(SentenceAudioCommandResult.Failed); return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation {
            if (!state.compareAndSet(State.QUEUED, State.CANCELLED) && state.get() == State.RUNNING) runCatching { cancelSession(session) }
        }
        try {
            Dispatchers.IO.dispatch(continuation.context, Runnable {
                if (!state.compareAndSet(State.QUEUED, State.RUNNING)) {
                    if (state.compareAndSet(State.CANCELLED, State.FINISHED)) finish()
                    return@Runnable
                }
                val result = try { runSession(session); runCatching { resultFor(session) }.getOrDefault(SentenceAudioCommandResult.Failed) } catch (_: Exception) { SentenceAudioCommandResult.Failed } finally { state.set(State.FINISHED); finish() }
                if (continuation.isActive) continuation.resume(result)
            })
        } catch (_: Exception) {
            if (state.getAndSet(State.FINISHED) == State.RUNNING) runCatching { cancelSession(session) }
            finish()
            if (continuation.isActive) continuation.resume(SentenceAudioCommandResult.Failed)
        }
    }

    private enum class State { QUEUED, RUNNING, CANCELLED, FINISHED }
    private companion object {
        const val failureLogWaitMillis = 1_000
        val discardLogCallback = LogCallback {}
        val discardStatisticsCallback = StatisticsCallback {}
    }
}
