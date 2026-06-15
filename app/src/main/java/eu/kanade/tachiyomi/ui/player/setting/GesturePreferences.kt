package eu.kanade.tachiyomi.ui.player.setting

import eu.kanade.tachiyomi.ui.player.SingleActionGesture
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class GesturePreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun gestureVolumeBrightness() = preferenceStore.getBoolean(
        "pref_gesture_volume_brightness",
        true,
    )
    fun swapVolumeBrightness() = preferenceStore.getBoolean("pref_swap_volume_and_brightness", false)

    fun gestureHorizontalSeek() = preferenceStore.getBoolean("pref_gesture_horizontal_seek", true)
    fun skipLengthPreference() = preferenceStore.getInt("pref_skip_length_preference", 10)
    fun playerSmoothSeek() = preferenceStore.getBoolean("pref_player_smooth_seek", false)

    fun leftDoubleTapGesture() = preferenceStore.getEnum("pref_left_double_tap", SingleActionGesture.Seek)
    fun centerDoubleTapGesture() = preferenceStore.getEnum("pref_center_double_tap", SingleActionGesture.PlayPause)
    fun rightDoubleTapGesture() = preferenceStore.getEnum("pref_right_double_tap", SingleActionGesture.Seek)
}
