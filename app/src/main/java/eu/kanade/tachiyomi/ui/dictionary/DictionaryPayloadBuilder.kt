package eu.kanade.tachiyomi.ui.dictionary

import android.content.Context
import chimahon.DictionaryStyle
import chimahon.LookupResult
import chimahon.anki.AnkiProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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

/**
 * Serializes a single lookup result to JSON for lazy entry loading.
 * Used by PayloadBridge.getEntry(index) — avoids serializing all results at once.
 */
internal fun buildResultEntryJson(result: LookupResult, index: Int, profile: AnkiProfile, context: Context, groupPitches: Boolean): JsonObject {
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
            putJsonArray("glossaries") {
                for (g in result.term.glossaries) {
                    add(buildJsonObject {
                        put("dictName", g.dictName)
                        put("glossary", g.glossary)
                        put("definitionTags", g.definitionTags)
                        put("termTags", g.termTags)
                    })
                }
            }
            putJsonArray("frequencies") {
                for (group in result.term.frequencies) {
                    add(buildJsonObject {
                        put("dictName", group.dictName)
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
            putJsonArray("pitches") {
                val allPitches = result.term.pitches
                val priorityTitles = profile.dictionaryOrder.map { getDictionaryTitle(context, it) }
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
 * Builds the full render payload with all results inlined.
 * Used as a fallback for the DictionaryTab and other non-popup consumers.
 */
internal fun buildRenderPayload(
    context: Context,
    results: List<LookupResult>,
    styles: List<DictionaryStyle>,
    mediaDataUris: Map<String, String>,
    placeholder: String,
    isDark: Boolean,
    showFrequencyHarmonic: Boolean,
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
    val config = buildConfigPayload(
        context, styles, mediaDataUris, placeholder, isDark,
        showFrequencyHarmonic, groupTerms, showPitchDiagram, showPitchNumber, showPitchText,
        wordAudioAutoplay, activeProfile, existingExpressions, tabs, recursiveNavMode,
        wordAudioEnabled, showNavigationButtons, groupPitches,
    )
    config.forEach { (key, value) -> put(key, value) }

    putJsonArray("results") {
        for ((index, result) in results.withIndex()) {
            add(buildResultEntryJson(result, index, activeProfile, context, groupPitches))
        }
    }
}

/**
 * Serializes a list of results to a JSON array string for lazy entry loading.
 */
internal fun buildResultsJsonArray(results: List<LookupResult>, profile: AnkiProfile, context: Context, groupPitches: Boolean): String {
    return Json.encodeToString(
        JsonArray(results.mapIndexed { index, result -> buildResultEntryJson(result, index, profile, context, groupPitches) })
    )
}

internal fun String.toJavascriptExpression(): String =
    replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

private val dictionaryTitleCache = java.util.concurrent.ConcurrentHashMap<String, String>()

internal fun getDictionaryTitle(context: Context, dirName: String): String {
    return dictionaryTitleCache.getOrPut(dirName) {
        val dictionariesDir = File(context.getExternalFilesDir(null), "dictionaries")
        for (type in listOf("term", "frequency", "pitch")) {
            val indexFile = File(File(dictionariesDir, type), "$dirName/index.json")
            if (indexFile.exists()) {
                try {
                    val json = indexFile.readText()
                    return@getOrPut org.json.JSONObject(json).optString("title", dirName)
                } catch (_: Exception) {
                    return@getOrPut dirName
                }
            }
        }
        dirName
    }
}
