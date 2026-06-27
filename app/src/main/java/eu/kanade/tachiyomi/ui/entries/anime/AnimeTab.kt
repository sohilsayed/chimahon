package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.anime.components.LibraryBottomActionMenu
import eu.kanade.presentation.entries.anime.library.AnimeLibraryContent
import eu.kanade.presentation.entries.anime.library.AnimeLibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.category.CategoryScreen
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
        var currentPage by rememberSaveable { mutableIntStateOf(0) }

        val displayMode by remember { preferences.displayMode().changes() }
            .collectAsState(initial = preferences.displayMode().get())

        val snackbarHostState = remember { SnackbarHostState() }

        val updatingCategoryText = stringResource(MR.strings.updating_category)
        val onDismissRequest = screenModel::closeDialog

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

        BackHandler(enabled = state.selectionMode || state.searchQuery != null) {
            when {
                state.selectionMode -> screenModel.clearSelection()
                state.searchQuery != null -> screenModel.search(null)
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                LibraryToolbar(
                    hasActiveFilters = state.hasActiveFilters,
                    selectedCount = state.selection.size,
                    title = LibraryToolbarTitle(
                        text = stringResource(MR.strings.label_anime),
                    ),
                    onClickUnselectAll = screenModel::clearSelection,
                    onClickSelectAll = { screenModel.selectAll(currentPage) },
                    onClickInvertSelection = { screenModel.invertSelection(currentPage) },
                    onClickFilter = { showSettingsDialog = true },
                    onClickRefresh = {
                        scope.launch {
                            snackbarHostState.showSnackbar(updatingCategoryText)
                        }
                    },
                    onClickGlobalUpdate = null,
                    onClickOpenRandomManga = null,
                    onClickSyncNow = null,
                    onClickSyncExh = null,
                    isSyncEnabled = false,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = screenModel::search,
                    scrollBehavior = scrollBehavior,
                    onInvalidateDownloadCache = null,
                    onClickEditCategories = {
                        navigator.push(CategoryScreen(CategoryScreen.Tab.ANIME))
                    },
                    editCategoriesTitle = stringResource(MR.strings.action_edit_categories),
                )
            },
            bottomBar = {
                LibraryBottomActionMenu(
                    visible = state.selectionMode,
                    onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                    onMarkAsSeenClicked = { screenModel.markSeenSelection(true) },
                    onMarkAsUnseenClicked = { screenModel.markSeenSelection(false) },
                    onDownloadClicked = null,
                    onDeleteClicked = { screenModel.removeAnime(state.selection.map { it.id }) },
                    onClickResetInfo = null,
                )
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
                            snackbarHostState.showSnackbar(updatingCategoryText)
                        }
                        false
                    },
                    getItemsForCategory = { category ->
                        state.library[category].orEmpty()
                    },
                )
            }
        }

        when (val dialog = state.dialog) {
            is AnimeLibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        navigator.push(CategoryScreen(CategoryScreen.Tab.ANIME))
                    },
                    onConfirm = { include, exclude ->
                        screenModel.clearSelection()
                        screenModel.setAnimeCategories(dialog.anime, include, exclude)
                    },
                )
            }
            null -> {}
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
