package eu.kanade.tachiyomi.ui.youtube

import android.content.Context
import android.content.SharedPreferences

class YouTubePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("youtube_prefs", Context.MODE_PRIVATE)

    var preferredQuality: String
        get() = prefs.getString(KEY_QUALITY, DEFAULT_QUALITY)
            ?.takeIf { it in QUALITIES }
            ?: DEFAULT_QUALITY
        set(value) = prefs.edit().putString(KEY_QUALITY, value).apply()

    companion object {
        const val KEY_QUALITY = "preferred_quality"
        const val QUALITY_2160P = "2160p"
        const val QUALITY_1440P = "1440p"
        const val QUALITY_1080P = "1080p"
        const val QUALITY_720P = "720p"
        const val QUALITY_480P = "480p"
        const val QUALITY_360P = "360p"
        const val DEFAULT_QUALITY = QUALITY_1080P

        val QUALITIES = listOf(
            QUALITY_2160P,
            QUALITY_1440P,
            QUALITY_1080P,
            QUALITY_720P,
            QUALITY_480P,
            QUALITY_360P,
        )
    }
}
