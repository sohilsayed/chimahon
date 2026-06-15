package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.SingleActionGesture
import eu.kanade.tachiyomi.ui.player.setting.AdvancedPlayerPreferences
import eu.kanade.tachiyomi.ui.player.setting.AudioChannels
import eu.kanade.tachiyomi.ui.player.setting.AudioPreferences
import eu.kanade.tachiyomi.ui.player.setting.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.setting.GesturePreferences
import eu.kanade.tachiyomi.ui.player.setting.PlayerOrientation
import eu.kanade.tachiyomi.ui.player.setting.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.setting.SubtitlePreferences
import eu.kanade.tachiyomi.ui.player.setting.SubtitlesBorderStyle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Suppress("unused")
object SettingsPlayerScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsPlayerScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_player

    @Composable
    override fun getPreferences(): List<Preference> {
        val playerPreferences = remember { Injekt.get<PlayerPreferences>() }
        val gesturePreferences = remember { Injekt.get<GesturePreferences>() }
        val subtitlePreferences = remember { Injekt.get<SubtitlePreferences>() }
        val audioPreferences = remember { Injekt.get<AudioPreferences>() }
        val decoderPreferences = remember { Injekt.get<DecoderPreferences>() }
        val advancedPlayerPreferences = remember { Injekt.get<AdvancedPlayerPreferences>() }

        return listOf(
            getGeneralGroup(playerPreferences),
            getPipGroup(playerPreferences),
            getExternalPlayerGroup(playerPreferences),
            getAniSkipGroup(playerPreferences),
            getGesturesGroup(gesturePreferences),
            getSubtitlesGroup(subtitlePreferences),
            getAudioGroup(audioPreferences),
            getAdvancedGroup(decoderPreferences, advancedPlayerPreferences),
        )
    }

    @Composable
    private fun getGeneralGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val progressInterval by playerPreferences.progressSaveIntervalSec().collectAsState()
        val controlsDelay by playerPreferences.playerTimeToDisappear().collectAsState()
        val doubleTapSeek by playerPreferences.doubleTapSeekLength().collectAsState()
        val progressThreshold by playerPreferences.progressPreference().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_general),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = playerPreferences.defaultPlayerOrientation(),
                    entries = PlayerOrientation.entries
                        .associate { it.flag to playerOrientationString(it) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_player_orientation),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.keepScreenOn(),
                    title = stringResource(MR.strings.pref_player_keep_screen_on),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.playerFullscreen(),
                    title = stringResource(MR.strings.pref_player_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.autoplayEnabled(),
                    title = stringResource(MR.strings.pref_player_autoplay),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.showLoadingCircle(),
                    title = stringResource(MR.strings.pref_player_show_loading),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.showCurrentChapter(),
                    title = stringResource(MR.strings.pref_player_show_chapter),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.reduceMotion(),
                    title = stringResource(MR.strings.pref_player_reduce_motion),
                    subtitle = stringResource(MR.strings.pref_player_reduce_motion_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.rememberPlayerBrightness(),
                    title = stringResource(MR.strings.pref_player_remember_brightness),
                    subtitle = stringResource(MR.strings.pref_player_remember_brightness_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.rememberPlayerVolume(),
                    title = stringResource(MR.strings.pref_player_remember_volume),
                    subtitle = stringResource(MR.strings.pref_player_remember_volume_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.preserveWatchingPosition(),
                    title = stringResource(MR.strings.pref_player_preserve_position),
                    subtitle = stringResource(MR.strings.pref_player_preserve_position_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = progressInterval,
                    valueRange = 1..30,
                    title = stringResource(MR.strings.pref_player_progress_interval),
                    valueString = "${progressInterval}s",
                    onValueChanged = {
                        playerPreferences.progressSaveIntervalSec().set(it)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = controlsDelay / 1000,
                    valueRange = 1..10,
                    title = stringResource(MR.strings.pref_player_controls_hide_delay),
                    valueString = "${controlsDelay / 1000}s",
                    onValueChanged = {
                        playerPreferences.playerTimeToDisappear().set(it * 1000)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = doubleTapSeek,
                    valueRange = 5..30,
                    title = stringResource(MR.strings.pref_player_double_tap_seek),
                    valueString = "${doubleTapSeek}s",
                    onValueChanged = {
                        playerPreferences.doubleTapSeekLength().set(it)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = (progressThreshold * 100).toInt(),
                    valueRange = 50..100,
                    title = stringResource(MR.strings.pref_player_progress_threshold),
                    valueString = "${(progressThreshold * 100).toInt()}%",
                    onValueChanged = {
                        playerPreferences.progressPreference().set(it / 100f)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getPipGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val pipEnabled by playerPreferences.enablePip().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_player_pip),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.enablePip(),
                    title = stringResource(MR.strings.pref_player_enable_pip),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.pipOnExit(),
                    title = stringResource(MR.strings.pref_player_pip_on_exit),
                    subtitle = stringResource(MR.strings.pref_player_pip_on_exit_summary),
                    enabled = pipEnabled,
                ),
            ),
        )
    }

    @Composable
    private fun getExternalPlayerGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_player_external),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.alwaysUseExternalPlayer(),
                    title = stringResource(MR.strings.pref_player_always_external),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = playerPreferences.externalPlayerPreference(),
                    title = stringResource(MR.strings.pref_player_external_app),
                ),
            ),
        )
    }

    @Composable
    private fun getAniSkipGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val aniSkipEnabled by playerPreferences.aniSkipEnabled().collectAsState()
        val skipIntroEnabled by playerPreferences.enableSkipIntro().collectAsState()
        val autoSkipIntro by playerPreferences.autoSkipIntro().collectAsState()
        val netflixStyleEnabled by playerPreferences.enableNetflixStyleIntroSkip().collectAsState()
        val waitingTime by playerPreferences.waitingTimeIntroSkip().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_player_aniskip),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.aniSkipEnabled(),
                    title = stringResource(MR.strings.pref_player_aniskip_enabled),
                    subtitle = stringResource(MR.strings.pref_player_aniskip_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.enableSkipIntro(),
                    title = stringResource(MR.strings.pref_player_skip_intro),
                    enabled = aniSkipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.autoSkipIntro(),
                    title = stringResource(MR.strings.pref_player_auto_skip_intro),
                    enabled = aniSkipEnabled && skipIntroEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = playerPreferences.enableNetflixStyleIntroSkip(),
                    title = stringResource(MR.strings.pref_player_netflix_skip),
                    enabled = aniSkipEnabled && skipIntroEnabled && !autoSkipIntro,
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = waitingTime,
                    valueRange = 1..15,
                    title = stringResource(MR.strings.pref_player_skip_wait_time),
                    valueString = "${waitingTime}s",
                    enabled = aniSkipEnabled && skipIntroEnabled && !autoSkipIntro && netflixStyleEnabled,
                    onValueChanged = {
                        playerPreferences.waitingTimeIntroSkip().set(it)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getGesturesGroup(gesturePreferences: GesturePreferences): Preference.PreferenceGroup {
        val volBrightEnabled by gesturePreferences.gestureVolumeBrightness().collectAsState()
        val skipLength by gesturePreferences.skipLengthPreference().collectAsState()

        val gestureEntries = persistentMapOf(
            SingleActionGesture.None to stringResource(MR.strings.pref_player_gesture_none),
            SingleActionGesture.Seek to stringResource(MR.strings.pref_player_gesture_seek),
            SingleActionGesture.PlayPause to stringResource(MR.strings.pref_player_gesture_play_pause),
        )

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_player_gestures),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = gesturePreferences.gestureVolumeBrightness(),
                    title = stringResource(MR.strings.pref_player_gesture_vol_bright),
                    subtitle = stringResource(MR.strings.pref_player_gesture_vol_bright_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = gesturePreferences.swapVolumeBrightness(),
                    title = stringResource(MR.strings.pref_player_swap_vol_bright),
                    subtitle = stringResource(MR.strings.pref_player_swap_vol_bright_summary),
                    enabled = volBrightEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = gesturePreferences.gestureHorizontalSeek(),
                    title = stringResource(MR.strings.pref_player_gesture_h_seek),
                    subtitle = stringResource(MR.strings.pref_player_gesture_h_seek_summary),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = skipLength,
                    valueRange = 3..30,
                    title = stringResource(MR.strings.pref_player_skip_length),
                    valueString = "${skipLength}s",
                    onValueChanged = {
                        gesturePreferences.skipLengthPreference().set(it)
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = gesturePreferences.playerSmoothSeek(),
                    title = stringResource(MR.strings.pref_player_smooth_seek),
                    subtitle = stringResource(MR.strings.pref_player_smooth_seek_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = gesturePreferences.leftDoubleTapGesture(),
                    entries = gestureEntries,
                    title = stringResource(MR.strings.pref_player_left_double_tap),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = gesturePreferences.centerDoubleTapGesture(),
                    entries = gestureEntries,
                    title = stringResource(MR.strings.pref_player_center_double_tap),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = gesturePreferences.rightDoubleTapGesture(),
                    entries = gestureEntries,
                    title = stringResource(MR.strings.pref_player_right_double_tap),
                ),
            ),
        )
    }

    @Composable
    private fun getSubtitlesGroup(subtitlePreferences: SubtitlePreferences): Preference.PreferenceGroup {
        val fontSize by subtitlePreferences.subtitleFontSize().collectAsState()
        val borderSize by subtitlePreferences.subtitleBorderSize().collectAsState()
        val shadowOffset by subtitlePreferences.shadowOffsetSubtitles().collectAsState()
        val subtitlePos by subtitlePreferences.subtitlePos().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_player_subtitles),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.EditTextPreference(
                    preference = subtitlePreferences.preferredSubLanguages(),
                    title = stringResource(MR.strings.pref_player_preferred_sub_lang),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = subtitlePreferences.subtitleFont(),
                    title = stringResource(MR.strings.pref_player_subtitle_font),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = fontSize,
                    valueRange = 20..100,
                    title = stringResource(MR.strings.pref_player_subtitle_font_size),
                    valueString = "$fontSize",
                    onValueChanged = {
                        subtitlePreferences.subtitleFontSize().set(it)
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = subtitlePreferences.boldSubtitles(),
                    title = stringResource(MR.strings.pref_player_subtitle_bold),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = subtitlePreferences.italicSubtitles(),
                    title = stringResource(MR.strings.pref_player_subtitle_italic),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = subtitlePreferences.borderStyleSubtitles(),
                    entries = persistentMapOf(
                        SubtitlesBorderStyle.OutlineAndShadow to stringResource(MR.strings.pref_player_subtitle_outline),
                        SubtitlesBorderStyle.OpaqueBox to stringResource(MR.strings.pref_player_subtitle_opaque_box),
                        SubtitlesBorderStyle.BackgroundBox to stringResource(MR.strings.pref_player_subtitle_bg_box),
                    ),
                    title = stringResource(MR.strings.pref_player_subtitle_border_style),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = borderSize,
                    valueRange = 0..10,
                    title = stringResource(MR.strings.pref_player_subtitle_border_size),
                    valueString = "$borderSize",
                    onValueChanged = {
                        subtitlePreferences.subtitleBorderSize().set(it)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = shadowOffset,
                    valueRange = 0..10,
                    title = stringResource(MR.strings.pref_player_subtitle_shadow_offset),
                    valueString = "$shadowOffset",
                    onValueChanged = {
                        subtitlePreferences.shadowOffsetSubtitles().set(it)
                    },
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = subtitlePos,
                    valueRange = 0..100,
                    title = stringResource(MR.strings.pref_player_subtitle_position),
                    valueString = "$subtitlePos",
                    onValueChanged = {
                        subtitlePreferences.subtitlePos().set(it)
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = subtitlePreferences.overrideSubsASS(),
                    title = stringResource(MR.strings.pref_player_subtitle_override_ass),
                    subtitle = stringResource(MR.strings.pref_player_subtitle_override_ass_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = subtitlePreferences.screenshotSubtitles(),
                    title = stringResource(MR.strings.pref_player_screenshot_subs),
                ),
            ),
        )
    }

    @Composable
    private fun getAudioGroup(audioPreferences: AudioPreferences): Preference.PreferenceGroup {
        val volumeBoostCap by audioPreferences.volumeBoostCap().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_player_audio),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.EditTextPreference(
                    preference = audioPreferences.preferredAudioLanguages(),
                    title = stringResource(MR.strings.pref_player_preferred_audio_lang),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = audioPreferences.enablePitchCorrection(),
                    title = stringResource(MR.strings.pref_player_pitch_correction),
                    subtitle = stringResource(MR.strings.pref_player_pitch_correction_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = audioPreferences.audioChannels(),
                    entries = persistentMapOf(
                        AudioChannels.Auto to stringResource(MR.strings.pref_player_audio_auto),
                        AudioChannels.AutoSafe to stringResource(MR.strings.pref_player_audio_auto_safe),
                        AudioChannels.Mono to stringResource(MR.strings.pref_player_audio_mono),
                        AudioChannels.Stereo to stringResource(MR.strings.pref_player_audio_stereo),
                        AudioChannels.ReverseStereo to stringResource(MR.strings.pref_player_audio_reverse_stereo),
                    ),
                    title = stringResource(MR.strings.pref_player_audio_channels),
                ),
                Preference.PreferenceItem.SliderPreference(
                    value = volumeBoostCap,
                    valueRange = 0..200,
                    title = stringResource(MR.strings.pref_player_volume_boost_cap),
                    valueString = "$volumeBoostCap%",
                    onValueChanged = {
                        audioPreferences.volumeBoostCap().set(it)
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getAdvancedGroup(
        decoderPreferences: DecoderPreferences,
        advancedPlayerPreferences: AdvancedPlayerPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_player_advanced),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = decoderPreferences.tryHWDecoding(),
                    title = stringResource(MR.strings.pref_player_hw_decoding),
                    subtitle = stringResource(MR.strings.pref_player_hw_decoding_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = decoderPreferences.gpuNext(),
                    title = stringResource(MR.strings.pref_player_gpu_next),
                    subtitle = stringResource(MR.strings.pref_player_gpu_next_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = decoderPreferences.videoDebanding(),
                    entries = persistentMapOf(
                        Debanding.None to stringResource(MR.strings.pref_player_debanding_none),
                        Debanding.CPU to stringResource(MR.strings.pref_player_debanding_cpu),
                        Debanding.GPU to stringResource(MR.strings.pref_player_debanding_gpu),
                    ),
                    title = stringResource(MR.strings.pref_player_debanding),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = decoderPreferences.useYUV420P(),
                    title = stringResource(MR.strings.pref_player_yuv420p),
                    subtitle = stringResource(MR.strings.pref_player_yuv420p_summary),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = advancedPlayerPreferences.mpvConf(),
                    title = stringResource(MR.strings.pref_player_mpv_conf),
                    subtitle = stringResource(MR.strings.pref_player_mpv_conf_summary),
                ),
                Preference.PreferenceItem.EditTextPreference(
                    preference = advancedPlayerPreferences.mpvInput(),
                    title = stringResource(MR.strings.pref_player_mpv_input),
                    subtitle = stringResource(MR.strings.pref_player_mpv_input_summary),
                ),
            ),
        )
    }

    @Composable
    private fun playerOrientationString(orientation: PlayerOrientation): String {
        return when (orientation) {
            PlayerOrientation.FREE -> stringResource(MR.strings.pref_player_orientation_free)
            PlayerOrientation.PORTRAIT -> stringResource(MR.strings.pref_player_orientation_portrait)
            PlayerOrientation.LANDSCAPE -> stringResource(MR.strings.pref_player_orientation_landscape)
            PlayerOrientation.LOCKED_PORTRAIT -> stringResource(MR.strings.pref_player_orientation_locked_portrait)
            PlayerOrientation.LOCKED_LANDSCAPE -> stringResource(MR.strings.pref_player_orientation_locked_landscape)
            PlayerOrientation.REVERSE_PORTRAIT -> stringResource(MR.strings.pref_player_orientation_reverse_portrait)
        }
    }
}
