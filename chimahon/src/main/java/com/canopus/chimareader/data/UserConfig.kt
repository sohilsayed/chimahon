package com.canopus.chimareader.data

import android.content.Context
import android.content.SharedPreferences

class UserConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ChimaReaderPrefs", Context.MODE_PRIVATE)

    var continuousMode: Boolean
        get() = prefs.getBoolean("continuousMode", false)
        set(value) = prefs.edit().putBoolean("continuousMode", value).apply()

    var verticalWriting: Boolean
        get() = prefs.getBoolean("verticalWriting", true)
        set(value) = prefs.edit().putBoolean("verticalWriting", value).apply()

    var fontSize: Int
        get() = prefs.getInt("fontSize", 22)
        set(value) = prefs.edit().putInt("fontSize", value).apply()

    var readerHideFurigana: Boolean
        get() = prefs.getBoolean("readerHideFurigana", false)
        set(value) = prefs.edit().putBoolean("readerHideFurigana", value).apply()

    var enableStatistics: Boolean
        get() = prefs.getBoolean("enableStatistics", true)
        set(value) = prefs.edit().putBoolean("enableStatistics", value).apply()

    var enableSasayaki: Boolean
        get() = prefs.getBoolean("enableSasayaki", true)
        set(value) = prefs.edit().putBoolean("enableSasayaki", value).apply()

    var selectedFont: String
        get() = prefs.getString("selectedFont", "System Serif") ?: "System Serif"
        set(value) = prefs.edit().putString("selectedFont", value).apply()
}
