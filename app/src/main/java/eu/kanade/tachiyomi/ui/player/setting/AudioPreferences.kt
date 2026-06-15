package eu.kanade.tachiyomi.ui.player.setting

import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

class AudioPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun preferredAudioLanguages() = preferenceStore.getString("pref_audio_lang", "")
    fun enablePitchCorrection() = preferenceStore.getBoolean("pref_audio_pitch_correction", true)
    fun audioChannels() = preferenceStore.getEnum("pref_audio_config", AudioChannels.AutoSafe)
    fun volumeBoostCap() = preferenceStore.getInt("pref_audio_volume_boost_cap", 30)

    fun audioDelay() = preferenceStore.getInt("pref_audio_delay", 0)
}

enum class AudioChannels(val property: String, val value: String) {
    Auto("audio-channels", "auto-safe"),
    AutoSafe("audio-channels", "auto"),
    Mono("audio-channels", "mono"),
    Stereo("audio-channels", "stereo"),
    ReverseStereo("af", "pan=[stereo|c0=c1|c1=c0]"),
}
