package eu.kanade.tachiyomi.ui.dictionary

import tachiyomi.core.common.preference.PreferenceStore

class DictionaryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun popupWidth() = preferenceStore.getInt("pref_dictionary_popup_width", 340)

    fun popupHeight() = preferenceStore.getInt("pref_dictionary_popup_height", 450)

    fun popupScale() = preferenceStore.getInt("pref_dictionary_popup_scale", 100)

    fun dictionaryOrder() = preferenceStore.getString("pref_dictionary_order", "")

    fun ocrBoxScale() = preferenceStore.getFloat("pref_ocr_box_scale", 1.0f)

    fun showFrequencyHarmonic() = preferenceStore.getBoolean("pref_dict_show_frequency_harmonic", false)

    // Anki integration
    fun ankiEnabled() = preferenceStore.getBoolean("pref_anki_enabled", false)

    fun ankiDeck() = preferenceStore.getString("pref_anki_deck", "")

    fun ankiModel() = preferenceStore.getString("pref_anki_model", "")

    fun ankiFieldMap() = preferenceStore.getString("pref_anki_field_map", "{}")

    fun ankiDuplicateCheck() = preferenceStore.getBoolean("pref_anki_duplicate_check", true)

    fun ankiDuplicateScope() = preferenceStore.getString("pref_anki_duplicate_scope", "deck")

    fun ankiDuplicateAction() = preferenceStore.getString("pref_anki_duplicate_action", "prevent")

    fun ankiDefaultTags() = preferenceStore.getString("pref_anki_default_tags", "chimahon")
}
