package com.canopus.chimareader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.graphics.Color

val Context.novelReaderDataStore: DataStore<Preferences> by preferencesDataStore(name = "novel_reader_settings")

enum class Theme {
    SYSTEM,
    LIGHT,
    DARK,
    SEPIA,
    CUSTOM
}

enum class StatisticsAutostartMode {
    OFF,
    ON,
    PAGETURN
}

class NovelReaderSettings(private val context: Context, private val language: String? = null) {

    companion object {
        fun default(): NovelReaderSettings {
            throw IllegalStateException("NovelReaderSettings requires a Context. Pass context from Activity.")
        }
    }

    private fun stringKey(name: String, languageSpecific: Boolean = false): androidx.datastore.preferences.core.Preferences.Key<String> {
        return stringPreferencesKey(if (languageSpecific && !language.isNullOrEmpty()) "${language}_$name" else name)
    }

    private fun booleanKey(name: String, languageSpecific: Boolean = false): androidx.datastore.preferences.core.Preferences.Key<Boolean> {
        return booleanPreferencesKey(if (languageSpecific && !language.isNullOrEmpty()) "${language}_$name" else name)
    }

    private fun intKey(name: String, languageSpecific: Boolean = false): androidx.datastore.preferences.core.Preferences.Key<Int> {
        return intPreferencesKey(if (languageSpecific && !language.isNullOrEmpty()) "${language}_$name" else name)
    }

    private fun doubleKey(name: String, languageSpecific: Boolean = false): androidx.datastore.preferences.core.Preferences.Key<Double> {
        return doublePreferencesKey(if (languageSpecific && !language.isNullOrEmpty()) "${language}_$name" else name)
    }

    private val dataStore = context.novelReaderDataStore
    
