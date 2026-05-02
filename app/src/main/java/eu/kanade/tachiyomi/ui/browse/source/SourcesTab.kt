package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined._18UpRating
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.SourceCategoriesDialog
import eu.kanade.presentation.browse.SourceOptionsDialog
import eu.kanade.presentation.browse.SourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreen.SmartSearchConfig
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import exh.ui.smartsearch.SmartSearchScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.sourcesTab(
    smartSearchConfig: SmartSearchConfig? = null,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { SourcesScreenModel(smartSearchConfig = smartSearchConfig) }
    val state by screenModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showImportDialog by remember { mutableStateOf(false) }

    val mangaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                scope.launch { ImportHandler.importManga(context, uris) }
            }
        },
    )

    val novelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                scope.launch { ImportHandler.importNovels(context, uris) }
            }
        },
    )

    if (showImportDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { androidx.compose.material3.Text(stringResource(MR.strings.action_add)) },
            text = { androidx.compose.material3.Text("Import local files to library") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showImportDialog = false
                        mangaPicker.launch(arrayOf("application/zip", "application/x-cbz", "application/x-rar", "application/x-cbr", "application/x-7z-compressed", "application/x-cb7", "application/x-tar", "application/x-cbt", "application/epub+zip"))
                    },
                ) {
                    androidx.compose.material3.Text("Manga")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showImportDialog = false
                        novelPicker.launch(arrayOf("application/epub+zip"))
                    },
                ) {
                    androidx.compose.material3.Text("Novel")
                }
            },
        )
    }

    return TabContent(
        // SY -->
        titleRes = when (smartSearchConfig == null) {
            true -> MR.strings.label_sources
            false -> SYMR.strings.find_in_another_source
        },
        actions = persistentListOf<AppBar.Action>().let { actions ->
            var updatedActions = actions
            if (smartSearchConfig == null) {
                updatedActions = updatedActions.add(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_add),
                        icon = Icons.Outlined.Add,
                        onClick = { showImportDialog = true },
                    ),
                )
            }
            updatedActions.add(
                AppBar.Action(
                    title = stringResource(MR.strings.action_global_search),
                    icon = Icons.Outlined.TravelExplore,
                    onClick = { navigator.push(GlobalSearchScreen(smartSearchConfig?.origTitle ?: "")) },
                ),
            )
        }.let {
            // KMK -->
            it.add(
                title = stringResource(KMR.strings.action_toggle_nsfw_only),
                icon = Icons.Outlined._18UpRating,
                iconTint = if (state.nsfwOnly) MaterialTheme.colorScheme.error else LocalContentColor.current,
                onClick = { screenModel.toggleNsfwOnly() },
            ),
            // KMK <--
        ).let {
            when (smartSearchConfig) {
                null -> {
                    it.add(
                        AppBar.Action(
                            title = stringResource(MR.strings.action_filter),
                            icon = Icons.Outlined.FilterList,
                            onClick = { navigator.push(SourcesFilterScreen()) },
                        ),
                    )
                }
                // Merge: find in another source
                else -> it
            }
        },
        // SY <--
        content = { contentPadding, snackbarHostState ->
            SourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    // SY -->
                    val screen = when {
                        // Search selected source for entries to merge or for the recommending entry
                        smartSearchConfig != null -> SmartSearchScreen(source.id, smartSearchConfig)
                        listing == Listing.Popular && screenModel.useNewSourceNavigation -> SourceFeedScreen(source.id)
                        else -> BrowseSourceScreen(source.id, listing.query)
                    }
                    navigator.push(screen)
                    // SY <--
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
                // KMK -->
                onChangeSearchQuery = screenModel::search,
                // KMK <--
            )

            when (val dialog = state.dialog) {
                is SourcesScreenModel.Dialog.SourceLongClick -> {
                    val source = dialog.source
                    SourceOptionsDialog(
                        source = source,
                        onClickPin = {
                            screenModel.togglePin(source)
                            screenModel.closeDialog()
                        },
                        onClickDisable = {
                            screenModel.toggleSource(source)
                            screenModel.closeDialog()
                        },
                        // SY -->
                        onClickSetCategories = {
                            screenModel.showSourceCategoriesDialog(source)
                        }.takeIf { state.categories.isNotEmpty() },
                        onClickToggleDataSaver = {
                            screenModel.toggleExcludeFromDataSaver(source)
                            screenModel.closeDialog()
                        }.takeIf { state.dataSaverEnabled },
                        // SY <--
                        onDismiss = screenModel::closeDialog,
                        // KMK -->
                        onClickSettings = {
                            if (source.installedExtension !== null) {
                                navigator.push(ExtensionDetailsScreen(source.installedExtension!!.pkgName))
                            }
                            screenModel.closeDialog()
                        },
                        // KMK <--
                    )
                }
                is SourcesScreenModel.Dialog.SourceCategories -> {
                    val source = dialog.source
                    SourceCategoriesDialog(
                        source = source,
                        categories = state.categories,
                        onClickCategories = { categories ->
                            screenModel.setSourceCategories(source, categories)
                            screenModel.closeDialog()
                        },
                        onDismissRequest = screenModel::closeDialog,
                    )
                }
                null -> Unit
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        SourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
