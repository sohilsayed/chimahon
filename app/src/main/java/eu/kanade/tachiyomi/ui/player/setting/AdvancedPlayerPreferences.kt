package eu.kanade.tachiyomi.ui.player.setting

import tachiyomi.core.common.preference.PreferenceStore

class AdvancedPlayerPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun mpvConf() = preferenceStore.getString("pref_mpv_conf", "")
    fun mpvInput() = preferenceStore.getString("pref_mpv_input", "")
    fun playerStatisticsPage() = preferenceStore.getInt("pref_player_statistics_page", 0)
}
