package eu.kanade.tachiyomi.ui.player.sentenceaudio

import chimahon.anki.AnkiSentenceAudioFailure
import eu.kanade.tachiyomi.BuildConfig
import kotlin.math.max
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

internal enum class SentenceAudioDiagnosticStage { REQUEST_VALIDATION, FALLBACK_DECISION, SELECTED_AUDIO_PROBE, ALL_AUDIO_PROBE, AUDIO_DISCOVERY_PROBE, AUDIO_EXTRACTION, OUTPUT_VALIDATION, OUTPUT_READ }
internal enum class SentenceAudioDiagnosticFallback { NOT_APPLICABLE, MISSING, SAME_AS_ORIGINAL, UNAVAILABLE, ATTEMPTED }
internal data class SentenceAudioDiagnosticEvent(
    val stage: SentenceAudioDiagnosticStage,
    val input: SentenceAudioInputSpec?,
    val fallback: SentenceAudioDiagnosticFallback,
    val failure: AnkiSentenceAudioFailure? = null,
    val result: SentenceAudioCommandResult? = null,
    val exceptionType: String? = null,
)
internal fun interface SentenceAudioDiagnosticLogger { fun record(event: SentenceAudioDiagnosticEvent) }
internal object NoOpSentenceAudioDiagnosticLogger : SentenceAudioDiagnosticLogger { override fun record(event: SentenceAudioDiagnosticEvent) = Unit }
internal class LogcatSentenceAudioDiagnosticLogger : SentenceAudioDiagnosticLogger {
    override fun record(event: SentenceAudioDiagnosticEvent) {
        logcat(LogPriority.INFO) {
            "[sentence-audio] ${SentenceAudioDiagnosticJournal.render(event)}"
        }
    }
}

internal fun createSentenceAudioDiagnosticLogger(): SentenceAudioDiagnosticLogger =
    if (BuildConfig.DEBUG) LogcatSentenceAudioDiagnosticLogger() else NoOpSentenceAudioDiagnosticLogger

internal object SentenceAudioDiagnosticJournal {
    fun retain(existing: ByteArray, entry: ByteArray, maxBytes: Int = maxLogBytes): ByteArray {
        val combined = existing + entry
        return if (combined.size <= maxBytes) combined else combined.copyOfRange(max(0, combined.size - maxBytes), combined.size)
    }
    fun render(event: SentenceAudioDiagnosticEvent): String = buildString {
        appendLine("recorded_at_utc=${System.currentTimeMillis()}"); appendLine("stage=${event.stage.name}")
        event.input?.let {
            appendLine("input_source=${it.origin.name}")
            appendLine("input_kind=${it.kind.name}")
            appendLine("audio_stream_index=${it.audioStreamIndex ?: "none"}")
            appendLine("input_value_sanitized=${SentenceAudioInputResolver.sanitizeForLog(it.value)}")
        }
        appendLine("fallback=${event.fallback.name}"); event.failure?.let { appendLine("failure=${it.name}") }; event.result?.let { appendResult(it) }; event.exceptionType?.let { appendLine("exception_type=$it") }; appendLine("---")
    }
    private fun StringBuilder.appendResult(result: SentenceAudioCommandResult) = when (result) {
        is SentenceAudioCommandResult.Success -> appendLine("command_result=SUCCESS")
        SentenceAudioCommandResult.Failed -> appendLine("command_result=EXECUTION_FAILED")
        is SentenceAudioCommandResult.FfmpegFailed -> { appendLine("command_result=NATIVE_FAILED"); appendLine("native_failure=${result.failure.name}"); result.nativeDiagnostics?.let { d -> d.returnCode?.let { appendLine("return_code=$it") }; redact(sequenceOf(d.failStackTrace, d.logs).filterNotNull().joinToString("\n")).takeLast(maxNativeDiagnosticChars).trim().takeIf(String::isNotEmpty)?.let { appendLine("native_diagnostic_begin"); appendLine(it); appendLine("native_diagnostic_end") } } }
    }
    internal fun redact(value: String): String = value.replace(urlPattern, "<redacted-url>").replace(sensitiveQueryPattern, "${'$'}1=<redacted>").replace(sensitiveHeaderPattern, "${'$'}1<redacted>").replace(localPathPattern, "<redacted-path>")
    const val maxLogBytes = 64 * 1024
    private const val maxNativeDiagnosticChars = 32 * 1024
    private val urlPattern = Regex("""(?i)\b(?:https?|file)://[^\s"'<>]+""")
    private val sensitiveQueryPattern = Regex("""(?i)\b(access_token|api_key|auth|authorization|credential|credentials|key|policy|signature|signed|sig|token|x-amz-[^=\s]+|x-goog-[^=\s]+)=([^&\s]+)""")
    private val sensitiveHeaderPattern = Regex("""(?im)^((?:authorization|cookie|referer|origin|user-agent|accept(?:-[a-z-]+)?|cache-control|pragma|proxy-authorization|x-[a-z0-9-]+)\s*:\s*).*$""")
    private val localPathPattern = Regex("""(?i)(?:[a-z]:\\|/(?:data|storage|sdcard|mnt|cache|files)/)[^\s"'<>]+""")
}
