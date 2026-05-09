package eu.kanade.tachiyomi.ui.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.anime.AnimeScreenContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeScreen(
    private val animeId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { AnimeScreenModel(animeId = animeId) }
        val state by screenModel.state.collectAsState()

        when (val s = state) {
            is AnimeScreenModel.State.Loading -> {
                LoadingScreen()
            }
            is AnimeScreenModel.State.Success -> {
                AnimeScreenContent(
                    state = s,
                    navigateUp = navigator::pop,
                    onEpisodeClicked = { episode ->
                        context.startActivity(
                            PlayerActivity.newIntent(context, animeId, episode.id),
                        )
                    },
                    onContinueWatching = {
                        val episode = s.nextUnseenEpisode ?: return@AnimeScreenContent
                        context.startActivity(
                            PlayerActivity.newIntent(context, animeId, episode.id),
                        )
                    },
                    onToggleFavorite = screenModel::toggleFavorite,
                    onDeleteClicked = {
                        screenModel.showDialog(AnimeScreenModel.Dialog.ConfirmDelete)
                    },
                    onDismissDialog = screenModel::dismissDialog,
                    onConfirmDelete = {
                        screenModel.deleteAnime()
                        navigator.pop()
                    },
                    onDownloadEpisode = screenModel::startDownload,
                    onDeleteEpisodeDownload = screenModel::deleteEpisodeDownload,
                    onConfirmDownloadQuality = screenModel::confirmDownload,
                )
            }
        }
    }
}
