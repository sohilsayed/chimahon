package chimahon.anki

import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages a list of [AnkiProfile]s backed by two plain String preference
 * values supplied by the caller:
 *
 *   - [profilesJson]   – the raw JSON array string (read + write via lambdas)
 *   - [activeIdJson]   – the ID string of the currently active profile
 *
 * All persistence is delegated back to the caller so that this class has
 * zero dependencies on Android SharedPreferences or Tachiyomi's PreferenceStore.
 */
class AnkiProfileStore(
    private val readProfiles: () -> String,
    private val writeProfiles: (String) -> Unit,
    private val readActiveId: () -> String,
    private val writeActiveId: (String) -> Unit,
) {

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    fun getProfiles(): List<AnkiProfile> {
        val raw = readProfiles().trim()
        if (raw.isBlank() || raw == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { AnkiProfile.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun getActiveProfile(): AnkiProfile {
        val profiles = getProfiles()
        if (profiles.isEmpty()) return createFallbackDefault()
        val activeId = readActiveId()
        return profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    fun getActiveId(): String = readActiveId()

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    fun saveProfiles(profiles: List<AnkiProfile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        writeProfiles(arr.toString())
    }

    fun setActiveProfile(id: String) {
        writeActiveId(id)
    }

    // -------------------------------------------------------------------------
    // CRUD helpers
    // -------------------------------------------------------------------------

    fun addProfile(profile: AnkiProfile) {
        val updated = getProfiles() + profile
        saveProfiles(updated)
        // Auto-select the new profile
        writeActiveId(profile.id)
    }

    fun updateProfile(profile: AnkiProfile) {
        val profiles = getProfiles()
        if (profiles.isEmpty()) {
            saveProfiles(listOf(profile))
            writeActiveId(profile.id)
            return
        }
        val updated = profiles.map { if (it.id == profile.id) profile else it }
        saveProfiles(updated)
    }

    /**
     * Deletes the profile with [id].
     * Returns false (and does nothing) if it is the last profile, so the
     * caller can show the user an appropriate message.
     */
    fun deleteProfile(id: String): Boolean {
        val profiles = getProfiles()
        if (profiles.size <= 1) return false
        val updated = profiles.filter { it.id != id }
        saveProfiles(updated)
        // If the active profile was deleted, fall back to the first remaining one.
        if (readActiveId() == id) {
            writeActiveId(updated.first().id)
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Migration
    // -------------------------------------------------------------------------

    /**
     * One-time migration: if the profiles list is empty, build a "Default"
     * profile from legacy flat-key values passed in by the caller and persist it.
     *
     * @param defaultName   Localised name for the first profile (e.g. "Default")
     * @param legacyValues  All the old flat preference values
     * @param allDictNames  Ordered list of all dictionary names already on disk
     */
    fun migrateIfEmpty(
        defaultName: String,
        legacyValues: LegacyAnkiValues,
        allDictNames: List<String>,
    ) {
        if (getProfiles().isNotEmpty()) return

        val profile = AnkiProfile.createDefault(
            defaultName = defaultName,
            ankiDeck = legacyValues.deck,
            ankiModel = legacyValues.model,
            ankiFieldMap = legacyValues.fieldMap,
            ankiTags = legacyValues.tags,
            ankiDupCheck = legacyValues.dupCheck,
            ankiDupScope = legacyValues.dupScope,
            ankiDupAction = legacyValues.dupAction,
            ankiCropMode = legacyValues.cropMode,
            dictionaryOrder = allDictNames,
        )
        saveProfiles(listOf(profile))
        writeActiveId(profile.id)
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private fun createFallbackDefault(): AnkiProfile = AnkiProfile.createDefault()

    // -------------------------------------------------------------------------
    // Value class for passing legacy flat-pref values
    // -------------------------------------------------------------------------

    data class LegacyAnkiValues(
        val deck: String = "",
        val model: String = "",
        val fieldMap: String = "{}",
        val tags: String = "chimahon",
        val dupCheck: Boolean = true,
        val dupScope: String = "deck",
        val dupAction: String = "prevent",
        val cropMode: String = "full",
    )
}
