package chimahon.dictionary

import org.json.JSONArray
import org.json.JSONObject

/**
 * A named mining configuration that bundles together:
 * - AnkiDroid deck/model/field-map/tag settings
 * - The ordered list of dictionaries to use
 * - Which of those dictionaries are enabled for lookup
 * - The language engine to use (ja, ko, ar, zh, en, etc.)
 */
data class DictionaryProfile(
    val id: String,
    val name: String,
    val languageCode: String = "ja",
    // Anki settings
    val ankiEnabled: Boolean = false,
    val ankiDeck: String = "",
    val ankiModel: String = "",
    val ankiFieldMap: Map<String, String> = emptyMap(),
    val ankiTags: String = "chimahon",
    val ankiDupCheck: Boolean = true,
    val ankiDupScope: String = "deck",
    val ankiDupAction: String = "prevent",
    val ankiCropMode: String = "full",
    // Dictionary configuration
    val dictionaryOrder: List<String> = emptyList(),
    val enabledDictionaries: Set<String> = emptySet(), // empty = all enabled
) {
    /** @return ankiFieldMap serialised to a JSON object string. */
    fun ankiFieldMapJson(): String = JSONObject(ankiFieldMap).toString()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("languageCode", languageCode)
        put("ankiEnabled", ankiEnabled)
        put("ankiDeck", ankiDeck)
        put("ankiModel", ankiModel)
        put("ankiFieldMap", JSONObject(ankiFieldMap))
        put("ankiTags", ankiTags)
        put("ankiDupCheck", ankiDupCheck)
        put("ankiDupScope", ankiDupScope)
        put("ankiDupAction", ankiDupAction)
        put("ankiCropMode", ankiCropMode)
        put("dictionaryOrder", JSONArray(dictionaryOrder))
        put("enabledDictionaries", JSONArray(enabledDictionaries.toList()))
    }

    companion object {

        private fun parseFieldMap(raw: String): Map<String, String> = runCatching {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { key -> put(key, obj.getString(key)) } }
        }.getOrDefault(emptyMap())

        fun fromJson(json: JSONObject): DictionaryProfile = DictionaryProfile(
            id = json.getString("id"),
            name = json.getString("name"),
            languageCode = json.optString("languageCode", "ja"),
            ankiEnabled = json.optBoolean("ankiEnabled", false),
            ankiDeck = json.optString("ankiDeck", ""),
            ankiModel = json.optString("ankiModel", ""),
            ankiFieldMap = parseFieldMap(json.optString("ankiFieldMap", "{}")),
            ankiTags = json.optString("ankiTags", "chimahon"),
            ankiDupCheck = json.optBoolean("ankiDupCheck", true),
            ankiDupScope = json.optString("ankiDupScope", "deck"),
            ankiDupAction = json.optString("ankiDupAction", "prevent"),
            ankiCropMode = json.optString("ankiCropMode", "full"),
            dictionaryOrder = json.optJSONArray("dictionaryOrder")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                ?: emptyList(),
            enabledDictionaries = json.optJSONArray("enabledDictionaries")
                ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }
                ?: emptySet(),
        )

        fun createDefault(
            defaultName: String = "Default",
            languageCode: String = "ja",
            ankiEnabled: Boolean = false,
            ankiDeck: String = "",
            ankiModel: String = "",
            ankiFieldMap: Map<String, String> = emptyMap(),
            ankiTags: String = "chimahon",
            ankiDupCheck: Boolean = true,
            ankiDupScope: String = "deck",
            ankiDupAction: String = "prevent",
            ankiCropMode: String = "full",
            dictionaryOrder: List<String> = emptyList(),
        ): DictionaryProfile = DictionaryProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = defaultName,
            languageCode = languageCode,
            ankiEnabled = ankiEnabled,
            ankiDeck = ankiDeck,
            ankiModel = ankiModel,
            ankiFieldMap = ankiFieldMap,
            ankiTags = ankiTags,
            ankiDupCheck = ankiDupCheck,
            ankiDupScope = ankiDupScope,
            ankiDupAction = ankiDupAction,
            ankiCropMode = ankiCropMode,
            dictionaryOrder = dictionaryOrder,
            enabledDictionaries = emptySet(),
        )

        /** A safe fallback used as a Compose initial state value before the DB emits. */
        fun default(): DictionaryProfile = DictionaryProfile(
            id = "default",
            name = "Default",
            languageCode = "ja",
        )
    }
}
