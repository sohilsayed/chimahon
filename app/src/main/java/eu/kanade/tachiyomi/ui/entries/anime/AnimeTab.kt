package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.entries.anime.library.AnimeLibraryContent
import eu.kanade.presentation.entries.anime.library.AnimeLibrarySettingsDialog
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.entries.anime.library.AnimeLibraryScreenModel
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import kotlinx.coroutines.launch
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.AnimeLibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object AnimeTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 6u,
                title = stringResource(MR.strings.label_anime),
                icon = rememberVectorPainter(Icons.Outlined.PlayCircle),
            )
        }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { AnimeLibraryScreenModel() }
        val state by screenModel.state.collectAsState()
        val preferences: AnimeLibraryPreferences = remember { Injekt.get() }

        var showSettingsDialog by remember { mutableStateOf(false) }
        var showOpenVideoDialog by remember { mutableStateOf(false) }
        var showOverflowMenu by remember { mutableStateOf(false) }
        var currentPage by rememberSaveable { mutableIntStateOf(0) }

        val displayMode by remember { preferences.displayMode().changes() }
            .collectAsState(initial = preferences.displayMode().get())

        val snackbarHostState = remember { SnackbarHostState() }

        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                context.startActivity(PlayerActivity.newStandaloneIntent(context, uri))
            }
        }

        BackHandler(enabled = state.selectionMode) {
            screenModel.clearSelection()
        }

        Scaffold(
            topBar = {
                if (state.selectionMode) {
                    BulkSelectionToolbar(
                        selectedCount = state.selection.size,
                        isRunning = false,
                        onClickClearSelection = screenModel::clearSelection,
                        onChangeCategoryClick = { /* TODO: open category picker */ },
                        onSelectAll = { screenModel.selectAll(currentPage) },
                        onReverseSelection = { screenModel.invertSelection(currentPage) },
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(MR.strings.label_anime)) },
                        actions = {
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter",
                                )
                            }
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.PlayCircle,
                                    contentDescription = "More",
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MR.strings.action_open_video)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showOpenVideoDialog = true
                                    },
                                )
                            }
                        },
                    )
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            if (state.isLoading) {
                LoadingScreen(modifier = Modifier.padding(contentPadding))
            } else {
                AnimeLibraryContent(
                    categories = state.displayCategories,
                    currentPage = currentPage,
                    contentPadding = contentPadding,
                    selection = state.selection.map { it.id }.toSet(),
                    hasActiveFilters = state.hasActiveFilters,
                    showPageTabs = state.showCategoryTabs,
                    showAnimeCount = state.showAnimeCount,
                    displayMode = displayMode,
                    onChangeCurrentPage = { currentPage = it },
                    onAnimeClicked = { animeId -> navigator.push(AnimeScreen(animeId)) },
                    onContinueWatchingClicked = null,
                    onToggleSelection = { anime -> screenModel.toggleSelection(anime) },
                    onRefresh = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Refreshing anime library...")
                        }
                        false
                    },
                    getItemsForCategory = { category ->
                        state.library[category].orEmpty()
                    },
                )
            }
        }

        if (showSettingsDialog) {
            AnimeLibrarySettingsDialog(
                onDismissRequest = { showSettingsDialog = false },
            )
        }

        if (showOpenVideoDialog) {
            OpenVideoDialog(
                onDismiss = { showOpenVideoDialog = false },
                onOpenUrl = { url ->
                    showOpenVideoDialog = false
                    context.startActivity(PlayerActivity.newStandaloneIntent(context, Uri.parse(url)))
                },
                onPickFile = {
                    showOpenVideoDialog = false
                    filePickerLauncher.launch(arrayOf("video/*", "application/x-bittorrent", "application/octet-stream"))
                },
            )
        }
    }
}
