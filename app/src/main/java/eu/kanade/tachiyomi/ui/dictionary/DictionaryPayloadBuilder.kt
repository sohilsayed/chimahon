package eu.kanade.tachiyomi.ui.dictionary

import android.content.Context
import chimahon.DictionaryStyle
import chimahon.LookupResult
import chimahon.anki.AnkiProfile
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/** Represents one entry in the scrollable lookup-history tab bar shown inside the WebView. */
data class TabInfo(val label: String, val active: Boolean)

/**
 * Full signature — used as a key for the payload builder.
 */
internal data class DictionaryRenderSignature(
    val results: List<LookupResult>,
    val styles: List<DictionaryStyle>,
    val placeholder: String,
    val isDark: Boolean,
    val showFrequencyHarmonic: Boolean,
    val showFrequencyAverage: Boolean,
    val groupTerms: Boolean,
    val showPitchDiagram: Boolean,
    val showPitchNumber: Boolean,
    val showPitchText: Boolean,
    val activeProfile: AnkiProfile,
    val tabs: List<TabInfo>,
    val recursiveNavMode: String,
    val wordAudioEnabled: Boolean,
    val wordAudioAutoplay: Boolean,
    val showNavigationButtons: Boolean,
    val groupPitches: Boolean,
)

private fun buildResultEntryJson(result: LookupResult, index: Int, priorityMap: Map<String, Int>, groupPitches: Boolean): JsonObject {
    val glossaries = result.term.glossaries.sortedByDictionaryPriority(priorityMap) { it.dictName }
    val frequencies = result.term.frequencies.sortedByDictionaryPriority(priorityMap) { it.dictName }
    val pitches = result.term.pitches.sortedByDictionaryPriority(priorityMap) { it.dictName }
    val ruleTags = splitTags(result.term.rules).distinct()
    return buildJsonObject {
        put("index", index)
        put("matched", result.matched)
        put("deinflected", result.deinflected)
        putJsonArray("process") {
            for (p in result.process) {
                add(buildJsonObject {
                    put("name", p.name)
                    put("description", p.description)
                })
            }
        }
        put("term", buildJsonObject {
            put("expression", result.term.expression)
            put("reading", result.term.reading)
            put("rules", result.term.rules)
            putJsonArray("ruleTags") {
                for (tag in ruleTags) add(JsonPrimitive(tag))
            }
            putJsonArray("glossaries") {
                for (g in glossaries) {
                    add(buildGlossaryPayload(g))
                }
            }
            putJsonArray("frequencies") {
                for (group in frequencies) {
                    add(buildJsonObject {
                        put("dictName", group.dictName)
                        put("displayValueText", frequencyDisplayText(group))
                        putJsonArray("frequencies") {
                            for (item in group.frequencies) {
                                add(buildJsonObject {
                                    put("value", item.value)
                                    put("displayValue", item.displayValue)
                                })
                            }
                        }
                    })
                }
            }
            harmonicRank(frequencies)?.let { put("frequencyHarmonicRank", it) }
            averageRank(frequencies)?.let { put("frequencyAverageRank", it) }
            putJsonArray("pitches") {
                val allPitches = pitches.toTypedArray()
                val priorityTitles = priorityMap.entries.sortedBy { it.value }.map { it.key }
                if (groupPitches) {
                    val orderedPitches = LinkedHashSet<Int>()
                    for (title in priorityTitles) {
                        allPitches.filter { it.dictName == title }
                            .forEach { group -> orderedPitches.addAll(group.pitchPositions.toList()) }
                    }
                    for (group in allPitches) {
                        if (group.dictName !in priorityTitles) {
                            orderedPitches.addAll(group.pitchPositions.toList())
                        }
                    }
                    val allDictIds = allPitches.map { it.dictName }.distinct()
                    if (orderedPitches.isNotEmpty()) {
                        val sortedTitles = allDictIds.sortedBy {
                            val idx = priorityTitles.indexOf(it)
                            if (idx == -1) Int.MAX_VALUE else idx
                        }
                        add(buildJsonObject {
                            put("dictName", sortedTitles.joinToString(", "))
                            putJsonArray("pitchPositions") {
                                for (pos in orderedPitches) add(JsonPrimitive(pos))
                            }
                        })
                    }
                } else {
                    for (group in allPitches) {
                        add(buildJsonObject {
                            put("dictName", group.dictName)
                            putJsonArray("pitchPositions") {
                                for (pos in group.pitchPositions.distinct()) add(JsonPrimitive(pos))
                            }
                        })
                    }
                }
            }
        })
    }
}

