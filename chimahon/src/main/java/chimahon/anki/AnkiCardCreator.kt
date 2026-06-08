package chimahon.anki

import android.content.Context
import chimahon.Cloze
import chimahon.DictionaryStyle
import chimahon.GlossaryEntry
import chimahon.LookupResult
import chimahon.MediaInfo
import chimahon.PitchEntry
import chimahon.audio.WordAudioResult
import chimahon.audio.WordAudioService
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// =============================================================================
// Legacy FieldType enum for UI backwards compatibility
// TODO: Migrate UI to use Marker strings
// =============================================================================

enum class FieldType {
    NONE,
    TARGET_WORD,
    READING,
    FURIGANA,
    DEFINITION,
    SENTENCE,
    FREQUENCY,
    PITCH_ACCENT,
    ;

    companion object {
        val DISPLAY_VALUES = entries.filter { it != NONE }

        fun fromKey(key: String): FieldType =
            entries.find { it.name == key } ?: NONE
    }
}

// =============================================================================
// Marker constants — mirrors Yomitan's getStandardFieldMarkers("term")
// =============================================================================

object Marker {
    const val EXPRESSION = "expression"
    const val READING = "reading"
    const val FURIGANA = "furigana"
    const val FURIGANA_PLAIN = "furigana-plain"
    const val AUDIO = "audio"
    const val GLOSSARY = "glossary"
    const val GLOSSARY_BRIEF = "glossary-brief"
    const val GLOSSARY_PLAIN = "glossary-plain"
    const val GLOSSARY_PLAIN_NO_DICT = "glossary-plain-no-dictionary"
    const val GLOSSARY_NO_DICT = "glossary-no-dictionary"
    const val GLOSSARY_FIRST = "glossary-first"
    const val GLOSSARY_FIRST_NO_DICT = "glossary-first-no-dictionary"
    const val GLOSSARY_FIRST_BRIEF = "glossary-first-brief"
    const val SENTENCE = "sentence"
    const val SENTENCE_BOLD = "sentence-bold"
    const val CLOZE_PREFIX = "cloze-prefix"
    const val CLOZE_BODY = "cloze-body"
    const val CLOZE_BODY_KANA = "cloze-body-kana"
    const val CLOZE_SUFFIX = "cloze-suffix"
    const val TAGS = "tags"
    const val PART_OF_SPEECH = "part-of-speech"
    const val CONJUGATION = "conjugation"
    const val DICTIONARY = "dictionary"
    const val DICTIONARY_ALIAS = "dictionary-alias"
    const val FREQUENCIES = "frequencies"
    const val FREQUENCY_LOWEST = "frequency-lowest"
    const val FREQUENCY_HARMONIC_RANK = "frequency-harmonic-rank"
    const val FREQUENCY_AVERAGE_RANK = "frequency-average-rank"
    const val PITCH_ACCENTS = "pitch-accents"
    const val PITCH_ACCENT_POSITIONS = "pitch-accent-positions"
    const val PITCH_ACCENT_CATEGORIES = "pitch-accent-categories"
    const val SCREENSHOT = "screenshot"
    const val SEARCH_QUERY = "search-query"
    const val URL = "url"
    const val BOOK = "book"
    const val CHAPTER = "chapter"
    const val MEDIA = "media"
    const val SINGLE_GLOSSARY = "single-glossary"
    const val MORAE = "morae"
    const val PITCH_ACCENT_GRAPHS = "pitch-accent-graphs"
    const val PITCH_ACCENT_GRAPHS_JJ = "pitch-accent-graphs-jj"
    const val PITCH_ACCENT_COMPOSITE = "pitch-accent-composite"
    const val SENTENCE_FURIGANA = "sentence-furigana"
    const val SENTENCE_FURIGANA_PLAIN = "sentence-furigana-plain"
    const val SENTENCE_TRANSLATION = "sentence-translation"
    const val SELECTION_TEXT = "selection-text"
    const val MISC_INFO = "misc-info"
    const val POPUP_SELECTION_TEXT = "popup-selection-text"
    const val SELECTED_GLOSSARY = "selected-glossary"
    const val DOCUMENT_TITLE = "document-title"
    const val WORD_AUDIO = "word-audio"
    const val SENTENCE_AUDIO = "sentence-audio"

    val ALL: List<String> = listOf(
        // Core/Common
        EXPRESSION, READING, GLOSSARY, SENTENCE, SCREENSHOT, WORD_AUDIO, AUDIO,
        SELECTED_GLOSSARY, SINGLE_GLOSSARY,

        // Furigana
        FURIGANA, FURIGANA_PLAIN,

        // Glossary variants
        GLOSSARY_BRIEF, GLOSSARY_PLAIN, GLOSSARY_PLAIN_NO_DICT, GLOSSARY_NO_DICT,
        GLOSSARY_FIRST, GLOSSARY_FIRST_NO_DICT, GLOSSARY_FIRST_BRIEF,

        // Sentence/Cloze
        SENTENCE_BOLD, CLOZE_PREFIX, CLOZE_BODY, CLOZE_BODY_KANA, CLOZE_SUFFIX,

        // Pitch Accent
        PITCH_ACCENTS, PITCH_ACCENT_POSITIONS, PITCH_ACCENT_CATEGORIES,
        PITCH_ACCENT_GRAPHS, PITCH_ACCENT_GRAPHS_JJ, PITCH_ACCENT_COMPOSITE, MORAE,

        // Frequencies
        FREQUENCIES, FREQUENCY_LOWEST, FREQUENCY_HARMONIC_RANK, FREQUENCY_AVERAGE_RANK,

        // Meta
        TAGS, CONJUGATION, DICTIONARY, DICTIONARY_ALIAS,

        // Source/Context
        BOOK, CHAPTER, MEDIA, DOCUMENT_TITLE,

        // Other
        SENTENCE_AUDIO, POPUP_SELECTION_TEXT,
    )

    val ALL_WITH_TODO: List<String> = ALL

    val TODO_MARKERS = setOf<String>()

    val AUTO_DETECT_ALIASES: Map<String, List<String>> = mapOf(
        EXPRESSION to listOf("expression", "phrase", "term", "word"),
        READING to listOf("reading", "expression-reading", "word-reading"),
        FURIGANA to listOf("furigana", "expression-furigana", "word-furigana"),
        GLOSSARY to listOf("glossary", "definition", "meaning"),
        WORD_AUDIO to listOf("audio", "sound", "word-audio", "term-audio"),
        DICTIONARY to listOf("dictionary", "dict"),
        PITCH_ACCENTS to listOf("pitch-accents", "pitch-accent", "pitchaccent", "accent", "pitch-pattern"),
        PITCH_ACCENT_POSITIONS to listOf("pitch-accent-positions", "pitch-positions", "positions", "pitchaccentpositions"),
        PITCH_ACCENT_CATEGORIES to listOf("pitch-accent-categories", "pitch-categories", "categories", "pitchaccentcategories"),
        PITCH_ACCENT_GRAPHS to listOf("pitch-accent-graphs", "pitch-graphs", "graphs", "pitchaccentgraphs"),
        SENTENCE to listOf("sentence", "example-sentence"),
        CLOZE_BODY to listOf("cloze-body", "cloze"),
        CLOZE_PREFIX to listOf("cloze-prefix"),
        CLOZE_SUFFIX to listOf("cloze-suffix"),
        FREQUENCIES to listOf("frequencies", "freq", "frequency-list"),
        FREQUENCY_HARMONIC_RANK to listOf("freq-rank", "frequency-rank", "freqSort"),
        FREQUENCY_AVERAGE_RANK to listOf("freq-avg", "frequency-average"),
        SENTENCE_TRANSLATION to listOf("sentence-translation", "sentenceTranslation", "meaning-eng"),
        SEARCH_QUERY to listOf("search-query", "query"),
        SCREENSHOT to listOf("screenshot"),
        TAGS to listOf("tags", "tag"),
        PART_OF_SPEECH to listOf("part-of-speech", "pos", "part"),
        CONJUGATION to listOf("conjugation", "inflection"),
        BOOK to listOf("book", "manga", "series", "title"),
        CHAPTER to listOf("chapter", "episode"),
        MEDIA to listOf("media", "source", "context"),
    )