    private fun androidx.datastore.preferences.core.Preferences.getSafeDouble(key: androidx.datastore.preferences.core.Preferences.Key<Double>, default: Double): Double {
        return try {
            this[key] ?: default
        } catch (e: ClassCastException) {
            val intKey = androidx.datastore.preferences.core.intPreferencesKey(key.name)
            this[intKey]?.toDouble() ?: default
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.getSafeInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, default: Int): Int {
        return try {
            this[key] ?: default
        } catch (e: ClassCastException) {
            val doubleKey = androidx.datastore.preferences.core.doublePreferencesKey(key.name)
            this[doubleKey]?.toInt() ?: default
        }
    }

    val theme: Flow<Theme> = dataStore.data.map { prefs ->
        Theme.valueOf(prefs[keys.THEME] ?: Theme.SYSTEM.name)
    }

    val systemLightSepia: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.SYSTEM_LIGHT_SEPIA] ?: false
    }

    val uiTheme: Flow<Theme> = dataStore.data.map { prefs ->
        Theme.valueOf(prefs[keys.UI_THEME] ?: Theme.SYSTEM.name)
    }

    val customBackgroundColor: Flow<Int> = dataStore.data.map { prefs ->
        prefs[keys.CUSTOM_BACKGROUND_COLOR] ?: 0xFFF2E2C9.toInt()
    }

    val customTextColor: Flow<Int> = dataStore.data.map { prefs ->
        prefs[keys.CUSTOM_TEXT_COLOR] ?: Color.BLACK
    }

    val customInfoColor: Flow<Int> = dataStore.data.map { prefs ->
        prefs[keys.CUSTOM_INFO_COLOR] ?: Color.DKGRAY
    }

    val verticalWriting: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.VERTICAL_WRITING] ?: true
    }

    val selectedFont: Flow<String> = dataStore.data.map { prefs ->
        prefs[keys.SELECTED_FONT] ?: "System"
    }

    val fontSize: Flow<Double> = dataStore.data.map { prefs ->
        prefs.getSafeDouble(keys.FONT_SIZE, 18.0)
    }

    val readerHideFurigana: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.READER_HIDE_FURIGANA] ?: false
    }

    val continuousMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.CONTINUOUS_MODE] ?: false
    }

    val chapterSwipeDistance: Flow<Int> = dataStore.data.map { prefs ->
        prefs.getSafeInt(keys.CHAPTER_SWIPE_DISTANCE, 96)
    }

    val chapterTapZones: Flow<Int> = dataStore.data.map { prefs ->
        prefs.getSafeInt(keys.CHAPTER_TAP_ZONES, 20)
    }

    val horizontalPadding: Flow<Double> = dataStore.data.map { prefs ->
        prefs.getSafeDouble(keys.HORIZONTAL_PADDING, 10.0)
    }

    val verticalPadding: Flow<Double> = dataStore.data.map { prefs ->
        prefs.getSafeDouble(keys.VERTICAL_PADDING, 10.0)
    }

    val avoidPageBreak: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.AVOID_PAGE_BREAK] ?: true
    }

    val justifyText: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.JUSTIFY_TEXT] ?: false
    }

    val layoutAdvanced: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.LAYOUT_ADVANCED] ?: false
    }

    val lineHeight: Flow<Double> = dataStore.data.map { prefs ->
        prefs.getSafeDouble(keys.LINE_HEIGHT, 1.6)
    }

    val characterSpacing: Flow<Double> = dataStore.data.map { prefs ->
        prefs.getSafeDouble(keys.CHARACTER_SPACING, 0.0)
    }

    val paragraphSpacing: Flow<Double> = dataStore.data.map { prefs ->
        prefs.getSafeDouble(keys.PARAGRAPH_SPACING, 0.0)
    }

    val readerShowTitle: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.READER_SHOW_TITLE] ?: true
    }

    val readerShowCharacters: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.READER_SHOW_CHARACTERS] ?: true
    }

    val readerShowPercentage: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.READER_SHOW_PERCENTAGE] ?: true
    }

    val readerShowProgressTop: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.READER_SHOW_PROGRESS_TOP] ?: true
    }

    val enableStatistics: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.ENABLE_STATISTICS] ?: true
    }

    val statisticsAutostartMode: Flow<StatisticsAutostartMode> = dataStore.data.map { prefs ->
        StatisticsAutostartMode.valueOf(prefs[keys.STATISTICS_AUTOSTART_MODE] ?: StatisticsAutostartMode.ON.name)
    }

    val readerShowReadingSpeed: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.READER_SHOW_READING_SPEED] ?: true
    }

    val readerShowReadingTime: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.READER_SHOW_READING_TIME] ?: true
    }

    val popupWidth: Flow<Int> = dataStore.data.map { prefs ->
        prefs.getSafeInt(keys.POPUP_WIDTH, 300)
    }

    val popupHeight: Flow<Int> = dataStore.data.map { prefs ->
        prefs.getSafeInt(keys.POPUP_HEIGHT, 200)
    }

    val popupFullWidth: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.POPUP_FULL_WIDTH] ?: false
    }

    val popupSwipeToDismiss: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.POPUP_SWIPE_TO_DISMISS] ?: true
    }

    val popupSwipeThreshold: Flow<Int> = dataStore.data.map { prefs ->
        prefs.getSafeInt(keys.POPUP_SWIPE_THRESHOLD, 50)
    }

    val maxResults: Flow<Int> = dataStore.data.map { prefs ->
        prefs.getSafeInt(keys.MAX_RESULTS, 10)
    }

    val scanLength: Flow<Int> = dataStore.data.map { prefs ->
        prefs.getSafeInt(keys.SCAN_LENGTH, 50)
    }

    val keepScreenOn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[keys.KEEP_SCREEN_ON] ?: false
    }

    suspend fun setTheme(value: Theme) {
        dataStore.edit { prefs ->
            prefs[keys.THEME] = value.name
        }
    }

    suspend fun setSystemLightSepia(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.SYSTEM_LIGHT_SEPIA] = value
        }
    }

    suspend fun setUiTheme(value: Theme) {
        dataStore.edit { prefs ->
            prefs[keys.UI_THEME] = value.name
        }
    }

    suspend fun setCustomBackgroundColor(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.CUSTOM_BACKGROUND_COLOR] = value
        }
    }

    suspend fun setCustomTextColor(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.CUSTOM_TEXT_COLOR] = value
        }
    }

    suspend fun setCustomInfoColor(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.CUSTOM_INFO_COLOR] = value
        }
    }

    suspend fun setVerticalWriting(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.VERTICAL_WRITING] = value
        }
    }

    suspend fun setSelectedFont(value: String) {
        dataStore.edit { prefs ->
            prefs[keys.SELECTED_FONT] = value
        }
    }

    suspend fun setFontSize(value: Double) {
        dataStore.edit { prefs ->
            prefs[keys.FONT_SIZE] = value
        }
    }

    suspend fun setReaderHideFurigana(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.READER_HIDE_FURIGANA] = value
        }
    }

    suspend fun setContinuousMode(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.CONTINUOUS_MODE] = value
        }
    }

    suspend fun setChapterSwipeDistance(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.CHAPTER_SWIPE_DISTANCE] = value
        }
    }

    suspend fun setChapterTapZones(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.CHAPTER_TAP_ZONES] = value
        }
    }

    suspend fun setHorizontalPadding(value: Double) {
        dataStore.edit { prefs ->
            prefs[keys.HORIZONTAL_PADDING] = value
        }
    }

    suspend fun setVerticalPadding(value: Double) {
        dataStore.edit { prefs ->
            prefs[keys.VERTICAL_PADDING] = value
        }
    }

    suspend fun setAvoidPageBreak(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.AVOID_PAGE_BREAK] = value
        }
    }

    suspend fun setJustifyText(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.JUSTIFY_TEXT] = value
        }
    }

    suspend fun setLayoutAdvanced(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.LAYOUT_ADVANCED] = value
        }
    }

    suspend fun setLineHeight(value: Double) {
        dataStore.edit { prefs ->
            prefs[keys.LINE_HEIGHT] = value
        }
    }

    suspend fun setCharacterSpacing(value: Double) {
        dataStore.edit { prefs ->
            prefs[keys.CHARACTER_SPACING] = value
        }
    }

    suspend fun setParagraphSpacing(value: Double) {
        dataStore.edit { prefs ->
            prefs[keys.PARAGRAPH_SPACING] = value
        }
    }

    suspend fun setReaderShowTitle(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.READER_SHOW_TITLE] = value
        }
    }

    suspend fun setReaderShowCharacters(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.READER_SHOW_CHARACTERS] = value
        }
    }

    suspend fun setReaderShowPercentage(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.READER_SHOW_PERCENTAGE] = value
        }
    }

    suspend fun setReaderShowProgressTop(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.READER_SHOW_PROGRESS_TOP] = value
        }
    }

    suspend fun setEnableStatistics(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.ENABLE_STATISTICS] = value
        }
    }

    suspend fun setStatisticsAutostartMode(value: StatisticsAutostartMode) {
        dataStore.edit { prefs ->
            prefs[keys.STATISTICS_AUTOSTART_MODE] = value.name
        }
    }

    suspend fun setReaderShowReadingSpeed(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.READER_SHOW_READING_SPEED] = value
        }
    }

    suspend fun setReaderShowReadingTime(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.READER_SHOW_READING_TIME] = value
        }
    }

    suspend fun setPopupWidth(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.POPUP_WIDTH] = value
        }
    }

    suspend fun setPopupHeight(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.POPUP_HEIGHT] = value
        }
    }

    suspend fun setPopupFullWidth(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.POPUP_FULL_WIDTH] = value
        }
    }

    suspend fun setPopupSwipeToDismiss(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.POPUP_SWIPE_TO_DISMISS] = value
        }
    }

    suspend fun setPopupSwipeThreshold(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.POPUP_SWIPE_THRESHOLD] = value
        }
    }

    suspend fun setMaxResults(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.MAX_RESULTS] = value
        }
    }

    suspend fun setScanLength(value: Int) {
        dataStore.edit { prefs ->
            prefs[keys.SCAN_LENGTH] = value
        }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[keys.KEEP_SCREEN_ON] = value
        }
    }

    private inner class PreferencesKeys {
        val THEME = stringKey("theme")
        val SYSTEM_LIGHT_SEPIA = booleanKey("system_light_sepia")
        val UI_THEME = stringKey("ui_theme")
        val CUSTOM_BACKGROUND_COLOR = intKey("custom_background_color")
        val CUSTOM_TEXT_COLOR = intKey("custom_text_color")
        val CUSTOM_INFO_COLOR = intKey("custom_info_color")
        val VERTICAL_WRITING = booleanKey("vertical_writing", languageSpecific = true)
        val SELECTED_FONT = stringKey("selected_font", languageSpecific = true)
        val FONT_SIZE = doubleKey("font_size", languageSpecific = true)
        val READER_HIDE_FURIGANA = booleanKey("reader_hide_furigana", languageSpecific = true)
        val CONTINUOUS_MODE = booleanKey("continuous_mode", languageSpecific = true)
        val CHAPTER_SWIPE_DISTANCE = intKey("chapter_swipe_distance")
        val CHAPTER_TAP_ZONES = intKey("chapter_tap_zones")
        val HORIZONTAL_PADDING = doubleKey("horizontal_padding", languageSpecific = true)
        val VERTICAL_PADDING = doubleKey("vertical_padding", languageSpecific = true)
        val AVOID_PAGE_BREAK = booleanKey("avoid_page_break", languageSpecific = true)
        val JUSTIFY_TEXT = booleanKey("justify_text", languageSpecific = true)
        val LAYOUT_ADVANCED = booleanKey("layout_advanced", languageSpecific = true)
        val LINE_HEIGHT = doubleKey("line_height", languageSpecific = true)
        val CHARACTER_SPACING = doubleKey("character_spacing", languageSpecific = true)
        val PARAGRAPH_SPACING = doubleKey("paragraph_spacing", languageSpecific = true)
        val READER_SHOW_TITLE = booleanKey("reader_show_title")
        val READER_SHOW_CHARACTERS = booleanKey("reader_show_characters")
        val READER_SHOW_PERCENTAGE = booleanKey("reader_show_percentage")
        val READER_SHOW_PROGRESS_TOP = booleanKey("reader_show_progress_top")
        val ENABLE_STATISTICS = booleanKey("enable_statistics")
        val STATISTICS_AUTOSTART_MODE = stringKey("statistics_autostart_mode")
        val READER_SHOW_READING_SPEED = booleanKey("reader_show_reading_speed")
        val READER_SHOW_READING_TIME = booleanKey("reader_show_reading_time")
        val POPUP_WIDTH = intKey("popup_width")
        val POPUP_HEIGHT = intKey("popup_height")
        val POPUP_FULL_WIDTH = booleanKey("popup_full_width")
        val POPUP_SWIPE_TO_DISMISS = booleanKey("popup_swipe_to_dismiss")
        val POPUP_SWIPE_THRESHOLD = intKey("popup_swipe_threshold")
        val MAX_RESULTS = intKey("max_results")
        val SCAN_LENGTH = intKey("scan_length")
        val KEEP_SCREEN_ON = booleanKey("keep_screen_on")
    }

    private val keys = PreferencesKeys()
}

val Context.novelReaderSettings: NovelReaderSettings
    get() = NovelReaderSettings(this)
