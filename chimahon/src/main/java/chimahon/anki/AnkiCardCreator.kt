package chimahon.anki

import android.content.Context
import chimahon.Cloze
import chimahon.GlossaryEntry
import chimahon.LookupResult
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

    val ALL: List<String> = listOf(
        EXPRESSION, READING, FURIGANA, FURIGANA_PLAIN,
        GLOSSARY, GLOSSARY_BRIEF, GLOSSARY_PLAIN, GLOSSARY_NO_DICT,
        GLOSSARY_FIRST, GLOSSARY_FIRST_BRIEF,
        SENTENCE, CLOZE_PREFIX, CLOZE_BODY, CLOZE_BODY_KANA, CLOZE_SUFFIX,
        TAGS, PART_OF_SPEECH, CONJUGATION,
        DICTIONARY, DICTIONARY_ALIAS,
        FREQUENCIES, FREQUENCY_HARMONIC_RANK, FREQUENCY_AVERAGE_RANK,
        PITCH_ACCENTS, PITCH_ACCENT_POSITIONS, PITCH_ACCENT_CATEGORIES,
    )

    val ALL_WITH_TODO: List<String> = ALL + listOf(AUDIO, SCREENSHOT, SEARCH_QUERY)

    val TODO_MARKERS = setOf(AUDIO, SCREENSHOT, SEARCH_QUERY)

    val AUTO_DETECT_ALIASES: Map<String, List<String>> = mapOf(
        EXPRESSION to listOf("expression", "phrase", "term", "word"),
        READING to listOf("reading", "expression-reading", "word-reading"),
        FURIGANA to listOf("furigana", "expression-furigana", "word-furigana"),
        GLOSSARY to listOf("glossary", "definition", "meaning"),
        AUDIO to listOf("audio", "sound", "word-audio", "term-audio"),
        DICTIONARY to listOf("dictionary", "dict"),
        PITCH_ACCENTS to listOf("pitch-accents", "pitch-accent", "pitch-pattern"),
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
    data object Success : AnkiResult()
    data class CardExists(val noteId: Long) : AnkiResult()
    data class Error(val message: String) : AnkiResult()
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
    ): AnkiResult {
        android.util.Log.d(TAG, "addToAnki: deck=$deck, model=$model, fieldMapJson=$fieldMapJson")
        
        if (deck.isBlank() || model.isBlank()) {
            android.util.Log.w(TAG, "addToAnki: NotConfigured - deck or model is blank")
            return AnkiResult.NotConfigured
        }

        val bridge = AnkiDroidBridge(context)
        val fieldMap = parseFieldMap(fieldMapJson)
        android.util.Log.d(TAG, "addToAnki: parsed fieldMap=$fieldMap")
        val cloze = if (sentence.isNotEmpty() && offset >= 0) {
            buildCloze(sentence, offset, result.term.expression, result.term.reading)
        } else {
            null
        }
        val fields = buildFields(result, fieldMap, cloze)
        android.util.Log.d(TAG, "addToAnki: built fields=$fields")
        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (dupCheck) {
            val existing = bridge.findNotes(result.term.expression, model)
            if (existing.isNotEmpty()) {
                when (dupAction) {
                    "prevent" -> return AnkiResult.CardExists(existing.first())
                    "overwrite" -> {
                        bridge.updateNoteFields(existing.first(), fields)
                        return AnkiResult.Success
                    }
                }
            }
        }

        bridge.addNote(deckName = deck, modelName = model, fields = fields, tags = tagList)
        return AnkiResult.Success
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
    ): Map<String, String> = fieldMap.mapValues { (_, template) ->
        formatField(template, result, cloze)
    }

    private fun formatField(template: String, result: LookupResult, cloze: Cloze?): String {
        if (template.isBlank()) return ""
        val spacedTemplate = template.replace("}{", "} {")
        return MARKER_PATTERN.replace(spacedTemplate) { match ->
            renderMarker(match.groupValues[1], result, cloze)
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

    fun renderMarker(marker: String, result: LookupResult, cloze: Cloze? = null): String = when (marker) {
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
        Marker.PITCH_ACCENTS -> buildPitchAccents(result.term.pitches, format = PitchFormat.TEXT)
        Marker.PITCH_ACCENT_POSITIONS -> buildPitchAccents(result.term.pitches, format = PitchFormat.POSITION)
        Marker.PITCH_ACCENT_CATEGORIES -> buildPitchCategories(result.term.pitches)
        Marker.AUDIO -> ""
        Marker.SCREENSHOT -> ""
        Marker.SEARCH_QUERY -> result.term.expression
        else -> ""
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
    ): String {
        if (glossaries.isEmpty()) return ""
        val entries = if (firstOnly) arrayOf(glossaries[0]) else glossaries

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

    private fun glossaryToHtml(raw: String, dictionary: String = ""): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return escapeHtml(trimmed)
        }
        return try {
            when {
                trimmed.startsWith("[") -> arrayToHtml(JSONArray(trimmed), dictionary)
                else -> objectToHtml(JSONObject(trimmed), dictionary)
            }
        } catch (_: Exception) {
            escapeHtml(trimmed)
        }
    }

    private fun glossaryToPlainText(raw: String): String {
        val html = glossaryToHtml(raw)
        return html
            .replace(Regex("<[^>]+>"), " ")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").replace("&quot;", "\"")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun arrayToHtml(arr: JSONArray, dictionary: String): String {
        if (arr.length() == 0) return ""

        val allStrings = (0 until arr.length()).all { arr.get(it) is String }
        if (allStrings) {
            if (arr.length() == 1) return escapeHtml(arr.getString(0))
            return "<ul>" + (0 until arr.length()).joinToString("") {
                "<li>${escapeHtml(arr.getString(it))}</li>"
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
        is String -> escapeHtml(value)
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
        val freqs = result.term.frequencies
        if (freqs.isEmpty()) return ""
        val sb = StringBuilder("""<ul style="text-align: left;">""")
        for (group in freqs) {
            for (f in group.frequencies) {
                sb.append("<li>")
                if (group.dictName.isNotBlank()) {
                    sb.append("${escapeHtml(group.dictName)}: ")
                }
                sb.append(escapeHtml(f.value.toString()))
                sb.append("</li>")
            }
        }
        sb.append("</ul>")
        return sb.toString()
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

    private enum class PitchFormat { TEXT, POSITION }

    private fun buildPitchAccents(pitches: Array<PitchEntry>, format: PitchFormat): String {
        if (pitches.isEmpty()) return ""

        val allPitches = pitches.flatMap { group ->
            group.pitchPositions.map { pos -> Pair(group.dictName, pos) }
        }
        if (allPitches.isEmpty()) return ""

        return if (allPitches.size == 1) {
            val (dict, pos) = allPitches[0]
            renderPitchItem(dict, pos, format)
        } else {
            "<ol>" + allPitches.joinToString("") { (dict, pos) ->
                "<li>${renderPitchItem(dict, pos, format)}</li>"
            } + "</ol>"
        }
    }

    private fun renderPitchItem(dictName: String, position: Int, format: PitchFormat): String {
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
        }
    }

    private fun buildPitchCategories(pitches: Array<PitchEntry>): String {
        val categories = linkedSetOf<String>()
        for (group in pitches) {
            val moraCount = group.pitchPositions.size
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
}
