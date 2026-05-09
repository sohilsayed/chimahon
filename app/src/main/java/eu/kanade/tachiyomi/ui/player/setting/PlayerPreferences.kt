package eu.kanade.tachiyomi.ui.player.setting

import tachiyomi.core.common.preference.PreferenceStore

class PlayerPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun defaultPlayerOrientation() = preferenceStore.getInt(
        "player_default_orientation",
        PlayerOrientation.FREE.flag,
    )

    fun doubleTapSeekLength() = preferenceStore.getInt("player_double_tap_seek_length", 10)

    fun keepScreenOn() = preferenceStore.getBoolean("player_keep_screen_on", true)

    fun progressSaveIntervalSec() = preferenceStore.getInt("player_progress_save_interval", 5)

    fun preferredSubLanguage() = preferenceStore.getString("player_preferred_sub_language", "")
}
