package chimahon.anki

internal enum class AnkiAddTimingStage {
    STARTED,
    CONFIGURATION_READY,
    PREFLIGHT_FINISHED,
    SCREENSHOT_PREPARED,
    SENTENCE_AUDIO_PREPARED,
    WORD_AUDIO_PREPARED,
    FIELDS_RENDERED,
    TAGS_RENDERED,
    DICTIONARY_MEDIA_PREPARED,
    MEDIA_PREPARED,
    FINAL_DUPLICATE_FINISHED,
    MEDIA_STORED,
    FIELDS_RESOLVED,
    NOTE_SAVED,
    STATS_RECORDED,
    SYNC_DISPATCHED,
    COMPLETED,
}

internal data class AnkiAddTimingEvent(
    val operationId: String,
    val stage: AnkiAddTimingStage,
    val elapsedMillis: Long,
    val fieldCount: Int? = null,
    val tagCount: Int? = null,
    val dictionaryMediaCount: Int? = null,
    val mediaBytes: Int? = null,
    val outcome: String? = null,
)

internal class AnkiAddTimingTrace(
    private val operationId: String,
    private val nowMillis: () -> Long,
    private val emit: (AnkiAddTimingEvent) -> Unit,
    startedAtMillis: Long? = null,
) {
    private val startedAt = startedAtMillis ?: nowMillis()

    fun mark(
        stage: AnkiAddTimingStage,
        fieldCount: Int? = null,
        tagCount: Int? = null,
        dictionaryMediaCount: Int? = null,
        mediaBytes: Int? = null,
        outcome: String? = null,
    ) {
        emit(
            AnkiAddTimingEvent(
                operationId = operationId,
                stage = stage,
                elapsedMillis = (nowMillis() - startedAt).coerceAtLeast(0L),
                fieldCount = fieldCount,
                tagCount = tagCount,
                dictionaryMediaCount = dictionaryMediaCount,
                mediaBytes = mediaBytes,
                outcome = outcome,
            ),
        )
    }
}

internal fun formatAnkiAddTimingEvent(event: AnkiAddTimingEvent): String = buildString {
    append("anki_add operation=${event.operationId} stage=${event.stage.name.lowercase()} elapsed_ms=${event.elapsedMillis}")
    event.fieldCount?.let { append(" fields=$it") }
    event.tagCount?.let { append(" tags=$it") }
    event.dictionaryMediaCount?.let { append(" dictionary_media=$it") }
    event.mediaBytes?.let { append(" media_bytes=$it") }
    event.outcome?.let { append(" outcome=$it") }
}
