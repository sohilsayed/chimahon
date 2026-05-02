package tachiyomi.data.dictionary

import chimahon.dictionary.DictionaryProfile
import tachiyomi.data.Dictionary_profiles

object DictionaryMapper {
    fun mapProfile(
        id: String,
        name: String,
        languageCode: String?,
        ankiEnabled: Boolean,
        ankiDeck: String,
        ankiModel: String,
        ankiFieldMap: Map<String, String>,
        ankiTags: String,
        ankiDupCheck: Boolean,
        ankiDupScope: String,
        ankiDupAction: String,
        ankiCropMode: String,
        dictionaryOrder: List<String>,
        enabledDictionaries: Set<String>
    ): DictionaryProfile {
        return DictionaryProfile(
            id = id,
            name = name,
            languageCode = languageCode ?: "",
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
            enabledDictionaries = enabledDictionaries
        )
    }
}
