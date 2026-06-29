package eu.kanade.tachiyomi.ui.history

import android.content.Context
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.preference.asState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.entries.anime.DuplicateAnimeDialog
import eu.kanade.presentation.history.AnimeHistoryScreen
import eu.kanade.presentation.history.HistoryScreen
import eu.kanade.presentation.history.HistoryScreenContent
import eu.kanade.presentation.history.HistorySelectionToolbar
import eu.kanade.presentation.history.components.HistoryDeleteAllDialog
import eu.kanade.presentation.history.components.HistoryDeleteDialog
import eu.kanade.presentation.history.components.HistoryFilterDialog
import eu.kanade.presentation.manga.DuplicateMangaDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import mihon.feature.migration.dialog.MigrateMangaDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.episode.model.Episode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TabText
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.active
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

data object HistoryTab : Tab {
    @Suppress("unused")
    private fun readResolve(): Any = HistoryTab

    private val snackbarHostState = SnackbarHostState()

    private val resumeLastChapterReadEvent = Channel<Unit>()
    private val resumeLastEpisodeSeenEvent = Channel<Unit>()

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.label_recent_manga),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastChapterReadEvent.send(Unit)
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { HistoryScreenModel() }
        val state by screenModel.state.collectAsState()
        val animeScreenModel = rememberScreenModel { AnimeHistoryScreenModel() }
        val animeState by animeScreenModel.state.collectAsState()
        var selectedTab by rememberSaveable { mutableIntStateOf(TAB_MANGA) }
        // KMK -->
        val settingsScreenModel = rememberScreenModel { HistorySettingsScreenModel() }
        val usePanoramaCover by settingsScreenModel.historyPreferences.usePanoramaCover().collectAsState()
        // KMK <--

        Scaffold(
            topBar = { scrollBehavior ->
                if (selectedTab == TAB_MANGA && state.selectionMode) {
                    HistorySelectionToolbar(
                        selectedCount = state.selection.size,
                        onCancelActionMode = screenModel::toggleSelectionMode,
                        onClickSelectAll = { screenModel.toggleAllSelection(true) },
                        onClickInvertSelection = screenModel::invertSelection,
                        onClickClearHistory = { screenModel.setDialog(HistoryScreenModel.Dialog.Delete(state.selected)) },
                    )
                } else {
                    SearchToolbar(
                        titleContent = { AppBarTitle(stringResource(MR.strings.history)) },
                        searchQuery = when (selectedTab) {
                            TAB_ANIME -> animeState.searchQuery
                            else -> state.searchQuery
                        },
                        onChangeSearchQuery = {
                            when (selectedTab) {
                                TAB_ANIME -> animeScreenModel.updateSearchQuery(it)
                                else -> screenModel.updateSearchQuery(it)
                            }
                        },
                        actions = {
                            val actions = when (selectedTab) {
                                TAB_ANIME -> listOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.pref_clear_history),
                                        icon = Icons.Outlined.DeleteSweep,
                                        onClick = { animeScreenModel.setDialog(AnimeHistoryScreenModel.Dialog.DeleteAll) },
                                    ),
                                )
                                else -> listOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_filter),
                                        icon = Icons.Outlined.FilterList,
                                        iconTint = if (state.hasActiveFilters) {
                                            MaterialTheme.colorScheme.active
                                        } else {
                                            LocalContentColor.current
                                        },
                                        onClick = screenModel::showFilterDialog,
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.pref_clear_history),
                                        icon = Icons.Outlined.Checklist,
                                        onClick = screenModel::toggleSelectionMode,
                                    ),
                                )
                            }
                            AppBarActions(kotlinx.collections.immutable.persistentListOf(*actions.toTypedArray()))
                        },
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
            snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            val layoutDirection = LocalLayoutDirection.current
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        start = contentPadding.calculateStartPadding(layoutDirection),
                        end = contentPadding.calculateEndPadding(layoutDirection),
                    ),
            ) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.zIndex(1f),
                ) {
                    Tab(
                        selected = selectedTab == TAB_MANGA,
                        onClick = { selectedTab = TAB_MANGA },
                        text = { TabText(text = stringResource(MR.strings.label_library)) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                    Tab(
                        selected = selectedTab == TAB_ANIME,
                        onClick = { selectedTab = TAB_ANIME },
                        text = { TabText(text = stringResource(MR.strings.label_anime)) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                }

                val pagePadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
                when (selectedTab) {
                    TAB_ANIME -> AnimeHistoryScreen(
                        state = animeState,
                        contentPadding = pagePadding,
                        onClickCover = { navigator.push(AnimeScreen(it)) },
                        onClickResume = animeScreenModel::getNextEpisodeForAnime,
                        onDialogChange = animeScreenModel::setDialog,
                        onClickFavorite = animeScreenModel::addFavorite,
                    )
                    else -> {
                        when {
                            state.isLoading -> LoadingScreen(Modifier.padding(pagePadding))
                            state.list.isEmpty() -> {
                                val msg = if (!state.searchQuery.isNullOrEmpty()) {
                                    MR.strings.no_results_found
                                } else {
                                    MR.strings.information_no_recent_manga
                                }
                                EmptyScreen(
                                    stringRes = msg,
                                    modifier = Modifier.padding(pagePadding),
                                )
                            }
                            else -> {
                                val uiModels = remember(state.list) { state.getUiModel() }
                                HistoryScreenContent(
                                    state = state,
                                    history = uiModels,
                                    contentPadding = pagePadding,
                                    onClickCover = { history -> navigator.push(MangaScreen(history.mangaId)) },
                                    onClickResume = { history ->
                                        screenModel.getNextChapterForManga(history.mangaId, history.chapterId)
                                    },
                                    onClickDelete = { item -> screenModel.setDialog(HistoryScreenModel.Dialog.Delete(item)) },
                                    onClickFavorite = { history -> screenModel.addFavorite(history.mangaId) },
                                    selectionMode = state.selectionMode,
                                    onHistorySelected = screenModel::toggleSelection,
                                    usePanoramaCover = usePanoramaCover,
                                )
                            }
                        }
                    }
                }
            }
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is HistoryScreenModel.Dialog.Delete -> {
                HistoryDeleteDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = { all ->
                        // KMK -->
                        if (all) {
                            screenModel.removeAllFromHistory(dialog.histories)
                        } else {
                            screenModel.removeFromHistory(dialog.histories)
                        }
                        // KMK <--
                    },
                )
            }
            is HistoryScreenModel.Dialog.DeleteAll -> {
                HistoryDeleteAllDialog(
                    onDismissRequest = onDismissRequest,
                    onDelete = screenModel::removeAllHistory,
                )
            }
            is HistoryScreenModel.Dialog.DuplicateManga -> {
                DuplicateMangaDialog(
                    duplicates = dialog.duplicates,
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenManga = { navigator.push(MangaScreen(it.id)) },
                    onMigrate = { screenModel.showMigrateDialog(dialog.manga, it) },
                    // KMK -->
                    targetManga = dialog.manga,
                    // KMK <--
                )
            }
            is HistoryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveMangaToCategoriesAndAddToLibrary(dialog.manga, include)
                    },
                )
            }
            is HistoryScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    current = dialog.current,
                    target = dialog.target,
                    // Initiated from the context of [dialog.target] so we show [dialog.current].
                    onClickTitle = { navigator.push(MangaScreen(dialog.current.id)) },
                    onDismissRequest = onDismissRequest,
                )
            }
            // KMK -->
            is HistoryScreenModel.Dialog.FilterSheet -> {
                HistoryFilterDialog(
                    onDismissRequest = onDismissRequest,
                    screenModel = settingsScreenModel,
                )
            }
            // KMK <--
            null -> {}
        }

        val onAnimeDismissRequest = { animeScreenModel.setDialog(null) }
        when (val dialog = animeState.dialog) {
            is AnimeHistoryScreenModel.Dialog.Delete -> {
                HistoryDeleteDialog(
                    onDismissRequest = onAnimeDismissRequest,
                    onDelete = { all ->
                        if (all) {
                            animeScreenModel.removeAllFromHistory(dialog.history.animeId)
                        } else {
                            animeScreenModel.removeFromHistory(dialog.history)
                        }
                    },
                )
            }
            is AnimeHistoryScreenModel.Dialog.DeleteAll -> {
                HistoryDeleteAllDialog(
                    onDismissRequest = onAnimeDismissRequest,
                    onDelete = animeScreenModel::removeAllHistory,
                )
            }
            is AnimeHistoryScreenModel.Dialog.DuplicateAnime -> {
                DuplicateAnimeDialog(
                    onDismissRequest = onAnimeDismissRequest,
                    onConfirm = { animeScreenModel.addFavorite(dialog.anime) },
                    onOpenAnime = { navigator.push(AnimeScreen(dialog.duplicate.id)) },
                    onMigrate = { navigator.push(AnimeScreen(dialog.duplicate.id)) },
                )
            }
            is AnimeHistoryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onAnimeDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        animeScreenModel.moveAnimeToCategoriesAndAddToLibrary(dialog.anime, include)
                    },
                )
            }
            null -> {}
        }

        // KMK -->
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                // KMK <--
                (context as? MainActivity)?.ready = true

                // AM (DISCORD) -->
                with(DiscordRPCService) {
                    discordScope.launchIO { setScreen(context, DiscordScreen.HISTORY) }
                }
                // <-- AM (DISCORD)
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { e ->
                when (e) {
                    HistoryScreenModel.Event.InternalError ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                    HistoryScreenModel.Event.HistoryCleared ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                    is HistoryScreenModel.Event.OpenChapter -> openChapter(context, e.chapter)
                }
            }
        }

        LaunchedEffect(Unit) {
            animeScreenModel.events.collectLatest { e ->
                when (e) {
                    AnimeHistoryScreenModel.Event.InternalError ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                    AnimeHistoryScreenModel.Event.HistoryCleared ->
                        snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                    is AnimeHistoryScreenModel.Event.OpenEpisode -> openEpisode(context, e.episode)
                }
            }
        }

        LaunchedEffect(Unit) {
            resumeLastChapterReadEvent.receiveAsFlow().collectLatest {
                openChapter(context, screenModel.getNextChapter())
            }
        }

        LaunchedEffect(Unit) {
            resumeLastEpisodeSeenEvent.receiveAsFlow().collectLatest {
                openEpisode(context, animeScreenModel.getNextEpisode())
            }
        }
    }

    private suspend fun openChapter(context: Context, chapter: Chapter?) {
        if (chapter != null) {
            val intent = ReaderActivity.newIntent(context, chapter.mangaId, chapter.id)
            context.startActivity(intent)
        } else {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_chapter))
        }
    }

    private suspend fun openEpisode(context: Context, episode: Episode?) {
        if (episode == null) {
            snackbarHostState.showSnackbar(context.stringResource(MR.strings.no_next_episode))
            return
        }

        val playerPreferences: PlayerPreferences by injectLazy()
        withIOContext {
            if (playerPreferences.alwaysUseExternalPlayer().get()) {
                try {
                    val intent = ExternalIntents().getExternalIntent(context, episode.animeId, episode.id, null)
                    if (intent != null) {
                        context.startActivity(intent)
                        return@withIOContext
                    }
                } catch (e: Throwable) {
                    snackbarHostState.showSnackbar(e.message ?: context.stringResource(MR.strings.internal_error))
                }
            }
            context.startActivity(PlayerActivity.newIntent(context, episode.animeId, episode.id))
        }
    }
}

private const val TAB_MANGA = 0
private const val TAB_ANIME = 1
