package eu.kanade.tachiyomi.ui.more

import android.content.Intent
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavSection
import eu.kanade.domain.ui.model.NavTabLayout
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.dictionary.ScreenLookupPermissionActivity
import eu.kanade.tachiyomi.ui.dictionary.ScreenLookupService
import eu.kanade.tachiyomi.ui.dictionary.ScreenLookupServiceState
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreen
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.novels.NovelLibraryScreenModel
import eu.kanade.tachiyomi.ui.libraryUpdateError.LibraryUpdateErrorScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.stats.StatsScreen
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import exh.ui.batchadd.BatchAddScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object MoreTab : Tab {
    private fun readResolve(): Any = MoreTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_more_enter)
            return TabOptions(
                index = 5u,
                title = stringResource(MR.strings.label_more),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(SettingsScreen())
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { MoreScreenModel() }
        val downloadQueueState by screenModel.downloadQueueState.collectAsState()
        val screenLookupActive by ScreenLookupServiceState.isRunning.collectAsState()
        MoreScreen(
            downloadQueueStateProvider = { downloadQueueState },
            downloadedOnly = screenModel.downloadedOnly,
            onDownloadedOnlyChange = { screenModel.downloadedOnly = it },
            incognitoMode = screenModel.incognitoMode,
            onIncognitoModeChange = { screenModel.incognitoMode = it },
            screenLookupActive = screenLookupActive,
            onScreenLookupChange = { enabled ->
                if (enabled) {
                    context.startActivity(Intent(context, ScreenLookupPermissionActivity::class.java))
                } else {
                    ScreenLookupService.stop(context)
                }
            },
            // SY -->
            moreTabKeys = NavTabLayout.parse(screenModel.moreTabKeys.value).getKeysForSection(NavSection.MORE),
            // SY <--
            onClickDownloadQueue = { navigator.push(DownloadQueueScreen) },
            onClickCategories = { navigator.push(CategoryScreen()) },
            onClickStats = { navigator.push(StatsScreen()) },
            onClickDataAndStorage = { navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage)) },
            onClickSettings = { navigator.push(SettingsScreen()) },
            onClickAbout = { navigator.push(SettingsScreen(SettingsScreen.Destination.About)) },
            // SY -->
            onClickBatchAdd = { navigator.push(BatchAddScreen()) },
            onClickUpdates = { navigator.push(UpdatesTab) },
            onClickHistory = { navigator.push(HistoryTab) },
            onClickLibrary = { navigator.push(eu.kanade.tachiyomi.ui.library.LibraryTab) },
            onClickBrowse = { navigator.push(eu.kanade.tachiyomi.ui.browse.BrowseTab) },
            onClickDictionary = { navigator.push(eu.kanade.tachiyomi.ui.dictionary.DictionaryTab) },
            onClickNovels = { navigator.push(eu.kanade.tachiyomi.ui.library.novels.NovelsTab) },
            onClickAnime = { navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeTab) },
            // SY <--
            // KMK -->
            onClickLibraryUpdateErrors = { navigator.push(LibraryUpdateErrorScreen()) },
            // KMK <--
        )

        // AM (DISCORD) -->
        LaunchedEffect(Unit) {
            with(DiscordRPCService) {
                discordScope.launchIO { setScreen(context, DiscordScreen.MORE) }
            }
        }
        // <-- AM (DISCORD)
    }
}

private class MoreScreenModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    preferences: BasePreferences = Injekt.get(),
    // SY -->
    uiPreferences: UiPreferences = Injekt.get(),
    // SY <--
) : ScreenModel {

    var downloadedOnly by preferences.downloadedOnly().asState(screenModelScope)
    var incognitoMode by preferences.incognitoMode().asState(screenModelScope)

    // SY -->
    val moreTabKeys = uiPreferences.navTabLayout().asState(screenModelScope)
    // SY <--

    private var _downloadQueueState: MutableStateFlow<DownloadQueueState> = MutableStateFlow(DownloadQueueState.Stopped)
    val downloadQueueState: StateFlow<DownloadQueueState> = _downloadQueueState.asStateFlow()

    init {
        screenModelScope.launchIO {
            combine(
                downloadManager.isDownloaderRunning,
                downloadManager.queueState,
                animeDownloadManager.isDownloaderRunning,
                animeDownloadManager.queueState,
            ) { mangaRunning, mangaQueue, animeRunning, animeQueue ->
                val totalSize = mangaQueue.size + animeQueue.size
                val isRunning = mangaRunning || animeRunning
                Pair(isRunning, totalSize)
            }
                .collectLatest { (isDownloading, downloadQueueSize) ->
                    val pendingDownloadExists = downloadQueueSize != 0
                    _downloadQueueState.value = when {
                        !pendingDownloadExists -> DownloadQueueState.Stopped
                        !isDownloading -> DownloadQueueState.Paused(downloadQueueSize)
                        else -> DownloadQueueState.Downloading(downloadQueueSize)
                    }
                }
        }
    }
}

sealed interface DownloadQueueState {
    data object Stopped : DownloadQueueState
    data class Paused(val pending: Int) : DownloadQueueState
    data class Downloading(val pending: Int) : DownloadQueueState
}
