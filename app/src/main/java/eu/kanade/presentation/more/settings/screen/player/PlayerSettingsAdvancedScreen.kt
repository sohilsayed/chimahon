package eu.kanade.presentation.more.settings.screen.player

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.presentation.more.settings.screen.player.editor.codeeditor.CodeEditScreen
import eu.kanade.tachiyomi.ui.player.settings.AdvancedPlayerPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

object PlayerSettingsAdvancedScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_player_advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val advancedPlayerPreferences = remember { Injekt.get<AdvancedPlayerPreferences>() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        val enableScripts = advancedPlayerPreferences.mpvScripts()
        val mpvConf = advancedPlayerPreferences.mpvConf()
        val mpvInput = advancedPlayerPreferences.mpvInput()

        val navigator = LocalNavigator.currentOrThrow

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                title = stringResource(MR.strings.pref_mpv_scripts),
                subtitle = stringResource(MR.strings.pref_mpv_scripts_summary),
                preference = enableScripts,
                onValueChanged = {
                    // Ask for external storage permission
                    if (it) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.fromParts("package", context.packageName, null)
                            context.startActivity(intent)
                        }
                    }
                    true
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_mpv_conf),
                subtitle = "mpv.conf",
                onClick = {
                    navigator.push(CodeEditScreen("mpv.conf"))
                },
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_mpv_input),
                subtitle = "input.conf",
                onClick = {
                    navigator.push(CodeEditScreen("input.conf"))
                },
            ),
        )
    }
}
