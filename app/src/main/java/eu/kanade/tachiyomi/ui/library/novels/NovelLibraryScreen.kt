package eu.kanade.tachiyomi.ui.library.novels

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.canopus.chimareader.data.BookMetadata
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.LibraryTabs
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.components.LibraryToolbarTitle
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.ui.category.NovelCategoryScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.plus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Screen.NovelLibraryScreen() {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { NovelLibraryScreenModel() }
    val state: NovelLibraryScreenModel.State by screenModel.state.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val epubPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            screenModel.importBook(uri)
        }
    }

    // Mirror manga library: back exits selection or clears search
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
                    text = stringResource(MR.strings.label_novels),
                    numberOfManga = if (state.activeCategory != null) {
                        state.getItemCountForCategory(state.activeCategory!!)
                    } else {
                        null
                    },
                ),
                onClickUnselectAll = screenModel::clearSelection,
                onClickSelectAll = screenModel::selectAll,
                onClickInvertSelection = screenModel::invertSelection,
                onClickFilter = screenModel::showSortDialog,
                onClickRefresh = {
                    if (!SyncDataJob.isRunning(context)) {
                        SyncDataJob.startNow(context, manual = true)
                    } else {
                        context.toast(SYMR.strings.sync_in_progress)
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
                onClickEditCategories = { navigator.push(NovelCategoryScreen()) },
                editCategoriesTitle = stringResource(MR.strings.action_edit_novel_categories),
                updateCategoryTitle = stringResource(SYMR.strings.label_sync),
            )
        },
        bottomBar = {
            NovelSelectionBar(
                visible = state.selectionMode,
                onEditClicked = screenModel::showEditDialog,
                canEdit = state.selection.size == 1,
                onChangeCategoryClicked = screenModel::showChangeCategoryDialog,
                onDeleteClicked = screenModel::showDeleteConfirmDialog,
                onResetClicked = screenModel::resetStatsForSelected,
            )
        },
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(
                visible = !state.selectionMode,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = { epubPicker.launch("application/epub+zip") },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(MR.strings.action_add),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        if (state.isLoading) {
            LoadingScreen(Modifier.padding(contentPadding))
        } else if (state.isLibraryEmpty) {
            EmptyScreen(
                stringRes = MR.strings.information_empty_library,
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            NovelLibraryContent(
                state = state,
                screenModel = screenModel,
                contentPadding = contentPadding,
                onCategoryChange = screenModel::updateActiveCategoryIndex,
                onClickBook = { book ->
                    val bookDir = com.canopus.chimareader.data.BookStorage.getBookDirectory(context, book.id)
                    com.canopus.chimareader.ui.reader.NovelReaderActivity.launch(context, bookDir)
                },
            )
        }
    }

    // Hide bottom nav while in selection mode — mirrors manga library
    LaunchedEffect(state.selectionMode, state.dialog) {
        HomeScreen.showBottomNav(!state.selectionMode)
    }

    // Reload whenever the screen is (re-)entered — catches books imported via + button
    LaunchedEffect(Unit) {
        screenModel.loadLibrary()
    }

    val onDismissRequest = screenModel::closeDialog
    when (val dialog = state.dialog) {
        is NovelLibraryScreenModel.Dialog.ChangeCategory -> {
            eu.kanade.presentation.category.components.ChangeCategoryDialog(
                initialSelection = state.categories.map { cat ->
                    val name = if (cat.isSystemCategory) stringResource(MR.strings.label_default) else cat.name
                    tachiyomi.core.common.preference.CheckboxState.State.None(
                        tachiyomi.domain.category.model.Category(
                            id = if (cat.isSystemCategory) 0L else cat.id.hashCode().toLong(),
                            name = name,
                            order = cat.order.toLong(),
                            flags = cat.flags,
                            hidden = false,
                        ),
                    )
                }.toImmutableList(),
                onDismissRequest = onDismissRequest,
                onEditCategories = { navigator.push(NovelCategoryScreen()) },
                onConfirm = { included: List<Long>, _ ->
                    val selectedCategory = state.categories.find {
                        (if (it.isSystemCategory) 0L else it.id.hashCode().toLong()) == included.firstOrNull()
                    }
                    if (selectedCategory != null) {
                        screenModel.moveSelectedToCategory(selectedCategory.id)
                    } else {
                        screenModel.closeDialog()
                    }
                },
            )
        }

        is NovelLibraryScreenModel.Dialog.DeleteConfirm -> {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                title = { Text(stringResource(MR.strings.action_delete)) },
                text = { Text(stringResource(MR.strings.action_delete)) },
                confirmButton = {
                    TextButton(onClick = {
                        screenModel.deleteSelected()
                        screenModel.closeDialog()
                    }) {
                        Text(stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        is NovelLibraryScreenModel.Dialog.EditBook -> {
            val book = dialog.book
            val dictPrefs = remember { Injekt.get<eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences>() }
            val profiles = remember { dictPrefs.profileStore.getProfiles() }
            val novelOverrideKey = chimahon.dictionary.DictionaryProfileResolver.novelOverrideKey(book.id)
            val initialOverride = remember { dictPrefs.rawProfileOverride(novelOverrideKey).get() }

            var editTitle by remember { mutableStateOf(book.title ?: "") }
            var editAuthor by remember { mutableStateOf(book.author ?: "") }
            var editLang by remember { mutableStateOf(book.lang ?: "") }
            var selectedOverride by remember { mutableStateOf(initialOverride) }

            val sourcePreferences = remember { Injekt.get<eu.kanade.domain.source.service.SourcePreferences>() }
            val enabledLanguages = remember {
                val langs = sourcePreferences.enabledLanguages().get()
                langs.filter { it != "all" }.sortedWith { a, b ->
                    eu.kanade.tachiyomi.util.system.LocaleHelper.getDisplayName(a)
                        .compareTo(eu.kanade.tachiyomi.util.system.LocaleHelper.getDisplayName(b))
                }
            }

            val resolvedAutoProfile = remember(editLang) {
                dictPrefs.profileResolver.resolve(
                    sourceId = 0L,
                    sourceLang = editLang,
                    novelId = ""
                )
            }
            val autoLabel = "Auto (${resolvedAutoProfile.name})"

            AlertDialog(
                onDismissRequest = onDismissRequest,
                title = { Text(stringResource(MR.strings.action_edit)) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.TextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.TextField(
                            value = editAuthor,
                            onValueChange = { editAuthor = it },
                            label = { Text("Author") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        var langExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = langExpanded,
                            onExpandedChange = { langExpanded = it }
                        ) {
                            val langDisplayName = if (editLang.isEmpty()) "None" else eu.kanade.tachiyomi.util.system.LocaleHelper.getDisplayName(editLang)
                            androidx.compose.material3.TextField(
                                value = langDisplayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Language") },
                                trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = langExpanded,
                                onDismissRequest = { langExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        editLang = ""
                                        langExpanded = false
                                    }
                                )
                                enabledLanguages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(eu.kanade.tachiyomi.util.system.LocaleHelper.getDisplayName(lang)) },
                                        onClick = {
                                            editLang = lang
                                            langExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        var profileExpanded by remember { mutableStateOf(false) }
                        val selectedName = if (selectedOverride.isEmpty()) {
                            autoLabel
                        } else {
                            profiles.firstOrNull { it.id == selectedOverride }?.name ?: autoLabel
                        }
                        
                        ExposedDropdownMenuBox(
                            expanded = profileExpanded,
                            onExpandedChange = { profileExpanded = it }
                        ) {
                            androidx.compose.material3.TextField(
                                value = selectedName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Anki Profile Override") },
                                trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded) },
                                modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = profileExpanded,
                                onDismissRequest = { profileExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(autoLabel) },
                                    onClick = {
                                        selectedOverride = ""
                                        profileExpanded = false
                                    }
                                )
                                profiles.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name) },
                                        onClick = {
                                            selectedOverride = p.id
                                            profileExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val updatedBook = book.copy(
                                title = editTitle.ifBlank { null },
                                author = editAuthor.ifBlank { null },
                                lang = editLang.ifBlank { null }
                            )
                            screenModel.updateBookMetadata(updatedBook, selectedOverride)
                        }
                    ) {
                        Text(stringResource(MR.strings.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                }
            )
        }

        is NovelLibraryScreenModel.Dialog.SortFilter, NovelLibraryScreenModel.Dialog.Settings -> {
            TabbedDialog(
                onDismissRequest = onDismissRequest,
                tabTitles = persistentListOf(
                    stringResource(MR.strings.action_sort),
                    stringResource(MR.strings.action_display),
                ),
            ) { page ->
                Column(
                    modifier = Modifier
                        .padding(vertical = TabbedDialogPaddings.Vertical)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (page) {
                        0 -> { // Sort Tab
                            NovelLibraryScreenModel.SortMode.entries.forEach { mode ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { screenModel.setSort(mode, state.sortDescending) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = state.sortMode == mode,
                                        onClick = { screenModel.setSort(mode, state.sortDescending) },
                                    )
                                    Text(
                                        text = when (mode) {
                                            NovelLibraryScreenModel.SortMode.Alphabetical ->
                                                stringResource(MR.strings.action_sort_alpha)
                                            NovelLibraryScreenModel.SortMode.DateAdded ->
                                                stringResource(MR.strings.action_sort_date_added)
                                            NovelLibraryScreenModel.SortMode.LastRead ->
                                                stringResource(MR.strings.action_sort_last_read)
                                        },
                                        modifier = Modifier.padding(start = 12.dp),
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { screenModel.setSort(state.sortMode, !state.sortDescending) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = state.sortDescending,
                                    onCheckedChange = { screenModel.setSort(state.sortMode, it) },
                                )
                                Text(
                                    text = stringResource(MR.strings.action_desc),
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }

                        1 -> { // Display Tab
                            val displayMode by screenModel.getDisplayMode().collectAsState()

                            SettingsChipRow(MR.strings.action_display_mode) {
                                listOf(
                                    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
                                    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
                                ).map { (titleRes, mode) ->
                                    FilterChip(
                                        selected = displayMode == mode,
                                        onClick = { screenModel.setDisplayMode(mode) },
                                        label = { Text(stringResource(titleRes)) },
                                    )
                                }
                            }

                            val configuration = LocalConfiguration.current
                            val isLandscape = configuration.orientation ==
                                android.content.res.Configuration.ORIENTATION_LANDSCAPE
                            val columnPreference = screenModel.getColumnsPreferenceForOrientation(isLandscape)
                            val columns by columnPreference.collectAsState()

                            SliderItem(
                                value = columns,
                                valueRange = 0..10,
                                label = stringResource(MR.strings.pref_library_columns),
                                valueString = if (columns > 0) columns.toString() else stringResource(MR.strings.label_auto),
                                onChange = columnPreference::set,
                            )

                            HeadingItem(MR.strings.tabs_header)
                            CheckboxItem(
                                label = stringResource(MR.strings.action_display_show_tabs),
                                pref = screenModel.showTabs(),
                            )
                            CheckboxItem(
                                label = stringResource(MR.strings.action_display_show_number_of_items),
                                pref = screenModel.showNumberOfItems(),
                            )
                        }
                    }
                }
            }
        }

        null -> {}
    }
}

// ---------------------------------------------------------------------------

@Composable
fun NovelLibraryContent(
    state: NovelLibraryScreenModel.State,
    screenModel: NovelLibraryScreenModel,
    contentPadding: PaddingValues,
    onCategoryChange: (Int) -> Unit,
    onClickBook: (BookMetadata) -> Unit,
) {
    val pagerState = rememberPagerState(state.activeCategoryIndex) { state.categories.size }
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val columns by screenModel.getColumnsPreferenceForOrientation(isLandscape).collectAsState()
    val displayMode by screenModel.getDisplayMode().collectAsState()

    val showTabs by screenModel.showTabs().collectAsState()
    val showNumberOfItems by screenModel.showNumberOfItems().collectAsState()

    Column(modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
        if (showTabs && state.categories.size >= 1) {
            LibraryTabs(
                categories = state.categories.map {
                    val name = if (it.isSystemCategory) {
                        stringResource(MR.strings.label_default)
                    } else {
                        it.name
                    }
                    tachiyomi.domain.category.model.Category(
                        id = if (it.isSystemCategory) 0L else it.id.hashCode().toLong(),
                        name = name,
                        order = it.order.toLong(),
                        flags = it.flags,
                        hidden = false,
                    )
                },
                pagerState = pagerState,
                getItemCountForCategory = { cat ->
                    if (showNumberOfItems) {
                        state.categories.find { it.name == cat.name }?.let { state.getItemCountForCategory(it) }
                    } else {
                        null
                    }
                },
                onTabItemClick = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
        ) { page ->
            val category = state.categories[page]
            val books = state.getBooksForCategory(category)

            FastScrollLazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = if (columns == 0) GridCells.Adaptive(128.dp) else GridCells.Fixed(columns),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()) + PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
                verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
            ) {
                items(books, key = { it.id }) { book ->
                    val isSelected = state.selection.contains(book.id)
                    val coverData = tachiyomi.domain.manga.model.MangaCover(
                        mangaId = book.id.hashCode().toLong(),
                        sourceId = -1L,
                        isMangaFavorite = true,
                        ogUrl = book.cover,
                        lastModified = 0L,
                    )
                    val onLongClick: () -> Unit = { screenModel.toggleSelection(book.id) }
                    val onClick: () -> Unit = {
                        if (state.selectionMode) {
                            screenModel.toggleSelection(book.id)
                        } else {
                            onClickBook(book)
                        }
                    }

                    val bookTitle = book.title ?: ""
                    val bookLang = book.lang

                    if (displayMode == LibraryDisplayMode.ComfortableGrid) {
                        eu.kanade.presentation.library.components.MangaComfortableGridItem(
                            isSelected = isSelected,
                            title = bookTitle,
                            coverData = coverData,
                            coverBadgeStart = {
                                if (!bookLang.isNullOrBlank()) {
                                    eu.kanade.presentation.library.components.LanguageBadge(
                                        isLocal = true,
                                        sourceLanguage = bookLang,
                                    )
                                }
                            },
                            onLongClick = onLongClick,
                            onClick = onClick,
                            usePanoramaCover = false,
                        )
                    } else {
                        eu.kanade.presentation.library.components.MangaCompactGridItem(
                            isSelected = isSelected,
                            title = bookTitle,
                            coverData = coverData,
                            coverBadgeStart = {
                                if (!bookLang.isNullOrBlank()) {
                                    eu.kanade.presentation.library.components.LanguageBadge(
                                        isLocal = true,
                                        sourceLanguage = bookLang,
                                    )
                                }
                            },
                            onLongClick = onLongClick,
                            onClick = onClick,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        onCategoryChange(pagerState.currentPage)
    }
}

// ---------------------------------------------------------------------------

@Composable
fun NovelSelectionBar(
    visible: Boolean,
    onEditClicked: () -> Unit,
    canEdit: Boolean,
    onChangeCategoryClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onResetClicked: () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom),
        exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large.copy(
                bottomEnd = androidx.compose.foundation.shape.ZeroCornerSize,
                bottomStart = androidx.compose.foundation.shape.ZeroCornerSize,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(
                        WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                    )
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                if (canEdit) {
                    SelectionButton(
                        title = stringResource(MR.strings.action_edit),
                        icon = Icons.Outlined.Edit,
                        onClick = onEditClicked,
                    )
                }
                SelectionButton(
                    title = stringResource(MR.strings.action_mark_as_unread),
                    icon = Icons.Outlined.RestartAlt,
                    onClick = onResetClicked,
                )
                SelectionButton(
                    title = stringResource(MR.strings.action_move_category),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    onClick = onChangeCategoryClicked,
                )
                SelectionButton(
                    title = stringResource(MR.strings.action_delete),
                    icon = Icons.Outlined.Delete,
                    onClick = onDeleteClicked,
                )
            }
        }
    }
}

@Composable
private fun SelectionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
