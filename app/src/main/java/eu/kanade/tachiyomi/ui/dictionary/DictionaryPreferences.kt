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
}
