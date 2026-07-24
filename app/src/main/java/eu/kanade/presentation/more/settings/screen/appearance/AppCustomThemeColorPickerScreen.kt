package eu.kanade.presentation.more.settings.screen.appearance

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.ThemeColorPickerWidget
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AppCustomThemeColorPickerScreen(private val isDictionary: Boolean = false) : Screen() {

    @Composable
    override fun Content() {
        val uiPreferences: UiPreferences = Injekt.get()
        val dictionaryPreferences: eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences = Injekt.get()

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val controller = rememberColorPickerController()

        val customColor by (if (isDictionary) dictionaryPreferences.customColor() else uiPreferences.colorTheme()).collectAsState()

        val customColorPref = if (isDictionary) dictionaryPreferences.customColor() else uiPreferences.colorTheme()
        val appThemePref = if (isDictionary) null else uiPreferences.appTheme()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(KMR.strings.pref_custom_color),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier.padding(contentPadding),
            ) {
                ThemeColorPickerWidget(
                    controller = controller,
                    initialColor = Color(customColor),
                    onItemClick = { color, appTheme ->
                        customColorPref.set(color.toArgb())
                        appThemePref?.set(appTheme)
                        if (!isDictionary) {
                            (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        }
                        navigator.pop()
                    },
                )
            }
        }
    }
}