internal fun orderLookupResultsForDisplay(
    results: List<LookupResult>,
    profile: AnkiProfile,
    context: Context,
): List<LookupResult> {
    val priorityMap = dictionaryPriorityMap(profile, context)
    if (priorityMap.isEmpty()) return results
    if (results.size <= 1) return results.map { it.withOrderedDictionaries(priorityMap) }

    return results
        .map { it.withOrderedDictionaries(priorityMap) }
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<LookupResult>> { it.value.matched.length }
                .thenBy { dictionaryPriority(priorityMap, it.value.term.glossaries.firstOrNull()?.dictName) }
                .thenBy { it.index },
        )
        .map { it.value }
}

/**
 * Builds the config portion of the render payload — everything except results.
 * Results are serialized separately and pulled lazily via PayloadBridge.getEntry(index).
 */
internal fun buildConfigPayload(
    context: Context,
    styles: List<DictionaryStyle>,
    mediaDataUris: Map<String, String>,
    placeholder: String,
    isDark: Boolean,
    showFrequencyHarmonic: Boolean,
    showFrequencyAverage: Boolean,
    groupTerms: Boolean,
    showPitchDiagram: Boolean,
    showPitchNumber: Boolean,
    showPitchText: Boolean,
    wordAudioAutoplay: Boolean,
    activeProfile: AnkiProfile,
    existingExpressions: Set<String> = emptySet(),
    tabs: List<TabInfo> = emptyList(),
    recursiveNavMode: String = "tabs",
    wordAudioEnabled: Boolean = true,
    showNavigationButtons: Boolean = true,
    groupPitches: Boolean = false,
): JsonObject = buildJsonObject {
    val orderedTitles = activeProfile.dictionaryOrder.map { getDictionaryTitle(context, it) }
    val displayModesByTitle = activeProfile.dictionaryDisplayModes.mapKeys { (dirName, _) -> getDictionaryTitle(context, dirName) }

    putJsonArray("dictionaryOrder") { for (title in orderedTitles) add(JsonPrimitive(title)) }
    put("ankiEnabled", activeProfile.ankiEnabled)
    put("ankiDupAction", activeProfile.ankiDupAction)
    put("dictionaryCollapseMode", activeProfile.dictionaryCollapseMode)
    put("dictionaryDisplayModes", buildJsonObject {
        for ((title, mode) in displayModesByTitle) put(title, mode)
    })
    put("placeholder", placeholder)
    put("isDark", isDark)
    put("showFrequencyHarmonic", showFrequencyHarmonic)
    put("showFrequencyAverage", showFrequencyAverage)
    put("groupTerms", groupTerms)
    put("showPitchDiagram", showPitchDiagram)
    put("showPitchNumber", showPitchNumber)
    put("showPitchText", showPitchText)
    put("wordAudioAutoplay", wordAudioAutoplay)
    put("wordAudioEnabled", wordAudioEnabled)
    put("recursiveNavMode", recursiveNavMode)
    put("showNavigationButtons", showNavigationButtons)
    putJsonArray("tabs") {
        for (tab in tabs) add(buildJsonObject { put("label", tab.label); put("active", tab.active) })
    }
    putJsonArray("existingExpressions") { for (expr in existingExpressions) add(JsonPrimitive(expr)) }
    putJsonArray("styles") {
        for (style in styles) add(buildJsonObject { put("dictName", style.dictName); put("styles", style.styles) })
    }
    put("mediaDataUris", buildJsonObject { for ((key, value) in mediaDataUris) put(key, value) })
    putJsonArray("results") {}
}

/**
 * Serializes result entries separately so PayloadBridge.getEntry(index) can pull
 * one entry at a time instead of parsing a single large results array.
 */
internal fun buildResultEntryJsonStrings(
    results: List<LookupResult>,
    profile: AnkiProfile,
    context: Context,
    groupPitches: Boolean,
): List<String> {
    val priorityMap = dictionaryPriorityMap(profile, context)
    return results.mapIndexed { index, result ->
        buildResultEntryJson(result, index, priorityMap, groupPitches).toString()
    }
}

internal fun String.toJavascriptExpression(): String =
    replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

