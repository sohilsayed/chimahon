package com.canopus.chimareader.ttusync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SyncSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadFromPrefs())

    val settings: Flow<SyncSettings> = _settings.asStateFlow()

    fun currentSettings(): SyncSettings = _settings.value

    fun update(transform: (SyncSettings) -> SyncSettings) {
        val updated = transform(_settings.value)
        saveToPrefs(updated)
        _settings.value = updated
    }

    private fun loadFromPrefs(): SyncSettings = SyncSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, false),
        mode = SyncMode.fromRawValue(prefs.getString(KEY_MODE, null)),
        autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false),
        statisticsSyncEnabled = prefs.getBoolean(KEY_STATS_SYNC_ENABLED, false),
        statisticsSyncMode = StatisticsSyncMode.fromRawValue(prefs.getString(KEY_STATS_SYNC_MODE, null)),
        audioBookSyncEnabled = prefs.getBoolean(KEY_AUDIO_SYNC_ENABLED, false),
    )

    private fun saveToPrefs(settings: SyncSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_MODE, settings.mode.rawValue)
            .putBoolean(KEY_AUTO_SYNC_ENABLED, settings.autoSyncEnabled)
            .putBoolean(KEY_STATS_SYNC_ENABLED, settings.statisticsSyncEnabled)
            .putString(KEY_STATS_SYNC_MODE, settings.statisticsSyncMode.rawValue)
            .putBoolean(KEY_AUDIO_SYNC_ENABLED, settings.audioBookSyncEnabled)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "ttu-sync-settings"
        private const val KEY_ENABLED = "syncEnabled"
        private const val KEY_MODE = "syncMode"
        private const val KEY_AUTO_SYNC_ENABLED = "autoSyncEnabled"
        private const val KEY_STATS_SYNC_ENABLED = "statsSyncEnabled"
        private const val KEY_STATS_SYNC_MODE = "statsSyncMode"
        private const val KEY_AUDIO_SYNC_ENABLED = "audioSyncEnabled"
    }
}
