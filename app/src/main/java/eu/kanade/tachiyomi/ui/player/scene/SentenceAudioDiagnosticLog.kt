package eu.kanade.tachiyomi.ui.player.scene

import chimahon.anki.AnkiSentenceAudioFailure
import com.hippo.unifile.UniFile
import logcat.LogPriority
import logcat.logcat
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.math.max

/**
 * Temporary diagnostic journal for sentence-audio failures.
 *
 * It is written to the existing `logs` directory below the storage folder selected by the user.
 * It intentionally records only source metadata and redacted native diagnostics. It must be
 * removed after the current stream-export investigation is complete.
 */
internal enum class SentenceAudioDiagnosticStage {
    REQUEST_VALIDATION,
    FALLBACK_DECISION,
    SELECTED_AUDIO_PROBE,
    ALL_AUDIO_PROBE,
    AUDIO_DISCOVERY_PROBE,
    AUDIO_EXTRACTION,
    OUTPUT_VALIDATION,
    OUTPUT_READ,
}

internal enum class SentenceAudioDiagnosticFallback {
    NOT_APPLICABLE,
    MISSING,
    SAME_AS_ORIGINAL,
    UNAVAILABLE,
    ATTEMPTED,
}

internal data class SentenceAudioDiagnosticEvent(
    val stage: SentenceAudioDiagnosticStage,
    val input: SceneVideoInputSpec?,
    val fallback: SentenceAudioDiagnosticFallback,
    val failure: AnkiSentenceAudioFailure? = null,
    val result: SceneCommandResult? = null,
    val exceptionType: String? = null,
)

internal fun interface SentenceAudioDiagnosticLogger {
    fun record(event: SentenceAudioDiagnosticEvent)
}

internal object NoOpSentenceAudioDiagnosticLogger : SentenceAudioDiagnosticLogger {
    override fun record(event: SentenceAudioDiagnosticEvent) = Unit
}

internal fun createSentenceAudioDiagnosticLogger(
    storageManager: StorageManager = Injekt.get(),
): SentenceAudioDiagnosticLogger {
    return StorageFolderSentenceAudioDiagnosticLogger(
        directory = storageManager::getLogsDirectory,
        onWriteFailure = { error ->
            logcat("SentenceAudioDiagnostic", LogPriority.ERROR) {
                "Sentence-audio diagnostic log could not be written to the selected storage folder: ${error.javaClass.simpleName}"
            }
        },
    )
}

internal class StorageFolderSentenceAudioDiagnosticLogger(
    private val directory: () -> UniFile?,
    private val maxBytes: Int = MAX_LOG_BYTES,
    private val onWriteFailure: (Throwable) -> Unit = {},
) : SentenceAudioDiagnosticLogger {
    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    override fun record(event: SentenceAudioDiagnosticEvent) {
        runCatching {
            val entry = SentenceAudioDiagnosticJournal.render(event).toByteArray(StandardCharsets.UTF_8)
            synchronized(lock) {
                val folder = directory()
                    ?: throw IOException("The selected storage folder is unavailable")
                val file = folder.findFile(FILE_NAME)
                    ?: folder.createFile(FILE_NAME)
                    ?: throw IOException("Could not create the sentence-audio diagnostic log")
                val existing = file.openInputStream().use { it.readBytes() }
                file.openOutputStream().use { output ->
                    output.write(SentenceAudioDiagnosticJournal.retain(existing, entry, maxBytes))
                }
            }
        }.onFailure(onWriteFailure)
    }

    private companion object {
        val lock = Any()
    }
}

internal object SentenceAudioDiagnosticJournal {
    fun retain(existing: ByteArray, entry: ByteArray, maxBytes: Int): ByteArray {
        val combined = existing + entry
        return if (combined.size <= maxBytes) {
            combined
        } else {
            combined.copyOfRange(max(0, combined.size - maxBytes), combined.size)
        }
    }

    fun render(event: SentenceAudioDiagnosticEvent): String {
        return buildString {
            appendLine("recorded_at_utc=${System.currentTimeMillis()}")
            appendLine("stage=${event.stage.name}")
            event.input?.let { input ->
                appendLine("input_source=${input.origin.name}")
                appendLine("input_kind=${input.kind.name}")
                appendLine("audio_stream_index=${input.audioStreamIndex ?: "none"}")
            }
            appendLine("fallback=${event.fallback.name}")
            event.failure?.let { appendLine("failure=${it.name}") }
            event.result?.let { appendResult(it) }
            event.exceptionType?.let { appendLine("exception_type=$it") }
            appendLine("---")
        }
    }

    private fun StringBuilder.appendResult(result: SceneCommandResult) {
        when (result) {
            is SceneCommandResult.Success -> appendLine("command_result=SUCCESS")
            SceneCommandResult.Failed -> appendLine("command_result=EXECUTION_FAILED")
            is SceneCommandResult.FfmpegFailed -> {
                appendLine("command_result=NATIVE_FAILED")
                appendLine("native_failure=${result.failure.name}")
                result.nativeDiagnostics?.let { details ->
                    details.returnCode?.let { appendLine("return_code=$it") }
                    val detail = sequenceOf(details.failStackTrace, details.logs)
                        .filterNotNull()
                        .joinToString(separator = "\n")
                        .redactNativeDiagnostics()
                        .takeLast(MAX_NATIVE_DIAGNOSTIC_CHARS)
                        .trim()
                    if (detail.isNotEmpty()) {
                        appendLine("native_diagnostic_begin")
                        appendLine(detail)
                        appendLine("native_diagnostic_end")
                    }
                }
            }
        }
    }

    private fun String.redactNativeDiagnostics(): String {
        return replace(URL_PATTERN, "<redacted-url>")
            .replace(SENSITIVE_QUERY_PATTERN, "${'$'}1=<redacted>")
            .replace(SENSITIVE_HEADER_PATTERN, "${'$'}1<redacted>")
            .replace(LOCAL_PATH_PATTERN, "<redacted-path>")
    }
}

private const val FILE_NAME = "chimahon_sentence_audio_debug.log"
private const val MAX_LOG_BYTES = 64 * 1024
private const val MAX_NATIVE_DIAGNOSTIC_CHARS = 32 * 1024
private val URL_PATTERN = Regex("""(?i)\b(?:https?|file)://[^\s\"'<>]+""")
private val SENSITIVE_QUERY_PATTERN = Regex(
    """(?i)\b(access_token|api_key|auth|authorization|credential|credentials|key|policy|signature|signed|sig|token|x-amz-[^=\s]+|x-goog-[^=\s]+)=([^&\s]+)""",
)
private val SENSITIVE_HEADER_PATTERN = Regex(
    """(?im)^((?:authorization|cookie|referer|origin|user-agent|accept(?:-[a-z-]+)?|cache-control|pragma|proxy-authorization|x-[a-z0-9-]+)\s*:\s*).*$""",
)
private val LOCAL_PATH_PATTERN = Regex("""(?i)(?:[a-z]:\\|/(?:data|storage|sdcard|mnt|cache|files)/)[^\s\"'<>]+""")
