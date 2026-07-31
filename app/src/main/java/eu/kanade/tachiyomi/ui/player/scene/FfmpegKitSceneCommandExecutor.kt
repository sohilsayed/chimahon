package eu.kanade.tachiyomi.ui.player.scene

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

internal sealed interface SceneCommandResult {
    data class Success(val output: String = "") : SceneCommandResult

    data class FfmpegFailed(
        val failure: SceneFfmpegFailure,
        val nativeDiagnostics: SceneNativeFailureDiagnostics? = null,
    ) : SceneCommandResult

    data object Failed : SceneCommandResult
}

/**
 * Raw native details remain in-memory until the temporary, app-private sentence-audio journal
 * redacts them. They must never be passed to UI warnings or regular application logs.
 */
internal data class SceneNativeFailureDiagnostics(
    val returnCode: Int?,
    val failStackTrace: String?,
    val logs: String?,
)

internal enum class SceneFfmpegFailure {
    STREAM_MAPPING,
    SOURCE_READ,
    SEEK,
    OUTPUT_WRITE,
    UNKNOWN,
}

/**
 * Converts untrusted native diagnostics into a closed, user-safe category. Raw logs may include
 * URLs or request headers, so they are never exposed to the UI; the temporary sentence-audio
 * journal is responsible for redacting any diagnostic detail it writes.
 */
internal fun classifySceneFfmpegFailure(
    failStackTrace: String?,
    logs: String?,
): SceneFfmpegFailure {
    val detail = sequenceOf(failStackTrace, logs)
        .filterNotNull()
        .joinToString(separator = "\n")
        .lowercase()

    return when {
        "stream map" in detail && "matches no streams" in detail -> {
            SceneFfmpegFailure.STREAM_MAPPING
        }
        "could not seek" in detail || "failed to seek" in detail || "invalid seek" in detail -> {
            SceneFfmpegFailure.SEEK
        }
        "could not write header for output" in detail ||
            "error opening output" in detail ||
            "failed to avio_open" in detail ||
            "error writing trailer" in detail ||
            "error muxing a packet" in detail -> {
            SceneFfmpegFailure.OUTPUT_WRITE
        }
        "http error" in detail ||
            "server returned" in detail ||
            "failed to open segment" in detail ||
            "error when loading first segment" in detail ||
            "unable to open resource" in detail ||
            "connection refused" in detail ||
            "connection reset by peer" in detail ||
            "network is unreachable" in detail ||
            "connection timed out" in detail -> {
            SceneFfmpegFailure.SOURCE_READ
        }
        else -> SceneFfmpegFailure.UNKNOWN
    }
}

internal interface SceneCommandExecutor {
    suspend fun executeFfmpeg(
        arguments: Array<String>,
        onNativeFinished: () -> Unit = {},
    ): SceneCommandResult

    suspend fun executeFfprobe(
        arguments: Array<String>,
        onNativeFinished: () -> Unit = {},
    ): SceneCommandResult
}

/**
 * Releases an input or output only after both its Kotlin owner and native FFmpeg are done with it.
 */
internal class SceneNativeCleanup(
    private val cleanup: () -> Unit,
) {
    private val nativeFinished = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val cleaned = AtomicBoolean(false)

    fun nativeFinished() {
        nativeFinished.set(true)
        cleanIfReady()
    }

    fun release() {
        released.set(true)
        cleanIfReady()
    }

    private fun cleanIfReady() {
        if (nativeFinished.get() && released.get() && cleaned.compareAndSet(false, true)) {
            runCatching(cleanup)
        }
    }
}

