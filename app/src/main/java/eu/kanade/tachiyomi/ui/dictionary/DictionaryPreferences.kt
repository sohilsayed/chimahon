package eu.kanade.tachiyomi.ui.dictionary

import chimahon.anki.AnkiProfile
import chimahon.anki.AnkiProfileStore
import tachiyomi.core.common.preference.PreferenceStore

class DictionaryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun popupWidth() = preferenceStore.getInt("pref_dictionary_popup_width", 300)

    fun popupHeight() = preferenceStore.getInt("pref_dictionary_popup_height", 360)

    fun popupScale() = preferenceStore.getInt("pref_dictionary_popup_scale", 100)

    fun ocrBoxScale() = preferenceStore.getFloat("pref_ocr_box_scale", 1.0f)

    fun showFrequencyHarmonic() = preferenceStore.getBoolean("pref_dict_show_frequency_harmonic", false)

    fun groupTerms() = preferenceStore.getBoolean("pref_dict_group_terms", true)
    fun showPitchDiagram() = preferenceStore.getBoolean("pref_dict_show_pitch_diagram", true)
    fun showPitchNumber() = preferenceStore.getBoolean("pref_dict_show_pitch_number", true)
    fun showPitchText() = preferenceStore.getBoolean("pref_dict_show_pitch_text", true)

    fun recursiveLookupMode() = preferenceStore.getString("pref_dict_recursive_lookup_mode", "tabs")

    // -------------------------------------------------------------------------
    // Profile storage (raw pref keys — consumed by AnkiProfileStore and settings UI)
    // -------------------------------------------------------------------------

    fun rawProfiles() = preferenceStore.getString("pref_anki_profiles", "[]")

    fun rawActiveProfileId() = preferenceStore.getString("pref_active_profile_id", "")

    // -------------------------------------------------------------------------
    // AnkiProfileStore (single instance, lazy)
    // -------------------------------------------------------------------------

    val profileStore: AnkiProfileStore by lazy {
        AnkiProfileStore(
            readProfiles = { rawProfiles().get() },
            writeProfiles = { rawProfiles().set(it) },
            readActiveId = { rawActiveProfileId().get() },
            writeActiveId = { rawActiveProfileId().set(it) },
        ).apply {
            if (getProfiles().isEmpty()) {
                val legacyDicts = dictionaryOrder().get()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                migrateIfEmpty(
                    defaultName = "Default",
                    legacyValues = AnkiProfileStore.LegacyAnkiValues(
                        deck = legacyAnkiDeck().get(),
                        model = legacyAnkiModel().get(),
                        fieldMap = legacyAnkiFieldMap().get(),
                        tags = legacyAnkiDefaultTags().get(),
                        dupCheck = legacyAnkiDuplicateCheck().get(),
                        dupScope = legacyAnkiDuplicateScope().get(),
                        dupAction = legacyAnkiDuplicateAction().get(),
                        cropMode = legacyAnkiCropMode().get(),
                    ),
                    allDictNames = legacyDicts,
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Legacy flat-key READERS — only used for one-time migration.
    // -------------------------------------------------------------------------

    fun legacyAnkiDeck() = preferenceStore.getString("pref_anki_deck", "")
    fun legacyAnkiModel() = preferenceStore.getString("pref_anki_model", "")
    fun legacyAnkiFieldMap() = preferenceStore.getString("pref_anki_field_map", "{}")
    fun legacyAnkiDuplicateCheck() = preferenceStore.getBoolean("pref_anki_duplicate_check", true)
    fun legacyAnkiDuplicateScope() = preferenceStore.getString("pref_anki_duplicate_scope", "deck")
    fun legacyAnkiDuplicateAction() = preferenceStore.getString("pref_anki_duplicate_action", "prevent")
    fun legacyAnkiDefaultTags() = preferenceStore.getString("pref_anki_default_tags", "chimahon")
    fun legacyAnkiCropMode() = preferenceStore.getString("pref_dict_anki_crop_mode", "full")

    /** Legacy global dictionary order — kept only to supply the initial migration list. */
    fun dictionaryOrder() = preferenceStore.getString("pref_dictionary_order", "")
}
