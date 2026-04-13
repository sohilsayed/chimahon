package chimahon.anki

import android.content.Context
import chimahon.Cloze
import chimahon.GlossaryEntry
import chimahon.LookupResult
import chimahon.MediaInfo
import chimahon.PitchEntry
import org.json.JSONArray
import org.json.JSONObject

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
    const val GLOSSARY = "glossary"
    const val GLOSSARY_BRIEF = "glossary-brief"
    const val GLOSSARY_PLAIN = "glossary-plain"
    const val GLOSSARY_NO_DICT = "glossary-no-dictionary"
    const val GLOSSARY_FIRST = "glossary-first"
    const val GLOSSARY_FIRST_BRIEF = "glossary-first-brief"
    const val SENTENCE = "sentence"
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
    const val FREQUENCY_HARMONIC_RANK = "frequency-harmonic-rank"
    const val FREQUENCY_AVERAGE_RANK = "frequency-average-rank"
    const val PITCH_ACCENTS = "pitch-accents"
    const val PITCH_ACCENT_POSITIONS = "pitch-accent-positions"
    const val PITCH_ACCENT_CATEGORIES = "pitch-accent-categories"
    const val AUDIO = "audio"
    const val SCREENSHOT = "screenshot"
    const val SEARCH_QUERY = "search-query"
    const val MANGA = "manga"
    const val CHAPTER = "chapter"
    const val MEDIA = "media"
    const val SINGLE_GLOSSARY = "single-glossary"
    const val MORAE = "morae"
    const val PITCH_ACCENT_GRAPHS = "pitch-accent-graphs"

    val ALL: List<String> = listOf(
        EXPRESSION, READING, FURIGANA, FURIGANA_PLAIN,
        GLOSSARY, GLOSSARY_BRIEF, GLOSSARY_PLAIN, GLOSSARY_NO_DICT,
        GLOSSARY_FIRST, GLOSSARY_FIRST_BRIEF,
        SENTENCE, CLOZE_PREFIX, CLOZE_BODY, CLOZE_BODY_KANA, CLOZE_SUFFIX,
        TAGS, PART_OF_SPEECH, CONJUGATION,
        DICTIONARY, DICTIONARY_ALIAS,
        FREQUENCIES, FREQUENCY_HARMONIC_RANK, FREQUENCY_AVERAGE_RANK,
        PITCH_ACCENTS, PITCH_ACCENT_POSITIONS, PITCH_ACCENT_CATEGORIES,
        PITCH_ACCENT_GRAPHS, MORAE,
        MANGA, CHAPTER, MEDIA,
        SINGLE_GLOSSARY,
        SCREENSHOT, SEARCH_QUERY,
    )

    val ALL_WITH_TODO: List<String> = ALL + listOf(AUDIO)

    val TODO_MARKERS = setOf(AUDIO)

    val AUTO_DETECT_ALIASES: Map<String, List<String>> = mapOf(
        EXPRESSION to listOf("expression", "phrase", "term", "word"),
        READING to listOf("reading", "expression-reading", "word-reading"),
        FURIGANA to listOf("furigana", "expression-furigana", "word-furigana"),
        GLOSSARY to listOf("glossary", "definition", "meaning"),
        AUDIO to listOf("audio", "sound", "word-audio", "term-audio"),
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
        FREQUENCY_HARMONIC_RANK to listOf("freq-rank", "frequency-rank"),
        FREQUENCY_AVERAGE_RANK to listOf("freq-avg", "frequency-average"),
        SEARCH_QUERY to listOf("search-query", "query"),
        SCREENSHOT to listOf("screenshot"),
        TAGS to listOf("tags", "tag"),
        PART_OF_SPEECH to listOf("part-of-speech", "pos", "part"),
        CONJUGATION to listOf("conjugation", "inflection"),
        MANGA to listOf("manga", "series", "title"),
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
    ): AnkiResult {
        android.util.Log.d(TAG, "addToAnki: deck=$deck, model=$model, fieldMapJson=$fieldMapJson, glossaryIndex=$glossaryIndex")

        if (deck.isBlank() || model.isBlank()) {
            android.util.Log.w(TAG, "addToAnki: NotConfigured - deck or model is blank")
            return AnkiResult.NotConfigured
        }

        val filteredResult = if (glossaryIndex != null && glossaryIndex >= 0) {
            filterToSingleGlossary(result, glossaryIndex)
        } else {
            result
        }

        val bridge = AnkiDroidBridge(context)
        if (!bridge.hasPermission()) {
            return AnkiResult.PermissionDenied
        }
        return try {
            val fieldMap = parseFieldMap(fieldMapJson)
            android.util.Log.d(TAG, "addToAnki: parsed fieldMap=$fieldMap")
            val cloze = if (sentence.isNotEmpty() && offset >= 0) {
                buildCloze(sentence, offset, filteredResult.term.expression, filteredResult.term.reading)
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

            val fields = buildFields(filteredResult, fieldMap, cloze, media, screenshotFilename)
            android.util.Log.d(TAG, "addToAnki: built fields=$fields")
            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

            if (dupCheck) {
                val existing = bridge.findNotes(filteredResult.term.expression, model)
                if (existing.isNotEmpty()) {
                    when (dupAction) {
                        "prevent" -> return AnkiResult.CardExists(existing.first())
                        "open" -> return AnkiResult.OpenCard(existing.first())
                        "overwrite" -> {
                            bridge.updateNoteFields(existing.first(), fields)
                            return AnkiResult.Success(existing.first())
                        }
                    }
                }
            }

            val noteId = bridge.addNote(deckName = deck, modelName = model, fields = fields, tags = tagList)
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

    suspend fun checkExistingCards(context: Context, expressions: List<String>, modelName: String): Set<String> {
        val existing = mutableSetOf<String>()
        if (modelName.isBlank()) return existing

        val bridge = AnkiDroidBridge(context)
        if (!bridge.hasPermission()) return existing

        for (expr in expressions.distinct()) {
            try {
                val notes = bridge.findNotes(expr, modelName)
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
        if (raw.contains('{')) return raw

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
        val parts = raw.split(",").mapNotNull { part ->
            val marker = legacyMap[part.trim()]
            if (marker.isNullOrBlank()) null else "{$marker}"
        }
        return parts.joinToString("<br>")
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
    ): Map<String, String> = fieldMap.mapValues { (_, template) ->
        formatField(template, result, cloze, media, screenshotFilename)
    }

    private fun formatField(template: String, result: LookupResult, cloze: Cloze?, media: MediaInfo?, screenshotFilename: String?): String {
        if (template.isBlank()) return ""
        val spacedTemplate = template.replace("}{", "} {")
        return MARKER_PATTERN.replace(spacedTemplate) { match ->
            renderMarker(match.groupValues[1], result, cloze, media, screenshotFilename)
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

        val bodyEnd = (safeOffset + expression.length).coerceAtMost(sentence.length)
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

    fun renderMarker(marker: String, result: LookupResult, cloze: Cloze? = null, media: MediaInfo? = null, screenshotFilename: String? = null): String = when (marker) {
        Marker.EXPRESSION -> result.term.expression
        Marker.READING -> result.term.reading
        Marker.FURIGANA -> buildFuriganaHtml(result.term.expression, result.term.reading)
        Marker.FURIGANA_PLAIN -> buildFuriganaPlain(result.term.expression, result.term.reading)
        Marker.GLOSSARY -> buildGlossary(result.term.glossaries, brief = false, noDictTag = false, firstOnly = false)
        Marker.GLOSSARY_BRIEF -> buildGlossary(
            result.term.glossaries,
            brief = true,
            noDictTag = false,
            firstOnly = false,
        )
        Marker.GLOSSARY_NO_DICT -> buildGlossary(
            result.term.glossaries,
            brief = false,
            noDictTag = true,
            firstOnly = false,
        )
        Marker.GLOSSARY_FIRST -> buildGlossary(
            result.term.glossaries,
            brief = false,
            noDictTag = false,
            firstOnly = true,
        )
        Marker.GLOSSARY_FIRST_BRIEF -> buildGlossary(
            result.term.glossaries,
            brief = true,
            noDictTag = false,
            firstOnly = true,
        )
        Marker.GLOSSARY_PLAIN -> buildGlossaryPlain(result.term.glossaries, noDictTag = false)
        Marker.SENTENCE -> cloze?.sentence ?: ""
        Marker.CLOZE_PREFIX -> cloze?.prefix ?: ""
        Marker.CLOZE_BODY -> cloze?.body ?: ""
        Marker.CLOZE_BODY_KANA -> cloze?.bodyKana ?: ""
        Marker.CLOZE_SUFFIX -> cloze?.suffix ?: ""
        Marker.TAGS -> buildTags(result)
        Marker.PART_OF_SPEECH -> buildPartOfSpeech(result)
        Marker.CONJUGATION -> buildConjugation(result)
        Marker.DICTIONARY -> result.term.glossaries.firstOrNull()?.dictName ?: ""
        Marker.DICTIONARY_ALIAS -> result.term.glossaries.firstOrNull()?.dictName ?: ""
        Marker.FREQUENCIES -> buildFrequenciesList(result)
        Marker.FREQUENCY_HARMONIC_RANK -> buildFrequencyHarmonicRank(result)
        Marker.FREQUENCY_AVERAGE_RANK -> buildFrequencyAverageRank(result)
        Marker.PITCH_ACCENTS -> buildPitchAccents(result.term.reading, result.term.pitches, format = PitchFormat.COMPOSITE)
        Marker.PITCH_ACCENT_POSITIONS -> buildPitchAccents(result.term.reading, result.term.pitches, format = PitchFormat.POSITION)
        Marker.PITCH_ACCENT_CATEGORIES -> buildPitchCategories(result.term.reading, result.term.pitches)
        Marker.AUDIO -> ""
        Marker.MORAE -> buildMorae(result.term.reading)
        Marker.PITCH_ACCENT_GRAPHS -> buildPitchAccentGraphs(result.term.reading, result.term.pitches)
        Marker.SCREENSHOT -> screenshotFilename?.let { "<img src=\"$it\">" } ?: ""
        Marker.SEARCH_QUERY -> result.term.expression
        Marker.MANGA -> media?.mangaTitle?.let { escapeHtml(it) } ?: ""
        Marker.CHAPTER -> media?.chapterName?.let { escapeHtml(it) } ?: ""
        Marker.MEDIA -> {
            if (media != null && media.mangaTitle.isNotBlank() && media.chapterName.isNotBlank()) {
                "${escapeHtml(media.mangaTitle)}-${escapeHtml(media.chapterName)}"
            } else {
                ""
            }
        }
        else -> parseSingleGlossaryMarker(marker, result)
    }

    // =============================================================================
    // Single glossary marker parsing (Yomitan-style)
    // =============================================================================

    private fun parseSingleGlossaryMarker(marker: String, result: LookupResult): String {
        val prefix = "single-glossary-"
        if (!marker.startsWith(prefix)) return ""

        val rest = marker.substring(prefix.length)
        if (rest.isBlank()) return ""

        val tokens = rest.split("-").toMutableList()
        var hasBrief = false
        var hasFirst = false
        while (tokens.isNotEmpty()) {
            when (tokens.last().lowercase()) {
                "brief" -> {
                    hasBrief = true
                    tokens.removeAt(tokens.lastIndex)
                }
                "first" -> {
                    hasFirst = true
                    tokens.removeAt(tokens.lastIndex)
                }
                else -> break
            }
        }

        val dictName = tokens.joinToString("-").trim()
        if (dictName.isEmpty()) return ""

        return buildGlossary(
            result.term.glossaries,
            brief = hasBrief,
            noDictTag = false,
            firstOnly = hasFirst,
            dictionaryFilter = dictName,
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
            if (furigana.isNotEmpty()) "$text[$furigana]" else text
        }
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
    ): String {
        if (glossaries.isEmpty()) return ""

        val filteredGlossaries = if (dictionaryFilter != null) {
            glossaries.filter { it.dictName.contains(dictionaryFilter, ignoreCase = true) }.toTypedArray()
        } else {
            glossaries
        }

        if (filteredGlossaries.isEmpty()) return ""
        val entries = if (firstOnly) arrayOf(filteredGlossaries[0]) else filteredGlossaries

        val sb = StringBuilder()
        sb.append("""<div style="text-align: left;" class="yomitan-glossary">""")

        if (entries.size == 1) {
            sb.append(renderGlossarySingle(entries[0], brief, noDictTag))
        } else {
            sb.append("<ol>")
            for (entry in entries) {
                val dictAttr = attrEscape(entry.dictName)
                sb.append("""<li data-dictionary="$dictAttr">""")
                sb.append(renderGlossarySingle(entry, brief, noDictTag))
                sb.append("</li>")
            }
            sb.append("</ol>")
        }

        sb.append("</div>")
        return sb.toString()
    }

    private fun renderGlossarySingle(
        entry: GlossaryEntry,
        brief: Boolean,
        noDictTag: Boolean,
    ): String {
        val sb = StringBuilder()

        if (!brief) {
            val tagParts = mutableListOf<String>()
            if (entry.definitionTags.isNotBlank()) {
                tagParts += entry.definitionTags.split(" ").filter { it.isNotBlank() }
            }
            if (!noDictTag && entry.dictName.isNotBlank()) {
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
                trimmed.startsWith("[") -> arrayToHtml(JSONArray(trimmed), dictionary)
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

    private fun arrayToHtml(arr: JSONArray, dictionary: String): String {
        if (arr.length() == 0) return ""

        val allStrings = (0 until arr.length()).all { arr.get(it) is String }
        if (allStrings) {
            if (arr.length() == 1) return contentValueToHtml(arr.getString(0), dictionary)
            return "<ul>" + (0 until arr.length()).joinToString("") {
                "<li>${contentValueToHtml(arr.getString(it), dictionary)}</li>"
            } + "</ul>"
        }

        return (0 until arr.length()).joinToString("") { i ->
            contentValueToHtml(arr.get(i), dictionary)
        }
    }

    private fun objectToHtml(node: JSONObject, dictionary: String): String {
        val type = node.optString("type", "")

        if (type == "structured-content") {
            val content = node.opt("content") ?: return ""
            return contentValueToHtml(content, dictionary)
        }

        if (type == "image") {
            return imageNodeToHtml(node)
        }

        val tag = node.optString("tag", "").trim().lowercase()

        if (tag.isEmpty()) {
            val content = node.opt("content") ?: return ""
            return contentValueToHtml(content, dictionary)
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
                sb.append(""" data-$key="${attrEscape(dataObj.get(key).toString())}"""")
            }
        }

        val styleObj = node.optJSONObject("style")
        if (styleObj != null && styleObj.length() > 0) {
            val cssStr = styleObj.keys().asSequence().joinToString("; ") { prop ->
                "${camelToKebab(prop)}: ${attrEscape(styleObj.getString(prop))}"
            }
            sb.append(""" style="$cssStr"""")
        }

        sb.append(">")

        val content = node.opt("content")
        if (content != null) {
            if (tag == "table") {
                sb.append(tableContentToHtml(content, dictionary))
            } else {
                sb.append(contentValueToHtml(content, dictionary))
            }
        }

        sb.append("</$tag>")

        return if (tag == "table") {
            """<div class="gloss-sc-table-container">$sb</div>"""
        } else {
            sb.toString()
        }
    }

    private fun contentValueToHtml(value: Any?, dictionary: String): String = when (value) {
        null -> ""
        is String -> escapeHtmlWithLineBreaks(value)
        is Number -> value.toString()
        is Boolean -> value.toString()
        is JSONArray -> arrayToHtml(value, dictionary)
        is JSONObject -> objectToHtml(value, dictionary)
        else -> escapeHtml(value.toString())
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
        if (width > 0 || height > 0) {
            val cssWidth = if (width > 0) "${width}$sizeUnits" else "auto"
            val cssHeight = if (height > 0) "${height}$sizeUnits" else "auto"
            sb.append(""" style="width: $cssWidth; height: $cssHeight; vertical-align: middle;"""")
        }

        val appearance = node.optString("appearance", "")
        if (appearance == "monochrome") {
            sb.append(""" class="gloss-image-monochrome"""")
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
            contentValueToHtml(content, dictionary)
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
        return seen.joinToString(", ")
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
        return ""
    }

    private fun buildConjugation(result: LookupResult): String {
        val rules = result.term.rules
        if (rules.isNullOrEmpty()) return ""
        return rules
    }

    // =============================================================================
    // Frequencies
    // =============================================================================

    private fun buildFrequenciesList(result: LookupResult): String {
        return selectLowestFrequencyValue(result) ?: ""
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
                
                // 1. Number [0]
                sb.append("<span class=\"pitch-number\" style=\"font-weight: bold;\">[$position]</span>")
                
                // 2. SVG (Graph)
                sb.append(createPitchSvg(morae, position))
                
                // 3. Overline Text
                sb.append(renderOverlineText(morae, position))
                
                sb.append("</div>")
                "$prefix${sb.toString()}"
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

    private fun buildPitchCategories(reading: String, pitches: Array<PitchEntry>): String {
        val categories = linkedSetOf<String>()
        val moraCount = getMorae(reading).size
        if (moraCount == 0) return ""
        
        for (group in pitches) {
            for (pos in group.pitchPositions) {
                categories.add(pitchPositionToCategory(pos, moraCount))
            }
        }
        return categories.joinToString(",")
    }

    private fun pitchPositionToCategory(position: Int, moraCount: Int): String = when {
        position == 0 -> "heiban"
        position == 1 -> "atamadaka"
        position == moraCount -> "odaka"
        position > 1 -> "nakadaka"
        else -> "unknown"
    }

    // =============================================================================
    // HTML utilities
    // =============================================================================

    internal fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun attrEscape(text: String): String = text
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
            'ァ', 'ィ', 'ゥ', 'ェ', 'ォ', 'ッ', 'ャ', 'ュ', 'ョ', 'ヮ', 'ヵ', 'ヶ'
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
                    if (i == 0) false
                    else if (i < p) true
                    else false
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