internal class FfmpegKitSceneCommandExecutor : SceneCommandExecutor {
    override suspend fun executeFfmpeg(
        arguments: Array<String>,
        onNativeFinished: () -> Unit,
    ): SceneCommandResult {
        return executeSession(
            createSession = {
                FFmpegSession.create(
                    arguments,
                    {},
                    DISCARD_LOG_CALLBACK,
                    DISCARD_STATISTICS_CALLBACK,
                    LogRedirectionStrategy.NEVER_PRINT_LOGS,
                )
            },
            runSession = FFmpegKitConfig::ffmpegExecute,
            cancelSession = FFmpegSession::cancel,
            resultFor = { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    SceneCommandResult.Success()
                } else {
                    val diagnostics = SceneNativeFailureDiagnostics(
                        returnCode = session.returnCode?.getValue(),
                        failStackTrace = session.failStackTrace,
                        logs = session.getAllLogsAsString(FAILURE_LOG_WAIT_MILLIS),
                    )
                    SceneCommandResult.FfmpegFailed(
                        classifySceneFfmpegFailure(
                            failStackTrace = diagnostics.failStackTrace,
                            logs = diagnostics.logs,
                        ),
                        nativeDiagnostics = diagnostics,
                    )
                }
            },
            onNativeFinished = onNativeFinished,
        )
    }

    override suspend fun executeFfprobe(
        arguments: Array<String>,
        onNativeFinished: () -> Unit,
    ): SceneCommandResult {
        return executeSession(
            createSession = {
                FFprobeSession.create(
                    arguments,
                    {},
                    DISCARD_LOG_CALLBACK,
                    LogRedirectionStrategy.NEVER_PRINT_LOGS,
                )
            },
            runSession = FFmpegKitConfig::ffprobeExecute,
            cancelSession = FFprobeSession::cancel,
            resultFor = { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    SceneCommandResult.Success(session.output.orEmpty())
                } else {
                    val diagnostics = SceneNativeFailureDiagnostics(
                        returnCode = session.returnCode?.getValue(),
                        failStackTrace = session.failStackTrace,
                        logs = session.getAllLogsAsString(FAILURE_LOG_WAIT_MILLIS),
                    )
                    SceneCommandResult.FfmpegFailed(
                        classifySceneFfmpegFailure(
                            failStackTrace = diagnostics.failStackTrace,
                            logs = diagnostics.logs,
                        ),
                        nativeDiagnostics = diagnostics,
                    )
                }
            },
            onNativeFinished = onNativeFinished,
        )
    }

    /**
     * FFmpegKit's Future can report cancellation after its Runnable has started. Dispatch the
     * synchronous API ourselves so QUEUED -> CANCELLED proves native code never ran, while a
     * RUNNING cancellation calls the native cancel hook. Cleanup is notified only after the
     * synchronous call actually returns.
     */
    private suspend fun <Session : Any> executeSession(
        createSession: () -> Session,
        runSession: (Session) -> Unit,
        cancelSession: (Session) -> Unit,
        resultFor: (Session) -> SceneCommandResult,
        onNativeFinished: () -> Unit,
    ): SceneCommandResult {
        return suspendCancellableCoroutine { continuation ->
            val state = AtomicReference(SessionExecutionState.QUEUED)
            val finishCalled = AtomicBoolean(false)
            fun finishNativeUse() {
                if (finishCalled.compareAndSet(false, true)) {
                    runCatching(onNativeFinished)
                }
            }

            val session = try {
                createSession()
            } catch (_: Exception) {
                state.set(SessionExecutionState.FINISHED)
                finishNativeUse()
                continuation.resume(SceneCommandResult.Failed)
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                if (!state.compareAndSet(SessionExecutionState.QUEUED, SessionExecutionState.CANCELLED) &&
                    state.get() == SessionExecutionState.RUNNING
                ) {
                    runCatching { cancelSession(session) }
                }
            }

            try {
                Dispatchers.IO.dispatch(
                    continuation.context,
                    Runnable {
                        if (!state.compareAndSet(SessionExecutionState.QUEUED, SessionExecutionState.RUNNING)) {
                            if (state.compareAndSet(
                                    SessionExecutionState.CANCELLED,
                                    SessionExecutionState.FINISHED,
                                )
                            ) {
                                finishNativeUse()
                            }
                            return@Runnable
                        }

                        val result = try {
                            runSession(session)
                            runCatching { resultFor(session) }
                                .getOrDefault(SceneCommandResult.Failed)
                        } catch (_: Exception) {
                            SceneCommandResult.Failed
                        } finally {
                            state.set(SessionExecutionState.FINISHED)
                            finishNativeUse()
                        }
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    },
                )
            } catch (_: Exception) {
                if (state.getAndSet(SessionExecutionState.FINISHED) == SessionExecutionState.RUNNING) {
                    runCatching { cancelSession(session) }
                }
                finishNativeUse()
                if (continuation.isActive) {
                    continuation.resume(SceneCommandResult.Failed)
                }
            }
        }
    }

    private enum class SessionExecutionState {
        QUEUED,
        RUNNING,
        CANCELLED,
        FINISHED,
    }

    private companion object {
        // Temporary diagnostic window: only native failures wait for late FFmpeg/FFprobe lines.
        const val FAILURE_LOG_WAIT_MILLIS = 1_000
        val DISCARD_LOG_CALLBACK = LogCallback {}
        val DISCARD_STATISTICS_CALLBACK = StatisticsCallback {}
    }
}
