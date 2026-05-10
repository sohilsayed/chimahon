package eu.kanade.tachiyomi.ui.anime

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import android.content.Intent
import eu.kanade.presentation.anime.AnimeScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.browse.animesource.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.util.system.isTabletUi
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeScreen(
    private val animeId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { AnimeScreenModel(animeId = animeId) }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val isTabletUi = LocalConfiguration.current.isTabletUi()

        when (val s = state) {
            is AnimeScreenModel.State.Loading -> {
                LoadingScreen()
            }
            is AnimeScreenModel.State.Success -> {
                AnimeScreen(
                    state = s,
                    snackbarHostState = snackbarHostState,
                    isTabletUi = isTabletUi,
                    navigateUp = navigator::pop,
                    onEpisodeClicked = { episode ->
                        context.startActivity(
                            PlayerActivity.newIntent(context, animeId, episode.id),
                        )
                    },
                    onContinueWatching = {
                        val episode = s.nextUnseenEpisode ?: return@AnimeScreen
                        context.startActivity(
                            PlayerActivity.newIntent(context, animeId, episode.id),
                        )
                    },
                    onAddToLibraryClicked = { screenModel.toggleFavorite() },
                    onAddToLibraryAnywayClicked = { screenModel.toggleFavorite(checkDuplicate = false) },
                    onTagSearch = { tag -> navigator.push(GlobalAnimeSearchScreen(tag)) },
                    onFilterButtonClicked = {
                        screenModel.showDialog(AnimeScreenModel.Dialog.SettingsSheet)
                    },
                    onShareClicked = run {
                        val sourceManager = Injekt.get<tachiyomi.domain.animesource.service.AnimeSourceManager>()
                        val source = sourceManager.get(s.anime.source) as? AnimeHttpSource
                        if (source != null) {
                            {
                                try {
                                    val sAnime = SAnime.create().apply {
                                        url = s.anime.url
                                        title = s.anime.title
                                    }
                                    val url = source.getAnimeUrl(sAnime)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, url)
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                } catch (e: Exception) {
                                    context.toast(e.message)
                                }
                            }
                        } else {
                            null
                        }
                    },
                    onRefresh = screenModel::refreshEpisodes,
                    onDeleteClicked = {
                        screenModel.showDialog(AnimeScreenModel.Dialog.ConfirmDelete)
                    },
                    onDismissDialog = screenModel::dismissDialog,
                    onConfirmDelete = {
                        screenModel.deleteAnime()
                        navigator.pop()
                    },
                    onDownloadEpisode = { item -> screenModel.startDownload(item.episode) },
                    onDeleteEpisodeDownload = { item -> screenModel.deleteEpisodeDownload(item.episode) },
                    onConfirmDownloadQuality = screenModel::confirmDownload,
                    onUnseenFilterChanged = screenModel::setUnseenFilter,
                    onBookmarkedFilterChanged = screenModel::setBookmarkFilter,
                    onSortModeChanged = screenModel::setSortMode,
                    onDisplayModeChanged = screenModel::setDisplayMode,
                    onMultiBookmarkClicked = { episodes, bookmarked ->
                        screenModel.toggleBookmark(episodes, bookmarked)
                    },
                    onMultiMarkAsSeenClicked = { episodes, markAsSeen ->
                        screenModel.markEpisodesSeen(episodes, markAsSeen)
                    },
                    onMarkPreviousAsSeenClicked = screenModel::markPreviousAsSeen,
                    onMultiDeleteClicked = screenModel::deleteEpisodeDownloads,
                    onMultiDownloadClicked = screenModel::downloadEpisodes,
                    onEpisodeSelected = screenModel::toggleSelection,
                    onAllEpisodeSelected = screenModel::toggleAllSelection,
                    onInvertSelection = screenModel::invertSelection,
                )
            }
        }
    }
}
