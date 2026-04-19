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

class NovelReaderSettings(private val context: Context) {

    companion object {
        fun default(): NovelReaderSettings {
            throw IllegalStateException("NovelReaderSettings requires a Context. Pass context from Activity.")
        }
    }
    
    private val dataStore = context.novelReaderDataStore
    
    val theme: Flow<Theme> = dataStore.data.map { prefs ->
        Theme.valueOf(prefs[PreferencesKeys.THEME] ?: Theme.SYSTEM.name)
    }
    
    val systemLightSepia: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SYSTEM_LIGHT_SEPIA] ?: false
    }
    
    val uiTheme: Flow<Theme> = dataStore.data.map { prefs ->
        Theme.valueOf(prefs[PreferencesKeys.UI_THEME] ?: Theme.SYSTEM.name)
    }
    
    val customBackgroundColor: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CUSTOM_BACKGROUND_COLOR] ?: 0xFFF2E2C9.toInt()
    }
    
    val customTextColor: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CUSTOM_TEXT_COLOR] ?: Color.BLACK
    }
    
    val customInfoColor: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CUSTOM_INFO_COLOR] ?: Color.DKGRAY
    }
    
    val verticalWriting: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VERTICAL_WRITING] ?: true
    }
    
    val selectedFont: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SELECTED_FONT] ?: "System"
    }
    
    val fontSize: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.FONT_SIZE] ?: 18
    }
    
    val readerHideFurigana: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.READER_HIDE_FURIGANA] ?: false
    }
    
    val continuousMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CONTINUOUS_MODE] ?: false
    }
    
    val chapterSwipeDistance: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CHAPTER_SWIPE_DISTANCE] ?: 96
    }
    
    val chapterTapZones: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CHAPTER_TAP_ZONES] ?: 20
    }
    
    val horizontalPadding: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.HORIZONTAL_PADDING] ?: 10
    }
    
    val verticalPadding: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VERTICAL_PADDING] ?: 10
    }
    
    val avoidPageBreak: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.AVOID_PAGE_BREAK] ?: true
    }
    
    val justifyText: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.JUSTIFY_TEXT] ?: false
    }
    
    val layoutAdvanced: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LAYOUT_ADVANCED] ?: false
    }
    
    val lineHeight: Flow<Double> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LINE_HEIGHT] ?: 1.6
    }
    
    val characterSpacing: Flow<Double> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.CHARACTER_SPACING] ?: 0.0
    }
    
    val readerShowTitle: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.READER_SHOW_TITLE] ?: true
    }
    
    val readerShowCharacters: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.READER_SHOW_CHARACTERS] ?: true
    }
    
    val readerShowPercentage: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.READER_SHOW_PERCENTAGE] ?: true
    }
    
    val readerShowProgressTop: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.READER_SHOW_PROGRESS_TOP] ?: true
    }
    
    val enableStatistics: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.ENABLE_STATISTICS] ?: true
    }
    
    val statisticsAutostartMode: Flow<StatisticsAutostartMode> = dataStore.data.map { prefs ->
        StatisticsAutostartMode.valueOf(prefs[PreferencesKeys.STATISTICS_AUTOSTART_MODE] ?: StatisticsAutostartMode.ON.name)
    }
    
    val readerShowReadingSpeed: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.READER_SHOW_READING_SPEED] ?: true
    }
    
    val readerShowReadingTime: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.READER_SHOW_READING_TIME] ?: true
    }
    
    val popupWidth: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.POPUP_WIDTH] ?: 300
    }
    
    val popupHeight: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.POPUP_HEIGHT] ?: 200
    }
    
    val popupFullWidth: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.POPUP_FULL_WIDTH] ?: false
    }
    
    val popupSwipeToDismiss: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.POPUP_SWIPE_TO_DISMISS] ?: true
    }
    
    val popupSwipeThreshold: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.POPUP_SWIPE_THRESHOLD] ?: 50
    }
    
    val maxResults: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.MAX_RESULTS] ?: 10
    }
    
    val scanLength: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.SCAN_LENGTH] ?: 50
    }
    
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferencesKeys.KEEP_SCREEN_ON] ?: false
    }
    
    suspend fun setTheme(value: Theme) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME] = value.name
        }
    }
    
    suspend fun setSystemLightSepia(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SYSTEM_LIGHT_SEPIA] = value
        }
    }
    
    suspend fun setUiTheme(value: Theme) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.UI_THEME] = value.name
        }
    }
    
    suspend fun setCustomBackgroundColor(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CUSTOM_BACKGROUND_COLOR] = value
        }
    }
    
    suspend fun setCustomTextColor(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CUSTOM_TEXT_COLOR] = value
        }
    }
    
    suspend fun setCustomInfoColor(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CUSTOM_INFO_COLOR] = value
        }
    }
    
    suspend fun setVerticalWriting(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.VERTICAL_WRITING] = value
        }
    }
    
    suspend fun setSelectedFont(value: String) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SELECTED_FONT] = value
        }
    }
    
    suspend fun setFontSize(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.FONT_SIZE] = value
        }
    }
    
    suspend fun setReaderHideFurigana(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.READER_HIDE_FURIGANA] = value
        }
    }
    
    suspend fun setContinuousMode(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CONTINUOUS_MODE] = value
        }
    }
    
    suspend fun setChapterSwipeDistance(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CHAPTER_SWIPE_DISTANCE] = value
        }
    }
    
    suspend fun setChapterTapZones(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CHAPTER_TAP_ZONES] = value
        }
    }
    
    suspend fun setHorizontalPadding(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.HORIZONTAL_PADDING] = value
        }
    }
    
    suspend fun setVerticalPadding(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.VERTICAL_PADDING] = value
        }
    }
    
    suspend fun setAvoidPageBreak(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.AVOID_PAGE_BREAK] = value
        }
    }
    
    suspend fun setJustifyText(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.JUSTIFY_TEXT] = value
        }
    }
    
    suspend fun setLayoutAdvanced(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAYOUT_ADVANCED] = value
        }
    }
    
    suspend fun setLineHeight(value: Double) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.LINE_HEIGHT] = value
        }
    }
    
    suspend fun setCharacterSpacing(value: Double) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.CHARACTER_SPACING] = value
        }
    }
    
    suspend fun setReaderShowTitle(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.READER_SHOW_TITLE] = value
        }
    }
    
    suspend fun setReaderShowCharacters(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.READER_SHOW_CHARACTERS] = value
        }
    }
    
    suspend fun setReaderShowPercentage(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.READER_SHOW_PERCENTAGE] = value
        }
    }
    
    suspend fun setReaderShowProgressTop(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.READER_SHOW_PROGRESS_TOP] = value
        }
    }
    
    suspend fun setEnableStatistics(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.ENABLE_STATISTICS] = value
        }
    }
    
    suspend fun setStatisticsAutostartMode(value: StatisticsAutostartMode) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.STATISTICS_AUTOSTART_MODE] = value.name
        }
    }
    
    suspend fun setReaderShowReadingSpeed(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.READER_SHOW_READING_SPEED] = value
        }
    }
    
    suspend fun setReaderShowReadingTime(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.READER_SHOW_READING_TIME] = value
        }
    }
    
    suspend fun setPopupWidth(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.POPUP_WIDTH] = value
        }
    }
    
    suspend fun setPopupHeight(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.POPUP_HEIGHT] = value
        }
    }
    
    suspend fun setPopupFullWidth(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.POPUP_FULL_WIDTH] = value
        }
    }
    
    suspend fun setPopupSwipeToDismiss(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.POPUP_SWIPE_TO_DISMISS] = value
        }
    }
    
    suspend fun setPopupSwipeThreshold(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.POPUP_SWIPE_THRESHOLD] = value
        }
    }
    
    suspend fun setMaxResults(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.MAX_RESULTS] = value
        }
    }
    
    suspend fun setScanLength(value: Int) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.SCAN_LENGTH] = value
        }
    }
    
    suspend fun setKeepScreenOn(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.KEEP_SCREEN_ON] = value
        }
    }
    
    private object PreferencesKeys {
        val THEME = stringPreferencesKey("theme")
        val SYSTEM_LIGHT_SEPIA = booleanPreferencesKey("system_light_sepia")
        val UI_THEME = stringPreferencesKey("ui_theme")
        val CUSTOM_BACKGROUND_COLOR = intPreferencesKey("custom_background_color")
        val CUSTOM_TEXT_COLOR = intPreferencesKey("custom_text_color")
        val CUSTOM_INFO_COLOR = intPreferencesKey("custom_info_color")
        val VERTICAL_WRITING = booleanPreferencesKey("vertical_writing")
        val SELECTED_FONT = stringPreferencesKey("selected_font")
        val FONT_SIZE = intPreferencesKey("font_size")
        val READER_HIDE_FURIGANA = booleanPreferencesKey("reader_hide_furigana")
        val CONTINUOUS_MODE = booleanPreferencesKey("continuous_mode")
        val CHAPTER_SWIPE_DISTANCE = intPreferencesKey("chapter_swipe_distance")
        val CHAPTER_TAP_ZONES = intPreferencesKey("chapter_tap_zones")
        val HORIZONTAL_PADDING = intPreferencesKey("horizontal_padding")
        val VERTICAL_PADDING = intPreferencesKey("vertical_padding")
        val AVOID_PAGE_BREAK = booleanPreferencesKey("avoid_page_break")
        val JUSTIFY_TEXT = booleanPreferencesKey("justify_text")
        val LAYOUT_ADVANCED = booleanPreferencesKey("layout_advanced")
        val LINE_HEIGHT = doublePreferencesKey("line_height")
        val CHARACTER_SPACING = doublePreferencesKey("character_spacing")
        val READER_SHOW_TITLE = booleanPreferencesKey("reader_show_title")
        val READER_SHOW_CHARACTERS = booleanPreferencesKey("reader_show_characters")
        val READER_SHOW_PERCENTAGE = booleanPreferencesKey("reader_show_percentage")
        val READER_SHOW_PROGRESS_TOP = booleanPreferencesKey("reader_show_progress_top")
        val ENABLE_STATISTICS = booleanPreferencesKey("enable_statistics")
        val STATISTICS_AUTOSTART_MODE = stringPreferencesKey("statistics_autostart_mode")
        val READER_SHOW_READING_SPEED = booleanPreferencesKey("reader_show_reading_speed")
        val READER_SHOW_READING_TIME = booleanPreferencesKey("reader_show_reading_time")
        val POPUP_WIDTH = intPreferencesKey("popup_width")
        val POPUP_HEIGHT = intPreferencesKey("popup_height")
        val POPUP_FULL_WIDTH = booleanPreferencesKey("popup_full_width")
        val POPUP_SWIPE_TO_DISMISS = booleanPreferencesKey("popup_swipe_to_dismiss")
        val POPUP_SWIPE_THRESHOLD = intPreferencesKey("popup_swipe_threshold")
        val MAX_RESULTS = intPreferencesKey("max_results")
        val SCAN_LENGTH = intPreferencesKey("scan_length")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }
}

val Context.novelReaderSettings: NovelReaderSettings
    get() = NovelReaderSettings(this)