    fun autoDetect(fieldName: String, fieldIndex: Int, entryType: String = "term"): String? {
        if (fieldIndex == 0) {
            return if (entryType == "kanji") "character" else EXPRESSION
        }
        val lower = fieldName.lowercase()
        for ((marker, aliases) in AUTO_DETECT_ALIASES) {
            if (aliases.any { alias -> lower == alias }) return marker
        }
        return null
    }
}

// =============================================================================
// AnkiResult
// =============================================================================

sealed class AnkiResult {
    data class Success(val noteId: Long) : AnkiResult()
    data class CardExists(val noteId: Long) : AnkiResult()
    data class OpenCard(val noteId: Long) : AnkiResult()
    data class Error(val message: String) : AnkiResult()
    data object PermissionDenied : AnkiResult()
    data object NotConfigured : AnkiResult()
}

// =============================================================================
// Field map parsing
// =============================================================================

object AnkiCardCreator {

    private const val TAG = "AnkiCardCreator"

    suspend fun addToAnki(
        context: Context,
        result: LookupResult,
        deck: String,
        model: String,
        fieldMapJson: String,
        tags: String,
        dupCheck: Boolean,
        dupScope: String,
        dupAction: String,
        sentence: String = "",
        offset: Int = -1,
        media: MediaInfo? = null,
        screenshotBytes: ByteArray? = null,
        glossaryIndex: Int? = null,
        selection: String? = null,
        selectedDict: String? = null,
        popupSelection: String? = null,
        styles: List<DictionaryStyle> = emptyList(),
        forceOpen: Boolean = false,
        type: String? = null,
        syncOnCreate: Boolean = false,
    ): AnkiResult {
        android.util.Log.d(TAG, "addToAnki: deck=$deck, model=$model, forceOpen=$forceOpen, glossaryIndex=$glossaryIndex")

        val bridge = AnkiDroidBridge(context)
        if (!bridge.hasPermission()) {
            return AnkiResult.PermissionDenied
        }
        return try {
            val effectiveDeck = deck.ifBlank { bridge.ensureDefaultDeckName() }
            val requestedModel = model.ifBlank { LapisPreset.MODEL_NAME }
            val effectiveModel = if (LapisPreset.isBundledModelName(requestedModel)) {
                bridge.ensureLapisModelName()
            } else {
                requestedModel
            }
            val effectiveFieldMapJson = if (
                LapisPreset.isBundledModelName(effectiveModel) &&
                LapisPreset.isBlankFieldMap(fieldMapJson)
            ) {
                LapisPreset.defaultFieldMapJson
            } else {
                fieldMapJson
            }

            if (effectiveDeck.isBlank() || effectiveModel.isBlank()) {
                android.util.Log.w(TAG, "addToAnki: NotConfigured - deck or model is blank")
                return AnkiResult.NotConfigured
            }

            val fieldMap = parseFieldMap(effectiveFieldMapJson)
            android.util.Log.d(TAG, "addToAnki: parsed fieldMap=$fieldMap")
            val cloze = if (sentence.isNotEmpty() && offset >= 0) {
                // Use result.matched (the exact surface form the dictionary engine consumed)
                // so the bold window is precisely the word that was looked up, not the base form.
                // If the caller provided a manual selection override, use that instead.
                val boldTarget = selection?.takeIf { it.isNotEmpty() } ?: result.matched
                buildCloze(sentence, offset, result.term.expression, result.term.reading, boldTarget)
            } else {
                null
            }

            var screenshotFilename: String? = null
            if (screenshotBytes != null) {
                try {
                    screenshotFilename = bridge.storeMedia(
                        filename = generateScreenshotFilename(screenshotBytes),
                        data = screenshotBytes,
                    )
                    android.util.Log.d(TAG, "addToAnki: stored screenshot media as $screenshotFilename")
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "addToAnki: failed to store screenshot media", e)
                }
            }

            var wordAudioFilename: String? = null
            val hasWordAudioMarker = fieldMap.values.any {
                it.contains("{${Marker.WORD_AUDIO}}") || it.contains("{${Marker.AUDIO}}")
            }
            if (hasWordAudioMarker) {
                try {
                    val wordAudioService = Injekt.get<WordAudioService>()
                    val audioResults = wordAudioService.findWordAudio(result.term.expression, result.term.reading)
                    if (audioResults.isNotEmpty()) {
                        // Use the first result (priority order)
                        val bestAudio = audioResults.first()
                        val audioData = if (bestAudio.url.startsWith("chimahon-local://")) {
                            val uri = android.net.Uri.parse(bestAudio.url)
                            val sourceId = uri.host ?: ""
                            val filePath = uri.path?.substring(1) ?: ""
                            wordAudioService.getAudioData(filePath, sourceId)
                        } else {
                            wordAudioService.fetchRemoteAudioData(bestAudio.url)
                        }

                        if (audioData != null) {
                            val ext = bestAudio.url.substringBefore('?').substringAfterLast('.', "mp3").lowercase()
                            val filename = "chimahon_audio_${result.term.expression}_${result.term.reading}_${System.currentTimeMillis()}.$ext"
                            wordAudioFilename = bridge.storeMedia(filename, audioData)
                            android.util.Log.d(TAG, "addToAnki: stored word audio media as $wordAudioFilename")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "addToAnki: failed to store word audio media", e)
                }
            }

            val fields = buildFields(
                result,
                fieldMap,
                cloze,
                media,
                screenshotFilename,
                wordAudioFilename,
                selectedDict,
                popupSelection,
                glossaryIndex,
                styles,
            )
            android.util.Log.d(TAG, "addToAnki: built fields=$fields")
            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

            if (dupCheck || forceOpen) {
                val targetDeckId = if (dupScope == "deck" && effectiveDeck.isNotBlank()) {
                    try {
                        bridge.getDeckId(effectiveDeck)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }

                val existing = bridge.findNotes(result.term.expression, null, targetDeckId)
                if (existing.isNotEmpty()) {
                    if (forceOpen) return AnkiResult.OpenCard(existing.first())
                    when (dupAction) {
                        "prevent" -> return AnkiResult.CardExists(existing.first())
                        "open" -> return AnkiResult.OpenCard(existing.first())
                        "overwrite" -> {
                            bridge.updateNoteFields(existing.first(), fields)
                            com.canopus.chimareader.data.AnkiStatsStorage.addCard(context, type)
                            if (syncOnCreate) bridge.triggerSync()
                            return AnkiResult.Success(existing.first())
                        }
                    }
                }
            }

            val noteId = bridge.addNote(deckName = effectiveDeck, modelName = effectiveModel, fields = fields, tags = tagList)
            com.canopus.chimareader.data.AnkiStatsStorage.addCard(context, type)
            if (syncOnCreate) bridge.triggerSync()
            AnkiResult.Success(noteId)
        } catch (e: SecurityException) {
            AnkiResult.PermissionDenied
        } catch (e: Exception) {
            AnkiResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun filterToSingleGlossary(result: LookupResult, glossaryIndex: Int): LookupResult {
        val glossary = result.term.glossaries.getOrNull(glossaryIndex) ?: return result
        val filteredGlossaries = arrayOf(glossary)
        return result.copy(
            term = result.term.copy(
                glossaries = filteredGlossaries,
            ),
        )
    }

    suspend fun checkExistingCards(
        context: Context,
        expressions: List<String>,
        deckName: String = "",
        dupScope: String = "collection",
    ): Set<String> {
        val existing = mutableSetOf<String>()

        val bridge = AnkiDroidBridge(context)
        if (!bridge.hasPermission()) return existing

        val targetDeckId = if (dupScope == "deck" && deckName.isNotBlank()) {
            try {
                bridge.getDeckId(deckName)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        for (expr in expressions.distinct()) {
            try {
                val notes = bridge.findNotes(expr, null, targetDeckId)
                if (notes.isNotEmpty()) {
                    existing.add(expr)
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "checkExistingCards failed for expr=$expr", e)
            }
        }
        return existing
    }

    fun parseFieldMap(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    val raw = obj.getString(key).trim()
                    if (raw.isNotEmpty()) put(key, normalizeFieldValue(raw))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun normalizeFieldValue(raw: String): String {
        // If it looks like a template already (contains markers or HTML), preserve it.
        if (raw.contains("{") || raw.contains("<") || raw.contains(">")) return raw

        val legacyMap = mapOf(
            "TARGET_WORD" to Marker.EXPRESSION,
            "READING" to Marker.READING,
            "FURIGANA" to Marker.FURIGANA,
            "DEFINITION" to Marker.GLOSSARY,
            "SENTENCE" to Marker.SENTENCE,
            "FREQUENCY" to Marker.FREQUENCY_HARMONIC_RANK,
            "PITCH_ACCENT" to Marker.PITCH_ACCENTS,
            "NONE" to "",
        )
        val parts = raw.split(",")
        if (parts.all { it.trim() in legacyMap }) {
            return parts.mapNotNull { part ->
                val marker = legacyMap[part.trim()]
                if (marker.isNullOrBlank()) null else "{$marker}"
            }.joinToString("<br>")
        }
        return raw
    }

    // =============================================================================
    // Field rendering
    // =============================================================================

    private val MARKER_PATTERN = Regex("""\{([\w\W]+?)\}""")

    fun buildFields(
        result: LookupResult,
        fieldMap: Map<String, String>,
        cloze: Cloze? = null,
        media: MediaInfo? = null,
        screenshotFilename: String? = null,
        wordAudioFilename: String? = null,
        selectedDict: String? = null,
        popupSelection: String? = null,
        glossaryIndex: Int? = null,
        styles: List<DictionaryStyle> = emptyList(),
    ): Map<String, String> = fieldMap.mapValues { (_, template) ->
        formatField(template, result, cloze, media, screenshotFilename, wordAudioFilename, selectedDict, popupSelection, glossaryIndex, styles)
    }

    private fun formatField(
        template: String,
        result: LookupResult,
        cloze: Cloze?,
        media: MediaInfo?,
        screenshotFilename: String?,
        wordAudioFilename: String?,
        selectedDict: String?,
        popupSelection: String?,
        glossaryIndex: Int?,
        styles: List<DictionaryStyle>,
    ): String {
        if (template.isBlank()) return ""
        return MARKER_PATTERN.replace(template) { match ->
            renderMarker(
                marker = match.groupValues[1],
                result = result,
                cloze = cloze,
                media = media,
                screenshotFilename = screenshotFilename,
                wordAudioFilename = wordAudioFilename,
                selectedDict = selectedDict,
                popupSelection = popupSelection,
                glossaryIndex = glossaryIndex,
                styles = styles,
            )
        }
    }

    // =============================================================================
    // Cloze
    // =============================================================================

    fun buildCloze(
        sentence: String,
        offset: Int,
        expression: String,
        reading: String,
        // matched: the exact surface form the dictionary engine consumed (e.g. "走った" not "走る").
        // Pass result.matched here for precise bolding. A user manual highlight takes priority at the call site.
        matched: String? = null,
    ): Cloze {
        if (sentence.isEmpty() || offset < 0 || expression.isEmpty()) {
            return Cloze(
                sentence = sentence,
                prefix = "",
                body = expression,
                bodyKana = reading,
                suffix = "",
            )
        }

        val safeOffset = offset.coerceIn(0, sentence.length)
        val prefix = sentence.substring(0, safeOffset)

        // Bold exactly as many characters as the dictionary matched (surface form length).
        // Fall back to expression length if matched is unavailable.
        val boldLen = (matched?.length ?: expression.length).coerceAtLeast(1)
        val bodyEnd = (safeOffset + boldLen).coerceAtMost(sentence.length)
        val body = sentence.substring(safeOffset, bodyEnd)

        val suffix = sentence.substring(bodyEnd)

        val bodyKana = calculateBodyKana(expression, reading)

        return Cloze(
            sentence = sentence,
            prefix = prefix,
            body = body,
            bodyKana = bodyKana,
            suffix = suffix,
        )
    }

    private fun calculateBodyKana(expression: String, reading: String): String {
        if (reading.isEmpty()) return expression
        if (expression == reading) return expression

        val segments = distributeFurigana(expression, reading)
        return segments.joinToString("") { (_, furigana) ->
            if (furigana.isNotEmpty()) furigana else ""
        }
    }

    // =============================================================================
    // Marker rendering
    // =============================================================================

    fun renderMarker(
        marker: String,
        result: LookupResult,
        cloze: Cloze? = null,
        media: MediaInfo? = null,
        screenshotFilename: String? = null,
        wordAudioFilename: String? = null,
        selectedDict: String? = null,
        popupSelection: String? = null,
        glossaryIndex: Int? = null,
        styles: List<DictionaryStyle> = emptyList(),
    ): String = when (marker) {
        Marker.EXPRESSION -> escapeHtml(result.term.expression)
        Marker.READING -> escapeHtml(result.term.reading)
        Marker.FURIGANA -> buildFuriganaHtml(result.term.expression, result.term.reading)
        Marker.FURIGANA_PLAIN -> buildFuriganaPlain(result.term.expression, result.term.reading)
        Marker.AUDIO -> wordAudioFilename?.let { "[sound:$it]" } ?: ""
        Marker.GLOSSARY -> buildGlossary(
            if (glossaryIndex != null && glossaryIndex >= 0) arrayOf(result.term.glossaries.getOrElse(glossaryIndex) { result.term.glossaries.first() }) else result.term.glossaries,
            brief = false,
            noDictTag = false,
            firstOnly = false,
            styles = styles,
        )
        Marker.GLOSSARY_BRIEF -> buildGlossary(
            if (glossaryIndex != null && glossaryIndex >= 0) arrayOf(result.term.glossaries.getOrElse(glossaryIndex) { result.term.glossaries.first() }) else result.term.glossaries,
            brief = true,
            noDictTag = false,
            firstOnly = false,
            styles = styles,
        )
        Marker.GLOSSARY_NO_DICT -> buildGlossary(
            if (glossaryIndex != null && glossaryIndex >= 0) arrayOf(result.term.glossaries.getOrElse(glossaryIndex) { result.term.glossaries.first() }) else result.term.glossaries,
            brief = false,
            noDictTag = true,
            firstOnly = false,
            styles = styles,
        )
        Marker.GLOSSARY_FIRST -> buildGlossary(
            if (glossaryIndex != null && glossaryIndex >= 0) arrayOf(result.term.glossaries.getOrElse(glossaryIndex) { result.term.glossaries.first() }) else result.term.glossaries,
            brief = false,
            noDictTag = false,
            firstOnly = true,
            styles = styles,
        )
        Marker.GLOSSARY_FIRST_NO_DICT -> buildGlossary(
            if (glossaryIndex != null && glossaryIndex >= 0) arrayOf(result.term.glossaries.getOrElse(glossaryIndex) { result.term.glossaries.first() }) else result.term.glossaries,
            brief = false,
            noDictTag = true,
            firstOnly = true,
            styles = styles,
        )
        Marker.GLOSSARY_FIRST_BRIEF -> buildGlossary(
            if (glossaryIndex != null && glossaryIndex >= 0) arrayOf(result.term.glossaries.getOrElse(glossaryIndex) { result.term.glossaries.first() }) else result.term.glossaries,
            brief = true,
            noDictTag = false,
            firstOnly = true,
            styles = styles,
        )
        Marker.GLOSSARY_PLAIN -> buildGlossaryPlain(
            if (glossaryIndex != null && glossaryIndex >= 0) arrayOf(result.term.glossaries.getOrElse(glossaryIndex) { result.term.glossaries.first() }) else result.term.glossaries,
            noDictTag = false,
        )
        Marker.GLOSSARY_PLAIN_NO_DICT -> buildGlossaryPlain(
            if (glossaryIndex != null && glossaryIndex >= 0) arrayOf(result.term.glossaries.getOrElse(glossaryIndex) { result.term.glossaries.first() }) else result.term.glossaries,
            noDictTag = true,
        )
        Marker.SENTENCE -> cloze?.let { escapeHtml(it.sentence) } ?: ""
        Marker.SENTENCE_BOLD -> cloze?.let { "${escapeHtml(it.prefix)}<b>${escapeHtml(it.body)}</b>${escapeHtml(it.suffix)}" } ?: ""
        Marker.CLOZE_PREFIX -> cloze?.let { escapeHtml(it.prefix) } ?: ""
        Marker.CLOZE_BODY -> cloze?.let { escapeHtml(it.body) } ?: ""
        Marker.CLOZE_BODY_KANA -> cloze?.let { escapeHtml(it.bodyKana) } ?: ""
        Marker.CLOZE_SUFFIX -> cloze?.let { escapeHtml(it.suffix) } ?: ""
        Marker.TAGS -> buildTags(result)
        Marker.PART_OF_SPEECH -> buildPartOfSpeech(result)
        Marker.CONJUGATION -> buildConjugation(result)
        Marker.DICTIONARY -> result.term.glossaries.firstOrNull()?.let { escapeHtml(it.dictName) } ?: ""
        Marker.DICTIONARY_ALIAS -> result.term.glossaries.firstOrNull()?.let { escapeHtml(it.dictName) } ?: ""
        Marker.FREQUENCIES -> buildFrequenciesList(result)
        Marker.FREQUENCY_LOWEST -> selectLowestFrequencyValue(result) ?: ""
        Marker.FREQUENCY_HARMONIC_RANK -> buildFrequencyHarmonicRank(result)
        Marker.FREQUENCY_AVERAGE_RANK -> buildFrequencyAverageRank(result)
        Marker.PITCH_ACCENTS -> buildPitchAccents(result.term.reading, result.term.pitches, format = PitchFormat.SVG)
        Marker.PITCH_ACCENT_POSITIONS -> buildPitchAccentPositions(result.term.pitches)
        Marker.PITCH_ACCENT_CATEGORIES -> buildPitchCategories(result.term.reading, result.term.rules, result.term.pitches)
        Marker.PITCH_ACCENT_COMPOSITE -> buildPitchAccents(result.term.reading, result.term.pitches, format = PitchFormat.COMPOSITE)
        Marker.WORD_AUDIO -> wordAudioFilename?.let { "[sound:$it]" } ?: ""
        Marker.SENTENCE_AUDIO -> ""
        Marker.MORAE -> buildMorae(result.term.reading)
        Marker.PITCH_ACCENT_GRAPHS, Marker.PITCH_ACCENT_GRAPHS_JJ -> buildPitchAccentGraphs(result.term.reading, result.term.pitches)
        Marker.SCREENSHOT -> screenshotFilename?.let { "<img src=\"$it\">" } ?: ""
        Marker.SEARCH_QUERY -> escapeHtml(result.term.expression)
        Marker.URL -> ""
        Marker.BOOK -> media?.mangaTitle?.let { escapeHtml(it) } ?: ""
        Marker.CHAPTER -> media?.chapterName?.let { escapeHtml(it) } ?: ""
        Marker.MEDIA -> {
            if (media != null && media.mangaTitle.isNotBlank()) {
                if (media.chapterName.isNotBlank()) {
                    "${escapeHtml(media.mangaTitle)}-${escapeHtml(media.chapterName)}"
                } else {
                    escapeHtml(media.mangaTitle)
                }
            } else {
                ""
            }
        }
        Marker.POPUP_SELECTION_TEXT -> popupSelection?.let { escapeHtmlWithLineBreaks(it) } ?: ""
        Marker.SELECTED_GLOSSARY -> {
            val selected = selectedDict?.takeIf { it.isNotBlank() }
            if (selected != null) {
                buildGlossary(
                    result.term.glossaries,
                    brief = false,
                    noDictTag = false,
                    firstOnly = false,
                    dictionaryFilter = selected,
                    styles = styles,
                )
            } else {
                renderMarker(Marker.GLOSSARY_FIRST, result, glossaryIndex = glossaryIndex, styles = styles)
            }
        }
        Marker.DOCUMENT_TITLE -> media?.mangaTitle?.let { escapeHtml(it) } ?: ""
        else -> parseSingleGlossaryMarker(marker, result, styles)
    }

    // =============================================================================
    // Single glossary marker parsing (Yomitan-style)
    // =============================================================================

    private fun parseSingleGlossaryMarker(
        marker: String,
        result: LookupResult,
        styles: List<DictionaryStyle>,
    ): String {
        val prefix = "single-glossary-"
        if (!marker.startsWith(prefix)) return ""

        val rest = marker.substring(prefix.length)
        if (rest.isBlank()) return ""

        val tokens = rest.split("-").toMutableList()
        var hasBrief = false
        var hasFirst = false
        var hasPlain = false
        var noDictTag = false
        while (tokens.isNotEmpty()) {
            val suffix = tokens.last().lowercase()
            when {
                suffix == "brief" -> {
                    hasBrief = true
                    tokens.removeAt(tokens.lastIndex)
                }
                suffix == "first" -> {
                    hasFirst = true
                    tokens.removeAt(tokens.lastIndex)
                }
                suffix == "plain" -> {
                    hasPlain = true
                    tokens.removeAt(tokens.lastIndex)
                }
                tokens.size >= 2 &&
                    tokens[tokens.lastIndex - 1].equals("no", ignoreCase = true) &&
                    suffix == "dictionary" -> {
                    noDictTag = true
                    tokens.removeAt(tokens.lastIndex)
                    tokens.removeAt(tokens.lastIndex)
                }
                else -> break
            }
        }

        val dictName = tokens.joinToString("-").trim()
        if (dictName.isEmpty()) return ""
        val dictionaryFilter = dictName.takeUnless { it.equals("all", ignoreCase = true) }

        if (hasPlain) {
            val filtered = if (dictionaryFilter == null) {
                result.term.glossaries
            } else {
                result.term.glossaries.filter { it.dictName.contains(dictionaryFilter, ignoreCase = true) }.toTypedArray()
            }
            val entries = if (hasFirst) filtered.take(1).toTypedArray() else filtered
            return buildGlossaryPlain(entries, noDictTag = noDictTag)
        }

        return buildGlossary(
            result.term.glossaries,
            brief = hasBrief,
            noDictTag = noDictTag,
            firstOnly = hasFirst,
            dictionaryFilter = dictionaryFilter,
            styles = styles,
        )
    }

    // =============================================================================
    // Furigana
    // =============================================================================

    fun buildFuriganaHtml(expression: String, reading: String): String {
        if (expression.isBlank()) return ""
        if (reading.isBlank() || expression == reading) return escapeHtml(expression)
        val segments = distributeFurigana(expression, reading)
        return segments.joinToString("") { (text, furigana) ->
            if (furigana.isNotEmpty()) {
                "<ruby>${escapeHtml(text)}<rt>${escapeHtml(furigana)}</rt></ruby>"
            } else {
                escapeHtml(text)
            }
        }
    }

    fun buildFuriganaPlain(expression: String, reading: String): String {
        if (expression.isBlank()) return ""
        if (reading.isBlank() || expression == reading) return expression
        val segments = distributeFurigana(expression, reading)
        return segments.joinToString("") { (text, furigana) ->
            if (furigana.isNotEmpty()) "$text[$furigana]" else "$text "
        }.trimEnd()
    }

    private fun distributeFurigana(expression: String, reading: String): List<Pair<String, String>> {
        val exprChars = expression.toList()
        val result = mutableListOf<Pair<String, String>>()
        var i = 0
        var readingPos = 0

        while (i < exprChars.size) {
            val ch = exprChars[i]
            when {
                isKana(ch) -> {
                    val kana = toHiragana(ch.toString())
                    if (readingPos < reading.length && reading.startsWith(kana, readingPos)) {
                        readingPos += kana.length
                    }
                    result.add(ch.toString() to "")
                    i++
                }
                isKanji(ch) -> {
                    val kanjiEnd = findNextKana(exprChars, i + 1)
                    val kanjiStr = exprChars.subList(i, kanjiEnd).joinToString("")
                    val readingEnd = if (kanjiEnd < exprChars.size) {
                        val nextKanaHiragana = toHiragana(exprChars[kanjiEnd].toString())
                        findReadingEndBefore(reading, readingPos, nextKanaHiragana)
                    } else {
                        reading.length
                    }
                    result.add(kanjiStr to reading.substring(readingPos, readingEnd))
                    readingPos = readingEnd
                    i = kanjiEnd
                }
                else -> {
                    result.add(ch.toString() to "")
                    i++
                }
            }
        }
        return result
    }

    private fun isKana(ch: Char) = ch.code in 0x3040..0x309F || ch.code in 0x30A0..0x30FF
    private fun isKanji(ch: Char) = ch.code in 0x4E00..0x9FAF || ch.code in 0x3400..0x4DBF

    private fun toHiragana(str: String) = str.map { ch ->
        if (ch.code in 0x30A1..0x30F6) (ch.code - 0x60).toChar() else ch
    }.joinToString("")

    private fun findNextKana(chars: List<Char>, start: Int): Int {
        var i = start
        while (i < chars.size && !isKana(chars[i])) i++
        return i
    }

    private fun findReadingEndBefore(reading: String, start: Int, nextKana: String): Int {
        if (nextKana.isEmpty()) return reading.length
        val idx = reading.indexOf(nextKana, start)
        return if (idx >= 0) idx else reading.length
    }

    // =============================================================================
    // Glossary
    // =============================================================================

    private fun buildGlossary(
        glossaries: Array<GlossaryEntry>,
        brief: Boolean,
        noDictTag: Boolean,
        firstOnly: Boolean,
        dictionaryFilter: String? = null,
        styles: List<DictionaryStyle> = emptyList(),
    ): String {
        if (glossaries.isEmpty()) return ""

        val filteredGlossaries = if (dictionaryFilter != null) {
            glossaries.filter { it.dictName.contains(dictionaryFilter, ignoreCase = true) }.toTypedArray()
        } else {
            glossaries
        }

        if (filteredGlossaries.isEmpty()) return ""
        val entries = if (firstOnly) arrayOf(filteredGlossaries[0]) else filteredGlossaries
        val scopedStyles = buildScopedDictionaryStyles(entries.map { it.dictName }.toSet(), styles)

        val sb = StringBuilder()
        sb.append("""<div style="text-align: left;" class="yomitan-glossary">""")
        sb.append("<ol>")
        var previousDictName = ""
        for (entry in entries) {
            val dictAttr = attrEscape(entry.dictName)
            sb.append("""<li data-dictionary="$dictAttr">""")
            sb.append(renderGlossarySingle(entry, brief, noDictTag, previousDictName))
            sb.append("</li>")
            previousDictName = entry.dictName
        }
        sb.append("</ol>")
        sb.append("</div>")
        if (scopedStyles.isNotEmpty()) {
            sb.append("<style>")
            sb.append(scopedStyles)
            sb.append("</style>")
        }
        return sb.toString()
    }

    private fun buildScopedDictionaryStyles(
        dictionaryNames: Set<String>,
        styles: List<DictionaryStyle>,
    ): String {
        if (dictionaryNames.isEmpty() || styles.isEmpty()) return ""
        return styles
            .asSequence()
            .filter { it.dictName in dictionaryNames && it.styles.isNotBlank() }
            .joinToString("\n") { style ->
                scopeDictionaryCss(
                    css = sanitizeDictionaryCss(style.styles),
                    scopeSelector = """[data-dictionary="${attrEscape(style.dictName)}"]""",
                )
            }
            .trim()
    }

    private fun sanitizeDictionaryCss(css: String): String = css
        .replace(Regex("(?is)<\\s*/?\\s*style[^>]*>"), "")
        .replace(Regex("(?is)@import\\s+[^;]+;"), "")
        .replace(Regex("(?is)url\\s*\\(\\s*javascript:[^)]+\\)"), "")
        .trim()

    private fun scopeDictionaryCss(css: String, scopeSelector: String): String {
        if (css.isBlank()) return ""
        if (!css.contains("{")) return "$scopeSelector { $css }"

        val out = StringBuilder()
        var i = 0
        while (i < css.length) {
            while (i < css.length && css[i].isWhitespace()) i++
            if (i >= css.length) break

            if (css.startsWith("/*", i)) {
                val end = css.indexOf("*/", i + 2)
                i = if (end >= 0) end + 2 else css.length
                continue
            }

            val preludeStart = i
            while (i < css.length && css[i] != '{' && css[i] != ';') i++
            if (i >= css.length) break

            if (css[i] == ';') {
                i++
                continue
            }

            val prelude = css.substring(preludeStart, i).trim()
            val bodyStart = i + 1
            val bodyEnd = findCssBlockEnd(css, i)
            if (bodyEnd <= bodyStart) break
            val body = css.substring(bodyStart, bodyEnd).trim()

            when {
                prelude.startsWith("@media", ignoreCase = true) ||
                    prelude.startsWith("@supports", ignoreCase = true) ||
                    prelude.startsWith("@layer", ignoreCase = true) -> {
                    val scopedBody = scopeDictionaryCss(body, scopeSelector)
                    if (scopedBody.isNotBlank()) {
                        out.append(prelude).append(" {\n").append(scopedBody).append("\n}\n")
                    }
                }
                prelude.startsWith("@font-face", ignoreCase = true) ||
                    prelude.startsWith("@keyframes", ignoreCase = true) ||
                    prelude.startsWith("@-", ignoreCase = true) -> {
                    out.append(prelude).append(" { ").append(body).append(" }\n")
                }
                prelude.startsWith("@", ignoreCase = true) -> {
                    out.append(prelude).append(" { ").append(body).append(" }\n")
                }
                else -> {
                    val selectors = prelude.split(",")
                        .map { prefixCssSelector(it.trim(), scopeSelector) }
                        .filter { it.isNotBlank() }
                    if (selectors.isNotEmpty()) {
                        out.append(selectors.joinToString(", "))
                            .append(" { ")
                            .append(body)
                            .append(" }\n")
                    }
                }
            }

            i = bodyEnd + 1
        }
        return out.toString().trim()
    }

    private fun findCssBlockEnd(css: String, openBraceIndex: Int): Int {
        var depth = 0
        var i = openBraceIndex
        while (i < css.length) {
            when (css[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return css.length
    }

    private fun prefixCssSelector(selector: String, scopeSelector: String): String {
        if (selector.isBlank()) return ""
        if (selector.equals(":root", ignoreCase = true) ||
            selector.equals("html", ignoreCase = true) ||
            selector.equals("body", ignoreCase = true)
        ) {
            return scopeSelector
        }
        val rootDesc = Regex("""(?i)^(:root|html|body)\s+(.+)$""").find(selector)
        if (rootDesc != null) {
            return "$scopeSelector ${rootDesc.groupValues[2]}"
        }
        return "$scopeSelector $selector"
    }

    private fun renderGlossarySingle(
        entry: GlossaryEntry,
        brief: Boolean,
        noDictTag: Boolean,
        previousDictName: String = "",
    ): String {
        val sb = StringBuilder()

        if (!brief) {
            val tagParts = mutableListOf<String>()
            if (entry.definitionTags.isNotBlank()) {
                tagParts += entry.definitionTags.split(" ").filter { it.isNotBlank() }
            }
            if (!noDictTag && entry.dictName.isNotBlank() && entry.dictName != previousDictName) {
                tagParts += entry.dictName
            }
            if (tagParts.isNotEmpty()) {
                sb.append("<i>(${tagParts.joinToString(", ")})</i> ")
            }
        }

        val content = glossaryToHtml(entry.glossary, entry.dictName)
        if (content.isNotEmpty()) sb.append(content)

        return sb.toString()
    }

    private fun buildGlossaryPlain(glossaries: Array<GlossaryEntry>, noDictTag: Boolean): String {
        if (glossaries.isEmpty()) return ""
        return glossaries.joinToString("<br>") { entry ->
            buildString {
                if (!noDictTag && entry.dictName.isNotBlank()) {
                    append("(${escapeHtml(entry.dictName)})<br>")
                }
                append(glossaryToPlainText(entry.glossary))
            }
        }
    }

    // =============================================================================
    // Structured-content → HTML
    // =============================================================================

    private fun normalizePlainText(text: String): String = text
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace("\\n", "\n")

    private fun escapeHtmlWithLineBreaks(text: String): String = escapeHtml(normalizePlainText(text))
        .replace("\n", "<br>")

    private fun glossaryToHtml(raw: String, dictionary: String = ""): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return escapeHtmlWithLineBreaks(trimmed)
        }
        return try {
            when {
                trimmed.startsWith("[") -> arrayToHtml(JSONArray(trimmed), dictionary, "div")
                else -> objectToHtml(JSONObject(trimmed), dictionary)
            }
        } catch (_: Exception) {
            escapeHtmlWithLineBreaks(trimmed)
        }
    }

    private fun glossaryToPlainText(raw: String): String {
        val html = glossaryToHtml(raw)
        val text = html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|li|div|tr|h[1-6])>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
        .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&quot;", "\"")
        return text
            .split('\n')
            .joinToString("\n") { line -> line.replace(Regex("[ \\t\\u000B\\u000C]+"), " ").trim() }
            .trim()
    }

    private fun arrayToHtml(arr: JSONArray, dictionary: String, parentTag: String): String {
        val allStrings = (0 until arr.length()).all { arr.get(it) is String }
        if (allStrings) {
            if (arr.length() == 1) return contentValueToHtml(arr.getString(0), dictionary, parentTag)
            
            // Only auto-wrap in UL if we are NOT already in a list-like tag
            val isListParent = parentTag == "ul" || parentTag == "ol" || parentTag == "li" || parentTag == "span" || parentTag == "td" || parentTag == "th"
            if (!isListParent) {
                return """<ul style="margin: 0.2em 0; padding-left: 1.2em;">""" + (0 until arr.length()).joinToString("") {
                    "<li>${contentValueToHtml(arr.getString(it), dictionary, "li")}</li>"
                } + "</ul>"
            }
        }

        return (0 until arr.length()).joinToString("") { i ->
            contentValueToHtml(arr.get(i), dictionary, parentTag)
        }
    }

    private fun objectToHtml(node: JSONObject, dictionary: String): String {
        val type = node.optString("type", "")
        if (type == "ruby") {
            val expression = node.optString("headword", "")
            val reading = node.optString("reading", "")
            return if (reading.isNotEmpty() && reading != expression) {
                "<ruby>$expression<rt>$reading</rt></ruby>"
            } else {
                expression
            }
        }

        if (type == "structured-content") {
            val content = node.opt("content") ?: return ""
            return contentValueToHtml(content, dictionary, "div")
        }

        if (type == "image") {
            return imageNodeToHtml(node)
        }

        val tag = node.optString("tag", "").trim().lowercase()

        if (tag.isEmpty()) {
            val content = node.opt("content") ?: return ""
            return contentValueToHtml(content, dictionary, "div")
        }

        val dataObj = node.optJSONObject("data")
        if (dataObj != null && dataObj.optString("content") == "attribution") return ""

        if (tag.lowercase() == "img") return imageNodeToHtml(node)

        if (tag == "br") return "<br>"
        if (tag == "hr") return "<hr>"

        val sb = StringBuilder("<$tag")

        node.optString("href", "").takeIf { it.isNotEmpty() }?.let { sb.append(""" href="${attrEscape(it)}"""") }
        node.optString("title", "").takeIf { it.isNotEmpty() }?.let { sb.append(""" title="${attrEscape(it)}"""") }
        node.optString("lang", "").takeIf { it.isNotEmpty() }?.let { sb.append(""" lang="${attrEscape(it)}"""") }
        node.optString("id", "").takeIf { it.isNotEmpty() }?.let { sb.append(""" id="${attrEscape(it)}"""") }

        if (dataObj != null) {
            for (key in dataObj.keys()) {
                val value = dataObj.get(key).toString()
                if (key == "class") {
                    sb.append(""" class="${attrEscape(value)}"""")
                    sb.append(""" data-sc-class="${attrEscape(value)}"""")
                } else {
                    sb.append(""" data-sc-$key="${attrEscape(value)}"""")
                }
            }
        }

        val styleObj = node.optJSONObject("style")
        if (styleObj != null && styleObj.length() > 0) {
            val cssStr = styleObj.keys().asSequence().joinToString("; ") { prop ->
                "${camelToKebab(prop)}: ${attrEscape(styleObj.getString(prop))}"
            }
            sb.append(""" style="$cssStr"""")
        } else {
            // Apply compact default styles for common block tags in Anki.
            // Avoid inline defaults for table elements — dictionary CSS
            // (included as scoped <style>) would be overridden by inline styles.
            when (tag) {
                "ul", "ol" -> sb.append(""" style="margin: 0.2em 0; padding-left: 1.2em;"""")
                "p", "div" -> sb.append(""" style="margin: 0.1em 0;"""")
            }
        }

        sb.append(">")

        val content = node.opt("content")
        if (content != null) {
            if (tag == "table") {
                sb.append(tableContentToHtml(content, dictionary))
            } else {
                sb.append(contentValueToHtml(content, dictionary, tag))
            }
        }

        sb.append("</$tag>")

        return if (tag == "table") {
            """<div class="gloss-sc-table-container">$sb</div>"""
        } else {
            sb.toString()
        }
    }

    private fun contentValueToHtml(value: Any?, dictionary: String, parentTag: String): String = when (value) {
        null -> ""
        is String -> escapeHtmlWithLineBreaks(value)
        is Number -> value.toString()
        is Boolean -> value.toString()
        is JSONArray -> arrayToHtml(value, dictionary, parentTag)
        is JSONObject -> objectToHtml(value, dictionary)
        else -> value.toString()
    }

    private fun imageNodeToHtml(node: JSONObject): String {
        val path = node.optString("path", "").ifEmpty { node.optString("src", "") }
        if (path.isEmpty()) return ""

        val sb = StringBuilder("<img")
        sb.append(""" src="${attrEscape(path)}"""")
        
        node.optString("alt", "").takeIf { it.isNotEmpty() }?.let { sb.append(""" alt="${attrEscape(it)}"""") }
        node.optString("title", "").takeIf { it.isNotEmpty() }?.let { sb.append(""" title="${attrEscape(it)}"""") }

        val width = node.optInt("width", 0)
        val height = node.optInt("height", 0)
        val sizeUnits = node.optString("sizeUnits", "px")
        val appearance = node.optString("appearance", "")
        
        val styleParts = mutableListOf<String>()
        if (width > 0 || height > 0) {
            val cssWidth = if (width > 0) "${width}$sizeUnits" else "auto"
            val cssHeight = if (height > 0) "${height}$sizeUnits" else "auto"
            styleParts.add("width: $cssWidth")
            styleParts.add("height: $cssHeight")
        }
        
        styleParts.add("vertical-align: middle")
        
        if (appearance == "monochrome") {
            sb.append(""" class="gloss-image-monochrome"""")
            // Yomitan style masking: allows the image to inherit text color
            styleParts.add("background-color: currentColor")
            styleParts.add("-webkit-mask-image: url('${attrEscape(path)}')")
            styleParts.add("-webkit-mask-repeat: no-repeat")
            styleParts.add("-webkit-mask-size: contain")
            styleParts.add("mask-image: url('${attrEscape(path)}')")
            styleParts.add("mask-repeat: no-repeat")
            styleParts.add("mask-size: contain")
        }
        
        if (styleParts.isNotEmpty()) {
            sb.append(""" style="${styleParts.joinToString("; ")}"""")
        }

        sb.append(" />")
        return sb.toString()
    }

    private fun tableContentToHtml(content: Any?, dictionary: String): String {
        val items: List<Any> = when (content) {
            is JSONArray -> (0 until content.length()).map { content.get(it) }
            else -> listOf(content ?: return "")
        }
        val hasSection = items.any {
            it is JSONObject && it.optString("tag") in listOf("tbody", "thead", "tfoot")
        }
        val hasBareRows = items.any { it is JSONObject && it.optString("tag") == "tr" }
        return if (hasBareRows && !hasSection) {
            val tbody = JSONObject().apply {
                put("tag", "tbody")
                put("content", JSONArray(items))
            }
            objectToHtml(tbody, dictionary)
        } else {
            contentValueToHtml(content, dictionary, "table")
        }
    }

    // =============================================================================
    // Tags, Part of Speech, Conjugation
    // =============================================================================

    private fun buildTags(result: LookupResult): String {
        val seen = linkedSetOf<String>()
        for (g in result.term.glossaries) {
            g.definitionTags.split(" ").filter { it.isNotBlank() }.forEach { seen.add(it) }
            g.termTags.split(" ").filter { it.isNotBlank() }.forEach { seen.add(it) }
        }
        return seen.joinToString(", ") { escapeHtml(it) }
    }

    private val POS_PRETTY = mapOf(
        "v1" to "Ichidan verb",
        "v5" to "Godan verb",
        "vk" to "Kuru verb",
        "vs" to "Suru verb",
        "vz" to "Zuru verb",
        "adj-i" to "I-adjective",
        "adj-na" to "Na-adjective",
        "n" to "Noun",
        "adv" to "Adverb",
        "prt" to "Particle",
        "exp" to "Expression",
        "conj" to "Conjunction",
        "pref" to "Prefix",
        "suf" to "Suffix",
        "aux-v" to "Auxiliary verb",
    )

    private fun buildPartOfSpeech(result: LookupResult): String {
        val seen = linkedSetOf<String>()
        result.term.rules
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { rule -> seen.add(POS_PRETTY[rule.lowercase()] ?: rule) }
        return if (seen.isEmpty()) "Unknown" else seen.joinToString(", ") { escapeHtml(it) }
    }

    private fun buildConjugation(result: LookupResult): String {
        val rules = result.term.rules
        if (rules.isNullOrEmpty()) return ""
        return rules.split(" « ").joinToString(" « ") { escapeHtml(it) }
    }

    // =============================================================================
    // Frequencies
    // =============================================================================

    private fun buildFrequenciesList(result: LookupResult): String {
        if (result.term.frequencies.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("<ul data-content=\"frequencies\">")
        for (group in result.term.frequencies) {
            val dictAttr = attrEscape(group.dictName)
            for (freq in group.frequencies) {
                if (freq.value <= 0) continue
                val display = freq.displayValue.takeIf { it.isNotBlank() } ?: freq.value.toString()
                sb.append("<li data-dictionary=\"$dictAttr\">${escapeHtml(display)}</li>")
            }
        }
        sb.append("</ul>")
        return sb.toString()
    }

    private fun selectLowestFrequencyValue(result: LookupResult): String? {
        var bestValue: Int? = null
        var bestDisplayValue: String? = null

        for (group in result.term.frequencies) {
            for (frequency in group.frequencies) {
                if (frequency.value <= 0) continue
                if (bestValue == null || frequency.value < bestValue) {
                    bestValue = frequency.value
                    bestDisplayValue = frequency.displayValue.takeIf { it.isNotBlank() }
                }
            }
        }

        return bestDisplayValue ?: bestValue?.toString()
    }

    private fun buildFrequencyHarmonicRank(result: LookupResult): String {
        val numbers = collectFrequencyNumbers(result)
        if (numbers.isEmpty()) return "9999999"
        val total = numbers.sumOf { 1.0 / it }
        return Math.floor(numbers.size / total).toInt().toString()
    }

    private fun buildFrequencyAverageRank(result: LookupResult): String {
        val numbers = collectFrequencyNumbers(result)
        if (numbers.isEmpty()) return "9999999"
        return (numbers.sum() / numbers.size).toString()
    }

    private fun collectFrequencyNumbers(result: LookupResult): List<Double> {
        val seen = linkedSetOf<String>()
        val out = mutableListOf<Double>()
        for (group in result.term.frequencies) {
            if (!seen.add(group.dictName)) continue
            for (f in group.frequencies) {
                val v = f.value
                if (v > 0) {
                    out.add(v.toDouble())
                    break
                }
            }
        }
        return out
    }

    // =============================================================================
    // Pitch accent
    // =============================================================================

    private enum class PitchFormat { TEXT, POSITION, SVG, OVERLINE, COMPOSITE }

    private fun buildPitchAccentPositions(pitches: Array<PitchEntry>): String {
        val positions = pitches.flatMap { group -> group.pitchPositions.toList() }
        if (positions.isEmpty()) return ""
        return if (positions.size == 1) {
            renderPitchPosition(positions.first())
        } else {
            positions.joinToString(prefix = "<ol>", postfix = "</ol>", separator = "") { position ->
                "<li>${renderPitchPosition(position)}</li>"
            }
        }
    }

    private fun renderPitchPosition(position: Int): String {
        val escapedPosition = escapeHtml(position.toString())
        return """<span style="display:inline;"><span>[</span><span>$escapedPosition</span><span>]</span></span>"""
    }

    private fun buildPitchAccents(reading: String, pitches: Array<PitchEntry>, format: PitchFormat): String {
        if (pitches.isEmpty()) return ""

        val allPitches = pitches.flatMap { group ->
            group.pitchPositions.map { pos -> Triple(group.dictName, pos, reading) }
        }
        if (allPitches.isEmpty()) return ""

        return if (allPitches.size == 1) {
            val (dict, pos, read) = allPitches[0]
            renderPitchItem(dict, pos, read, format)
        } else {
            val sb = StringBuilder()
            sb.append("<ol>")
            for ((dict, pos, read) in allPitches) {
                sb.append("<li>")
                sb.append(renderPitchItem(dict, pos, read, format))
                sb.append("</li>")
            }
            sb.append("</ol>")
            sb.toString()
        }
    }

    private fun renderPitchItem(dictName: String, position: Int, reading: String, format: PitchFormat): String {
        val prefix = if (dictName.isNotBlank()) "${escapeHtml(dictName)}: " else ""
        return when (format) {
            PitchFormat.POSITION -> "$prefix$position"
            PitchFormat.TEXT -> {
                val label = when (position) {
                    0 -> "平板 (heiban)"
                    1 -> "頭高 (atamadaka)"
                    else -> "中高/尾高 ($position)"
                }
                "$prefix$label"
            }
            PitchFormat.SVG -> {
                val morae = getMorae(reading)
                createPitchSvg(morae, position)
            }
            PitchFormat.OVERLINE -> {
                val morae = getMorae(reading)
                renderOverlineText(morae, position)
            }
            PitchFormat.COMPOSITE -> {
                val morae = getMorae(reading)
                val sb = StringBuilder()
                sb.append("<div class=\"pitch-accent-composite\" style=\"display: flex; align-items: center; gap: 0.5em; flex-wrap: wrap;\">")

                // 1. SVG (Graph)
                sb.append(createPitchSvg(morae, position))

                // 2. Overline Text
                sb.append(renderOverlineText(morae, position))

                // 3. Number [0]
                sb.append("<span class=\"pitch-number\" style=\"font-weight: bold;\">[$position]</span>")

                sb.append("</div>")
                "$prefix$sb"
            }
        }
    }

    private fun renderOverlineText(morae: List<String>, p: Int): String {
        val sb = StringBuilder()
        sb.append("<span class=\"pronunciation-text\">")
        for (i in morae.indices) {
            val mora = morae[i]
            val isHigh = when {
                p == 0 -> i > 0
                p == 1 -> i == 0
                else -> i > 0 && i < p
            }

            val style = if (isHigh) "border-top: 1px solid currentColor;" else ""
            sb.append("<span class=\"pronunciation-mora\" style=\"$style\">")
            sb.append(escapeHtml(mora))
            sb.append("</span>")

            // Add vertical line if it drops after this mora
            val dropsNext = (p == 1 && i == 0) || (p > 1 && i == p - 1)
            if (dropsNext) {
                sb.append("<span style=\"display: inline-block; width: 0; height: 1em; border-right: 1px solid currentColor; margin-left: -1px; vertical-align: bottom;\"></span>")
            }
        }
        sb.append("</span>")
        return sb.toString()
    }

    private fun buildPitchCategories(reading: String, rules: String, pitches: Array<PitchEntry>): String {
        val categories = linkedSetOf<String>()
        val moraCount = getMorae(reading).size
        if (moraCount == 0) return ""
        val verbOrAdjective = isVerbOrAdjective(rules)

        for (group in pitches) {
            for (pos in group.pitchPositions) {
                val cat = pitchPositionToCategory(pos, moraCount, verbOrAdjective)
                if (cat.isNotEmpty()) categories.add(cat)
            }
        }
        return categories.joinToString(",")
    }

    private fun pitchPositionToCategory(position: Int, moraCount: Int, verbOrAdjective: Boolean): String = when {
        position == 0 -> "heiban"
        verbOrAdjective && position > 0 -> "kifuku"
        position == 1 -> "atamadaka"
        position == moraCount -> "odaka"
        position > 1 -> "nakadaka"
        else -> ""
    }

    private fun isVerbOrAdjective(rules: String): Boolean {
        return rules.split(Regex("""\s+"""))
            .map { it.trim().lowercase() }
            .any { rule ->
                rule.startsWith("v") ||
                    rule.startsWith("adj") ||
                    rule == "aux-v"
            }
    }

    // =============================================================================
    // HTML utilities
    // =============================================================================

    internal fun escapeHtml(text: String): String = text
        .replace("\u001F", "")
        .replace("\u0000", "")
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun attrEscape(text: String): String = text
        .replace("\u001F", "")
        .replace("\u0000", "")
        .replace("&", "&amp;")
        .replace("\"", "&quot;")

    private fun camelToKebab(name: String): String =
        name.replace(Regex("([A-Z])")) { "-${it.value.lowercase()}" }

    private fun generateScreenshotFilename(bytes: ByteArray): String {
        val hash = try {
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val digest = md.digest(bytes)
            digest.joinToString("") { "%02x".format(it) }.take(12)
        } catch (e: Exception) {
            "screenshot_${System.currentTimeMillis()}"
        }
        return "chimahon_$hash.webp"
    }

    // =============================================================================
    // Pitch Accent / Morae
    // =============================================================================

    private fun buildMorae(reading: String): String {
        return getMorae(reading).joinToString(" ")
    }

    private fun getMorae(text: String): List<String> {
        val morae = mutableListOf<String>()
        val smallKana = setOf(
            'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'っ', 'ゃ', 'ゅ', 'ょ', 'ゎ',
            'ァ', 'ィ', 'ゥ', 'ェ', 'ォ', 'ッ', 'ャ', 'ュ', 'ョ', 'ヮ', 'ヵ', 'ヶ',
        )
        for (char in text) {
            if (morae.isNotEmpty() && char in smallKana) {
                morae[morae.size - 1] = morae.last() + char
            } else {
                morae.add(char.toString())
            }
        }
        return morae
    }

    private fun buildPitchAccentGraphs(reading: String, pitches: Array<PitchEntry>): String {
        if (pitches.isEmpty() || reading.isEmpty()) return ""
        val morae = getMorae(reading)
        if (morae.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("""<div class="pitch-accent-graphs">""")
        for (group in pitches) {
            for (pos in group.pitchPositions) {
                sb.append(createPitchSvg(morae, pos))
            }
        }
        sb.append("</div>")
        return sb.toString()
    }

    private fun createPitchSvg(morae: List<String>, p: Int): String {
        val n = morae.size
        // Values based on ref/pronunciation-generator.js JJ mode
        val stepWidth = 35
        val marginLr = 16
        val height = 45 // Reduced height since text is removed
        val svgWidth = Math.max(0, ((n) * stepWidth) + (marginLr * 2))

        val points = mutableListOf<Point>()
        for (i in 0..n) {
            val isHigh = when {
                p == 1 -> i == 0
                p == 0 -> i > 0
                p >= 2 -> {
                    if (i == 0) {
                        false
                    } else if (i < p) {
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
            points.add(Point(marginLr + (i * stepWidth), if (isHigh) 10 else 35))
        }

        val svg = StringBuilder()
        val displayWidth = (svgWidth * 0.8).toInt() // Increased from 0.6 to 0.8
        svg.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${displayWidth}px" height="40px" viewBox="0 0 $svgWidth $height" style="display: inline-block; vertical-align: middle;">""")

        val strokeColor = "currentColor"

        // 2. Draw Paths (Step 1 was text, now removed)
        if (points.size > 1) {
            var d = "M ${points[0].x} ${points[0].y}"
            for (i in 1 until points.size) {
                // JJ style uses simple lines, but let's stick to the path model
                d += " L ${points[i].x} ${points[i].y}"
            }
            svg.append("""<path d="$d" fill="none" stroke="$strokeColor" stroke-width="2" />""")
        }

        // 3. Draw Dots/Circles
        for (i in points.indices) {
            val pt = points[i]
            val isTail = i >= n
            if (isTail) {
                // Open circle for the tail (phonetic continuation)
                svg.append("""<circle cx="${pt.x}" cy="${pt.y}" r="4" fill="none" stroke="$strokeColor" stroke-width="2" />""")
            } else {
                svg.append("""<circle cx="${pt.x}" cy="${pt.y}" r="5" fill="$strokeColor" />""")
            }
        }

        svg.append("</svg>")
        return svg.toString()
    }

    private data class Point(val x: Int, val y: Int)
}
