package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.presentation.entries.anime.AnimeScreen
import eu.kanade.presentation.entries.anime.DuplicateAnimeDialog
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.anime.EpisodeSettingsDialog
import eu.kanade.presentation.entries.anime.SeasonSettingsDialog
import eu.kanade.presentation.entries.anime.components.AnimeCoverDialog
import eu.kanade.presentation.entries.anime.components.DeleteEpisodesDialog
import eu.kanade.presentation.entries.anime.components.SetIntervalDialog
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.more.settings.screen.player.PlayerSettingsGesturesScreen.SkipIntroLengthDialog
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.browse.animesource.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.source.local.entries.anime.isLocal

class AnimeScreen(
    private val animeId: Long,
    val fromSource: Boolean = false,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    @Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod")
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()
        val lifecycleOwner = LocalLifecycleOwner.current
        val screenModel = rememberScreenModel {
            AnimeScreenModel(
                context = context,
                lifecycle = lifecycleOwner.lifecycle,
                animeId = animeId,
                isFromSource = fromSource,
            )
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        if (state is AnimeScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeScreenModel.State.Success
        val isHttpSource = remember(successState.source) { successState.source is AnimeHttpSource }

        LaunchedEffect(successState.anime, screenModel.source) {
            if (isHttpSource) {
                assistUrl = withIOContext {
                    getAnimeUrl(screenModel.anime, screenModel.source)
                }
            }
        }

        AnimeScreen(
            state = successState,
            snackbarHostState = screenModel.snackbarHostState,
            nextUpdate = successState.anime.expectedNextUpdate,
            isTabletUi = isTabletUi(),
            episodeSwipeStartAction = screenModel.episodeSwipeStartAction,
            episodeSwipeEndAction = screenModel.episodeSwipeEndAction,
            showNextEpisodeAirTime = screenModel.showNextEpisodeAirTime,
            alwaysUseExternalPlayer = screenModel.alwaysUseExternalPlayer,
            showFileSize = screenModel.showFileSize,
            onBackClicked = navigator::pop,
            onEpisodeClicked = { episode, alt ->
                scope.launchIO {
                    openEpisode(
                        context = context,
                        episode = episode,
                        useExternalPlayer = screenModel.alwaysUseExternalPlayer != alt,
                    )
                }
            },
            onDownloadEpisode = screenModel::runEpisodeDownloadActions.takeIf { !successState.source.isLocalOrStub() },
            onAddToLibraryClicked = {
                screenModel.toggleFavorite()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onWebViewClicked = {
                openAnimeInWebView(navigator, screenModel.anime, screenModel.source)
            }.takeIf { isHttpSource },
            onWebViewLongClicked = {
                copyAnimeUrl(context, screenModel.anime, screenModel.source)
            }.takeIf { isHttpSource },
            onTrackingClicked = {
                navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
            },
            onTagSearch = { navigator.push(GlobalAnimeSearchScreen(it)) },
            onFilterButtonClicked = screenModel::showSettingsDialog,
            onSeasonClicked = { navigator.push(AnimeScreen(it.anime.id, true)) },
            onContinueWatchingClicked = { season ->
                scope.launchIO {
                    val episode = screenModel.getNextUnseenEpisode(season)
                    if (episode != null) {
                        openEpisode(
                            context = context,
                            episode = episode,
                            useExternalPlayer = screenModel.alwaysUseExternalPlayer,
                        )
                    }
                }
            },
            onRefresh = screenModel::fetchAllFromSource,
            onContinueWatching = {
                scope.launchIO {
                    continueWatching(
                        context = context,
                        unseenEpisode = screenModel.getNextUnseenEpisode(),
                        useExternalPlayer = screenModel.alwaysUseExternalPlayer,
                    )
                }
            },
            onSearch = { query, _ -> navigator.push(GlobalAnimeSearchScreen(query)) },
            onRelatedAnimeClicked = { navigator.push(AnimeScreen(it.id, true)) },
            onCoverClicked = screenModel::showCoverDialog,
            onShareClicked = {
                shareAnime(context, screenModel.anime, screenModel.source)
            }.takeIf { isHttpSource },
            onDownloadActionClicked = screenModel::runDownloadAction.takeIf { !successState.source.isLocalOrStub() },
            onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf { successState.anime.favorite },
            onEditFetchIntervalClicked = screenModel::showSetAnimeFetchIntervalDialog.takeIf {
                successState.anime.favorite
            },
            onMigrateClicked = {
                navigator.push(GlobalAnimeSearchScreen(successState.anime.title))
            }.takeIf { successState.anime.favorite },
            changeAnimeSkipIntro = screenModel::showAnimeSkipIntroDialog.takeIf { successState.anime.favorite },
            onEditInfoClicked = screenModel::showEditAnimeInfoDialog,
            onMultiBookmarkClicked = screenModel::bookmarkEpisodes,
            onMultiFillermarkClicked = screenModel::fillermarkEpisodes,
            onMultiMarkAsSeenClicked = screenModel::markEpisodesSeen,
            onMarkPreviousAsSeenClicked = screenModel::markPreviousEpisodeSeen,
            onMultiDeleteClicked = screenModel::showDeleteEpisodeDialog,
            onEpisodeSwipe = screenModel::episodeSwipe,
            onEpisodeSelected = screenModel::toggleSelection,
            onAllEpisodeSelected = screenModel::toggleAllSelection,
            onInvertSelection = screenModel::invertSelection,
        )

        val onDismissRequest = { screenModel.dismissDialog() }
        when (val dialog = successState.dialog) {
            null -> {}
            is AnimeScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoryScreen(CategoryScreen.Tab.ANIME)) },
                    onConfirm = { include, _ ->
                        screenModel.moveAnimeToCategoriesAndAddToLibrary(dialog.anime, include)
                    },
                )
            }
            is AnimeScreenModel.Dialog.DeleteEpisodes -> {
                DeleteEpisodesDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.toggleAllSelection(false)
                        screenModel.deleteEpisodes(dialog.episodes)
                    },
                )
            }
            is AnimeScreenModel.Dialog.DuplicateAnime -> {
                DuplicateAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.toggleFavorite(onRemoved = {}, checkDuplicate = false) },
                    onOpenAnime = { navigator.push(AnimeScreen(dialog.duplicates.firstOrNull()?.id ?: dialog.anime.id)) },
                    onMigrate = { navigator.push(GlobalAnimeSearchScreen(dialog.anime.title)) },
                )
            }
            AnimeScreenModel.Dialog.SettingsSheet -> {
                EpisodeSettingsDialog(
                    onDismissRequest = onDismissRequest,
                    anime = successState.anime,
                    onDownloadFilterChanged = screenModel::setDownloadedFilter,
                    onUnseenFilterChanged = screenModel::setUnseenFilter,
                    onBookmarkedFilterChanged = screenModel::setBookmarkedFilter,
                    onFillermarkedFilterChanged = screenModel::setFillermarkedFilter,
                    onSortModeChanged = screenModel::setSorting,
                    onDisplayModeChanged = screenModel::setDisplayMode,
                    onSetAsDefault = screenModel::setCurrentSettingsAsDefault,
                )
            }
            AnimeScreenModel.Dialog.SeasonSettings -> {
                SeasonSettingsDialog(
                    onDismissRequest = onDismissRequest,
                    anime = successState.anime,
                    onDownloadFilterChanged = screenModel::setSeasonDownloadedFilter,
                    onUnseenFilterChanged = screenModel::setSeasonUnseenFilter,
                    onStartedFilterChanged = screenModel::setSeasonStartedFilter,
                    onCompletedFilterChanged = screenModel::setSeasonCompletedFilter,
                    onBookmarkedFilterChanged = screenModel::setSeasonBookmarkedFilter,
                    onFillermarkedFilterChanged = screenModel::setSeasonFillermarkedFilter,
                    onSortModeChanged = screenModel::setSeasonSorting,
                    onDisplayGridModeChanged = screenModel::setSeasonDisplayGridMode,
                    onDisplayGridSizeChanged = screenModel::setSeasonDisplayGridSize,
                    onOverlayDownloadedChanged = screenModel::setSeasonDownloadedOverlay,
                    onOverlayUnseenChanged = screenModel::setSeasonUnseenOverlay,
                    onOverlayLocalChanged = screenModel::setSeasonLocalOverlay,
                    onOverlayLangChanged = screenModel::setSeasonLangOverlay,
                    onOverlayContinueChanged = screenModel::setSeasonContinueOverlay,
                    onDisplayModeChanged = screenModel::setSeasonDisplayMode,
                    onSetAsDefault = screenModel::setSeasonSettingsAsDefault,
                )
            }
            AnimeScreenModel.Dialog.FullCover -> {
                val sm = rememberScreenModel { AnimeCoverScreenModel(successState.anime.id) }
                val anime by sm.state.collectAsState()
                if (anime != null) {
                    val getContent = rememberLauncherForActivityResult(
                        ActivityResultContracts.GetContent(),
                    ) {
                        if (it == null) return@rememberLauncherForActivityResult
                        sm.editCover(context, it)
                    }
                    AnimeCoverDialog(
                        anime = anime!!,
                        snackbarHostState = sm.snackbarHostState,
                        isCustomCover = remember(anime) { anime!!.hasCustomCover() },
                        onShareClick = { sm.shareCover(context) },
                        onSaveClick = { sm.saveCover(context) },
                        onEditClick = {
                            when (it) {
                                EditCoverAction.EDIT -> getContent.launch("image/*")
                                EditCoverAction.DELETE -> sm.deleteCustomCover(context)
                            }
                        },
                        onDismissRequest = onDismissRequest,
                    )
                } else {
                    LoadingScreen(Modifier.systemBarsPadding())
                }
            }
            is AnimeScreenModel.Dialog.EditAnimeInfo -> {
                EditAnimeDialog(
                    anime = dialog.anime,
                    onDismissRequest = onDismissRequest,
                    onPositiveClick = screenModel::updateAnimeInfo,
                )
            }
            is AnimeScreenModel.Dialog.SetAnimeFetchInterval -> {
                SetIntervalDialog(
                    interval = dialog.anime.fetchInterval,
                    nextUpdate = dialog.anime.expectedNextUpdate,
                    onDismissRequest = onDismissRequest,
                    onValueChanged = { interval: Int -> screenModel.setFetchInterval(dialog.anime, interval) }
                        .takeIf { screenModel.isUpdateIntervalEnabled },
                )
            }
            AnimeScreenModel.Dialog.ChangeAnimeSkipIntro -> {
                SkipIntroLengthDialog(
                    initialSkipIntroLength = if (!successState.anime.skipIntroDisable &&
                        successState.anime.skipIntroLength == 0
                    ) {
                        screenModel.gesturePreferences.defaultIntroLength().get()
                    } else {
                        successState.anime.skipIntroLength
                    },
                    onDismissRequest = onDismissRequest,
                    onValueChanged = {
                        scope.launchIO {
                            screenModel.setAnimeViewerFlags.awaitSetSkipIntroLength(animeId, it.toLong())
                        }
                        onDismissRequest()
                    },
                )
            }
            is AnimeScreenModel.Dialog.ShowQualities,
            is AnimeScreenModel.Dialog.QualitySelection,
            is AnimeScreenModel.Dialog.DownloadLoading,
            is AnimeScreenModel.Dialog.ConfirmDelete,
            is AnimeScreenModel.Dialog.Migrate,
            AnimeScreenModel.Dialog.TrackSheet,
            -> {
                AlertDialog(
                    onDismissRequest = onDismissRequest,
                    confirmButton = {
                        TextButton(onClick = onDismissRequest) {
                            Text(text = stringResource(MR.strings.action_ok))
                        }
                    },
                    text = {
                        Text(text = stringResource(MR.strings.not_applicable))
                    },
                )
            }
        }
    }

    private suspend fun continueWatching(
        context: Context,
        unseenEpisode: Episode?,
        useExternalPlayer: Boolean,
    ) {
        if (unseenEpisode != null) openEpisode(context, unseenEpisode, useExternalPlayer)
    }

    private suspend fun openEpisode(context: Context, episode: Episode, useExternalPlayer: Boolean) {
        if (useExternalPlayer) {
            try {
                val intent = ExternalIntents().getExternalIntent(context, animeId, episode.id, null)
                if (intent != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (e: Throwable) {
                context.toast(e.message)
            }
        }

        context.startActivity(PlayerActivity.newIntent(context, animeId, episode.id))
    }

    @Suppress("LocalVariableName")
    private fun getAnimeUrl(anime_: Anime?, source_: AnimeSource?): String? {
        val anime = anime_ ?: return null
        val source = source_ as? AnimeHttpSource ?: return null

        return try {
            source.getAnimeUrl(anime.toSAnime())
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("LocalVariableName")
    private fun openAnimeInWebView(navigator: cafe.adriel.voyager.navigator.Navigator, anime_: Anime?, source_: AnimeSource?) {
        getAnimeUrl(anime_, source_)?.let { url ->
            navigator.push(
                WebViewScreen(
                    url = url,
                    initialTitle = anime_?.title,
                    sourceId = source_?.id,
                ),
            )
        }
    }

    @Suppress("LocalVariableName")
    private fun shareAnime(context: Context, anime_: Anime?, source_: AnimeSource?) {
        try {
            getAnimeUrl(anime_, source_)?.let { url ->
                val intent = url.toUri().toShareIntent(context, type = "text/plain")
                context.startActivity(
                    Intent.createChooser(
                        intent,
                        null,
                    ),
                )
            }
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    @Suppress("LocalVariableName")
    private fun copyAnimeUrl(context: Context, anime_: Anime?, source_: AnimeSource?) {
        val url = getAnimeUrl(anime_, source_) ?: return
        context.copyToClipboard(url, url)
    }

    private fun AnimeSource.isLocalOrStub(): Boolean = isLocal() || this is StubAnimeSource
}
