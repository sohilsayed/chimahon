package eu.kanade.tachiyomi.ui.player.setting

import android.content.pm.ActivityInfo

enum class PlayerOrientation(val flag: Int) {
    FREE(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    LOCKED_PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    LOCKED_LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    REVERSE_PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    ;

    companion object {
        fun fromPreference(preference: Int): PlayerOrientation =
            entries.find { it.flag == preference } ?: FREE
    }
}
