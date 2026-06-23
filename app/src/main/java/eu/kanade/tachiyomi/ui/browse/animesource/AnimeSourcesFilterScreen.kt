package eu.kanade.tachiyomi.ui.browse.animesource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.AnimeSourcesFilterScreen
import eu.kanade.presentation.util.Screen
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeSourcesFilterScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AnimeSourcesFilterScreenModel() }
        val state by screenModel.state.collectAsState()

        if (state is AnimeSourcesFilterScreenModel.State.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeSourcesFilterScreenModel.State.Success

        AnimeSourcesFilterScreen(
            navigateUp = navigator::pop,
            state = successState,
            onClickLanguage = screenModel::toggleLanguage,
            onClickSource = screenModel::toggleSource,
        )
    }
}
