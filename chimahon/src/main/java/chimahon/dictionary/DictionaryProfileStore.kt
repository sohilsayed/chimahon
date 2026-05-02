package chimahon.dictionary

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages [DictionaryProfile]s backed by [DictionaryProfileRepository].
 * This class acts as a bridge between the legacy Preference-based logic
 * and the new SQL-backed cascading profile system.
 */
class DictionaryProfileStore(
    private val repository: DictionaryProfileRepository,
) {

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    val profilesFlow: kotlinx.coroutines.flow.Flow<List<DictionaryProfile>> = repository.getProfiles()

    val activeProfileFlow: kotlinx.coroutines.flow.Flow<DictionaryProfile> = 
        kotlinx.coroutines.flow.combine(
            repository.getProfileByLanguageFlow("global"),
            profilesFlow
        ) { globalProfile, profiles ->
            globalProfile ?: profiles.firstOrNull() ?: createFallbackDefault()
        }

    fun getProfiles(): List<DictionaryProfile> = runBlocking {
        repository.getProfiles().first()
    }

    fun getActiveProfile(): DictionaryProfile = runBlocking {
        // For legacy "Active Profile", we'll use a special "Global" mapping or just the first profile
        repository.getProfileByLanguage("global") 
            ?: repository.getProfiles().first().firstOrNull() 
            ?: createFallbackDefault()
    }

    fun getActiveId(): String = getActiveProfile().id

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    fun saveProfiles(profiles: List<DictionaryProfile>) = runBlocking {
        profiles.forEach { repository.upsertProfile(it) }
    }

    fun setActiveProfile(id: String) = runBlocking {
        // Map the "active" profile to "global" language for legacy support
        repository.setLanguageProfile("global", id)
    }

    // -------------------------------------------------------------------------
    // CRUD helpers
    // -------------------------------------------------------------------------

    fun addProfile(profile: DictionaryProfile) = runBlocking {
        repository.upsertProfile(profile)
        setActiveProfile(profile.id)
    }

    fun updateProfile(profile: DictionaryProfile) = runBlocking {
        repository.upsertProfile(profile)
    }

    fun deleteProfile(id: String): Boolean = runBlocking {
        val profiles = repository.getProfiles().first()
        if (profiles.size <= 1) return@runBlocking false
        
        repository.deleteProfile(id)
        
        // If the active profile was deleted, fall back to the first remaining one.
        if (getActiveId() == id) {
            val remaining = repository.getProfiles().first()
            if (remaining.isNotEmpty()) {
                setActiveProfile(remaining.first().id)
            }
        }
        true
    }

    // -------------------------------------------------------------------------
    // Migration
    // -------------------------------------------------------------------------

    fun migrateIfEmpty(
        defaultName: String,
        legacyValues: LegacyAnkiValues,
        allDictNames: List<String>,
    ) = runBlocking {
        if (repository.getProfiles().first().isNotEmpty()) return@runBlocking

        val fieldMap: Map<String, String> = runCatching {
            val obj = org.json.JSONObject(legacyValues.fieldMap)
            buildMap { obj.keys().forEach { key -> put(key, obj.getString(key)) } }
        }.getOrDefault(emptyMap())

        val profile = DictionaryProfile.createDefault(
            defaultName = defaultName,
            languageCode = "ja", // Legacy was always Japanese
            ankiDeck = legacyValues.deck,
            ankiModel = legacyValues.model,
            ankiFieldMap = fieldMap,
            ankiTags = legacyValues.tags,
            ankiDupCheck = legacyValues.dupCheck,
            ankiDupScope = legacyValues.dupScope,
            ankiDupAction = legacyValues.dupAction,
            ankiCropMode = legacyValues.cropMode,
            dictionaryOrder = allDictNames,
        )
        repository.upsertProfile(profile)
        setActiveProfile(profile.id)
    }

    private fun createFallbackDefault(): DictionaryProfile = DictionaryProfile.createDefault()

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