private val dictionaryTitleCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/** Invalidate the cached title for a single directory. Call when display name or index.json title changes. */
internal fun invalidateDictionaryTitle(dirName: String) {
    dictionaryTitleCache.remove(dirName)
}

internal fun getDictionaryTitle(context: Context, dirName: String): String {
    return dictionaryTitleCache.getOrPut(dirName) {
        // 1. Display name (user-set custom name, survives updates)
        val prefs = Injekt.get<DictionaryPreferences>()
        val displayName = prefs.getDisplayName(dirName)
        if (displayName != null) return@getOrPut displayName

        // 2. index.json title
        val dictionariesDir = File(context.getExternalFilesDir(null), "dictionaries")
        for (type in listOf("term", "frequency", "pitch")) {
            val indexFile = File(File(dictionariesDir, type), "$dirName/index.json")
            if (indexFile.exists()) {
                try {
                    val json = indexFile.readText()
                    val title = org.json.JSONObject(json).optString("title", dirName)
                    if (title.isNotBlank()) return@getOrPut title
                } catch (_: Exception) {
                    // fall through to dirName
                }
            }
        }

        // 3. Directory name (stable fallback)
        dirName
    }
}

private fun dictionaryPriorityMap(profile: AnkiProfile, context: Context): Map<String, Int> =
    profile.dictionaryOrder
        .map { getDictionaryTitle(context, it) }
        .distinct()
        .withIndex()
        .associate { it.value to it.index }

private fun dictionaryPriority(priorityMap: Map<String, Int>, dictName: String?): Int =
    dictName?.let { priorityMap[it] } ?: Int.MAX_VALUE

private fun buildGlossaryPayload(glossary: chimahon.GlossaryEntry): JsonObject =
    buildJsonObject {
        val definitionTags = splitTags(glossary.definitionTags)
        put("dictName", glossary.dictName)
        put("glossary", glossary.glossary)
        put("definitionTags", glossary.definitionTags)
        putJsonArray("definitionTagList") {
            for (tag in definitionTags) add(JsonPrimitive(tag))
        }
        put("termTags", glossary.termTags)
    }

private fun splitTags(value: String): List<String> =
    value.split(Regex("\\s+")).filter { it.isNotBlank() }

private fun frequencyDisplayText(group: chimahon.FrequencyEntry): String =
    group.frequencies
        .mapNotNull { item -> item.displayValue.takeIf { it.isNotBlank() } ?: item.value.takeIf { it > 0 }?.toString() }
        .joinToString(", ")

private fun harmonicRank(frequencies: List<chimahon.FrequencyEntry>): Int? {
    val values = uniqueFrequencyValues(frequencies)
    if (values.isEmpty()) return null
    val reciprocalSum = values.sumOf { 1.0 / it }
    if (reciprocalSum <= 0.0) return null
    return kotlin.math.floor(values.size / reciprocalSum).toInt()
}

private fun averageRank(frequencies: List<chimahon.FrequencyEntry>): Double? {
    val values = uniqueFrequencyValues(frequencies)
    if (values.isEmpty()) return null
    return values.average()
}

private fun uniqueFrequencyValues(frequencies: List<chimahon.FrequencyEntry>): List<Int> {
    val seen = HashSet<String>()
    return frequencies.mapNotNull { group ->
        if (!seen.add(group.dictName)) return@mapNotNull null
        group.frequencies.firstOrNull { it.value > 0 }?.value
    }
}

private fun LookupResult.withOrderedDictionaries(priorityMap: Map<String, Int>): LookupResult {
    if (priorityMap.isEmpty()) return this
    return copy(
        term = term.copy(
            glossaries = term.glossaries.sortedByDictionaryPriority(priorityMap) { it.dictName }.toTypedArray(),
            frequencies = term.frequencies.sortedByDictionaryPriority(priorityMap) { it.dictName }.toTypedArray(),
            pitches = term.pitches.sortedByDictionaryPriority(priorityMap) { it.dictName }.toTypedArray(),
        ),
    )
}

private inline fun <T> Array<T>.sortedByDictionaryPriority(
    priorityMap: Map<String, Int>,
    crossinline dictName: (T) -> String,
): List<T> {
    if (priorityMap.isEmpty() || size <= 1) return toList()
    return withIndex()
        .sortedWith(
            compareBy<IndexedValue<T>> { dictionaryPriority(priorityMap, dictName(it.value)) }
                .thenBy { it.index },
        )
        .map { it.value }
}
