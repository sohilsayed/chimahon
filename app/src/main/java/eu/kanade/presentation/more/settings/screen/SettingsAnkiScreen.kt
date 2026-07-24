package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.i18n.kmk.KMR

/**
 * Anki mining + dictionary profiles (per-profile fields).
 * Preference widgets are reused from [SettingsDictionaryScreen].
 */
object SettingsAnkiScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = KMR.strings.pref_category_anki

    @Composable
    override fun getPreferences(): List<Preference> =
        SettingsDictionaryScreen.ankiPreferences()
}
