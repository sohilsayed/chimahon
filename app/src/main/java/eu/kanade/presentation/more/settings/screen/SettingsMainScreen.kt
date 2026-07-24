package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsMainScreen
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import exh.assets.EhAssets
import exh.assets.ehassets.MangadexLogo
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

object SettingsMainScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsMainScreen

    @Composable
    override fun Content() {
        Content(twoPane = false)
    }

    @Composable
    private fun getPalerSurface(): Color {
        val surface = MaterialTheme.colorScheme.surface
        val dark = isSystemInDarkTheme()
        return remember(surface, dark) {
            val arr = FloatArray(3)
            ColorUtils.colorToHSL(surface.toArgb(), arr)
            arr[2] = if (dark) {
                arr[2] - 0.05f
            } else {
                arr[2] + 0.02f
            }.coerceIn(0f, 1f)
            Color.hsl(arr[0], arr[1], arr[2])
        }
    }

    @Composable
    fun Content(twoPane: Boolean) {
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow
        val containerColor = if (twoPane) getPalerSurface() else MaterialTheme.colorScheme.surface
        val topBarState = rememberTopAppBarState()

        Scaffold(
            topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(topBarState),
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.label_settings),
                    navigateUp = backPress::invoke,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_search),
                                    icon = Icons.Outlined.Search,
                                    onClick = { navigator.navigate(SettingsSearchScreen(), twoPane) },
                                ),
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            containerColor = containerColor,
            content = { contentPadding ->
                val state = rememberLazyListState()
                val entries = remember {
                    buildMainEntries().filter { entry ->
                        when (entry) {
                            is MainEntry.Header -> true
                            is MainEntry.Item -> entry.item.screen !is SearchableSettings ||
                                entry.item.screen.isEnabled()
                        }
                    }
                }
                // Drop section headers that have no visible items after them
                val visibleEntries = remember(entries) {
                    entries.filterIndexed { index, entry ->
                        if (entry !is MainEntry.Header) return@filterIndexed true
                        entries.drop(index + 1).takeWhile { it is MainEntry.Item }.isNotEmpty()
                    }
                }

                val indexSelected = if (twoPane) {
                    visibleEntries.indexOfFirst {
                        it is MainEntry.Item && it.item.screen::class == navigator.items.first()::class
                    }.also {
                        LaunchedEffect(Unit) {
                            if (it >= 0) {
                                state.animateScrollToItem(it)
                                if (it > 0) {
                                    topBarState.contentOffset = topBarState.heightOffsetLimit
                                }
                            }
                        }
                    }
                } else {
                    null
                }

                LazyColumn(
                    state = state,
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(
                        items = visibleEntries,
                        key = { _, entry ->
                            when (entry) {
                                is MainEntry.Header -> "settings-header-${entry.titleRes}"
                                is MainEntry.Item -> "settings-main-${entry.item.hashCode()}"
                            }
                        },
                    ) { index, entry ->
                        when (entry) {
                            is MainEntry.Header -> {
                                PreferenceGroupHeader(title = stringResource(entry.titleRes))
                            }
                            is MainEntry.Item -> {
                                val item = entry.item
                                val selected = indexSelected == index
                                var modifier: Modifier = Modifier
                                var contentColor = LocalContentColor.current
                                if (twoPane) {
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .then(
                                            if (selected) {
                                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                                            } else {
                                                Modifier
                                            },
                                        )
                                    if (selected) {
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                }
                                CompositionLocalProvider(LocalContentColor provides contentColor) {
                                    TextPreferenceWidget(
                                        modifier = modifier,
                                        title = stringResource(item.titleRes),
                                        subtitle = item.formatSubtitle(),
                                        icon = item.icon,
                                        onPreferenceClick = {
                                            navigator.navigate(item.screen, twoPane)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    private fun Navigator.navigate(screen: VoyagerScreen, twoPane: Boolean) {
        if (twoPane) replaceAll(screen) else push(screen)
    }

    private data class Item(
        val titleRes: StringResource,
        val subtitleRes: StringResource,
        val formatSubtitle: @Composable () -> String = { stringResource(subtitleRes) },
        val icon: ImageVector,
        val screen: VoyagerScreen,
    )

    private sealed class MainEntry {
        data class Header(val titleRes: StringResource) : MainEntry()
        data class Item(val item: SettingsMainScreen.Item) : MainEntry()
    }

    private fun buildMainEntries(): List<MainEntry> = listOf(
        // General
        MainEntry.Header(MR.strings.pref_category_general),
        MainEntry.Item(
            Item(
                titleRes = MR.strings.pref_category_appearance,
                subtitleRes = MR.strings.pref_appearance_summary,
                icon = Icons.Outlined.Palette,
                screen = SettingsAppearanceScreen,
            ),
        ),
        MainEntry.Item(
            Item(
                titleRes = MR.strings.pref_category_security,
                subtitleRes = MR.strings.pref_security_summary,
                icon = Icons.Outlined.Security,
                screen = SettingsSecurityScreen,
            ),
        ),
        MainEntry.Item(
            Item(
                titleRes = MR.strings.pref_category_advanced,
                subtitleRes = MR.strings.pref_advanced_summary,
                icon = Icons.Outlined.Code,
                screen = SettingsAdvancedScreen,
            ),
        ),
        // Media
        MainEntry.Header(KMR.strings.pref_settings_section_media),
        MainEntry.Item(
            Item(
                titleRes = MR.strings.pref_category_reader,
                subtitleRes = MR.strings.pref_reader_summary,
                icon = Icons.AutoMirrored.Outlined.ChromeReaderMode,
                screen = SettingsReaderScreen,
            ),
        ),
        MainEntry.Item(
            Item(
                titleRes = MR.strings.pref_category_player,
                subtitleRes = MR.strings.pref_player_summary,
                icon = Icons.Outlined.OndemandVideo,
                screen = PlayerSettingsMainScreen,
            ),
        ),
        MainEntry.Item(
            Item(
                titleRes = MR.strings.pref_category_library,
                subtitleRes = MR.strings.pref_library_summary,
                icon = Icons.Outlined.CollectionsBookmark,
                screen = SettingsLibraryScreen,
            ),
        ),
        MainEntry.Item(
            Item(
                titleRes = MR.strings.pref_category_downloads,
                subtitleRes = MR.strings.pref_downloads_summary,
                icon = Icons.Outlined.GetApp,
                screen = SettingsDownloadScreen,
            ),
        ),
        MainEntry.Item(
            Item(
                titleRes = MR.strings.browse,
                subtitleRes = MR.strings.pref_browse_summary,
                icon = Icons.Outlined.Explore,
                screen = SettingsBrowseScreen,
            ),
        ),
        // Learning
        MainEntry.Header(KMR.strings.pref_settings_section_learning),
        MainEntry.Item(
            Item(
                titleRes = KMR.strings.pref_category_dictionaries_and_audio,
                subtitleRes = KMR.strings.pref_dictionary_summary,
                icon = TranslateIcon,
                screen = SettingsDictionaryScreen,
            ),
        ),
        MainEntry.Item(
            Item(
                titleRes = KMR.strings.pref_category_dictionary_popup,
                subtitleRes = KMR.strings.pref_dictionary_popup_summary,
                icon = Icons.Outlined.Tab,
                screen = SettingsDictionaryPopupScreen,
            ),
        ),
        MainEntry.Item(
            Item(
                titleRes = KMR.strings.pref_category_anki,
                subtitleRes = KMR.strings.pref_anki_settings_summary,
                icon = CardsStar,
                screen = SettingsAnkiScreen,
            ),
        ),
        // Sync
        MainEntry.Header(KMR.strings.pref_settings_section_sync),
        MainEntry.Item(
            Item(
                titleRes = MR.strings.pref_category_tracking,
                subtitleRes = MR.strings.pref_tracking_summary,
                icon = Icons.Outlined.Sync,
                screen = SettingsTrackingScreen,
            ),
        ),
        // AM (CONNECTIONS) -->
        MainEntry.Item(
            Item(
                titleRes = KMR.strings.pref_category_connections,
                subtitleRes = KMR.strings.pref_connections_summary,
                icon = Icons.Outlined.Link,
                screen = SettingsConnectionScreen,
            ),
        ),
        // <-- AM (CONNECTIONS)
        MainEntry.Item(
            Item(
                titleRes = MR.strings.label_data_storage,
                subtitleRes = MR.strings.pref_backup_summary,
                icon = Icons.Outlined.Storage,
                screen = SettingsDataScreen,
            ),
        ),
        // SY -->
        MainEntry.Item(
            Item(
                titleRes = SYMR.strings.pref_category_mangadex,
                subtitleRes = SYMR.strings.pref_mangadex_summary,
                icon = EhAssets.MangadexLogo,
                screen = SettingsMangadexScreen,
            ),
        ),
        // SY <--
        MainEntry.Item(
            Item(
                titleRes = MR.strings.pref_category_about,
                subtitleRes = StringResource(0),
                formatSubtitle = {
                    "${stringResource(MR.strings.app_name)} ${AboutScreen.getVersionName(withBuildDate = false)}"
                },
                icon = Icons.Outlined.Info,
                screen = AboutScreen(),
            ),
        ),
    )
}
