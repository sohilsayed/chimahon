package eu.kanade.tachiyomi.ui.player.setting

import eu.kanade.tachiyomi.ui.player.VideoAspect
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class PlayerPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun defaultPlayerOrientation() = preferenceStore.getInt(
        "player_default_orientation",
        PlayerOrientation.FREE.flag,
    )

    fun keepScreenOn() = preferenceStore.getBoolean("player_keep_screen_on", true)

    fun progressSaveIntervalSec() = preferenceStore.getInt("player_progress_save_interval", 5)

    // Controls

    fun showLoadingCircle() = preferenceStore.getBoolean("pref_show_loading", true)
    fun rememberPlayerBrightness() = preferenceStore.getBoolean("pref_remember_brightness", false)
    fun playerBrightnessValue() = preferenceStore.getFloat("player_brightness_value", -1.0f)
    fun rememberPlayerVolume() = preferenceStore.getBoolean("pref_remember_volume", false)
    fun playerVolumeValue() = preferenceStore.getFloat("player_volume_value", -1.0f)
    fun playerTimeToDisappear() = preferenceStore.getInt("pref_player_time_to_disappear", 4000)

    // Display

    fun playerFullscreen() = preferenceStore.getBoolean("player_fullscreen", true)
    fun reduceMotion() = preferenceStore.getBoolean("pref_reduce_motion", false)

    // PiP

    fun enablePip() = preferenceStore.getBoolean("pref_enable_pip", true)
    fun pipOnExit() = preferenceStore.getBoolean("pref_pip_on_exit", false)

    // External player

    fun alwaysUseExternalPlayer() = preferenceStore.getBoolean(
        "pref_always_use_external_player",
        false,
    )
    fun externalPlayerPreference() = preferenceStore.getString("external_player_preference", "")

    // Non-preferences

    fun playerSpeed() = preferenceStore.getFloat("pref_player_speed", 1f)
    fun speedPresets() = preferenceStore.getStringSet(
        "default_speed_presets",
        setOf("0.25", "0.5", "0.75", "1.0", "1.25", "1.5", "1.75", "2.0"),
    )
    fun aspectState() = preferenceStore.getEnum("pref_player_aspect_state", VideoAspect.Fit)
    fun autoplayEnabled() = preferenceStore.getBoolean("pref_auto_play_enabled", false)

    // AniSkip
    fun aniSkipEnabled() = preferenceStore.getBoolean("pref_aniskip_enabled", false)
    fun enableSkipIntro() = preferenceStore.getBoolean("pref_enable_skip_intro", true)
    fun autoSkipIntro() = preferenceStore.getBoolean("pref_auto_skip_intro", false)
    fun enableNetflixStyleIntroSkip() = preferenceStore.getBoolean("pref_netflix_style_intro_skip", false)
    fun waitingTimeIntroSkip() = preferenceStore.getInt("pref_waiting_time_intro_skip", 5)

    // Chapter display
    fun showCurrentChapter() = preferenceStore.getBoolean("pref_show_current_chapter", true)

    // Progress
    fun progressPreference() = preferenceStore.getFloat("pref_progress_preference", 0.85f)
    fun preserveWatchingPosition() = preferenceStore.getBoolean("pref_preserve_watching_position", false)

    fun doubleTapSeekLength() = preferenceStore.getInt("pref_double_tap_seek_length", 10)

    fun customSubtitlesForEpisode(episodeId: Long) = preferenceStore.getString("custom_subs_$episodeId", "")
}
