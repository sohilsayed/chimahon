package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoConfirmDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoConflictDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoCreateDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoDeleteDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionReposScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.presentation.core.screens.LoadingScreen

class AnimeExtensionReposScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { AnimeExtensionReposScreenModel() }
        val state by screenModel.state.collectAsState()

        if (state is AnimeRepoScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as AnimeRepoScreenState.Success

        ExtensionReposScreen(
            state = RepoScreenState.Success(
                repos = successState.repos,
            ),
            onClickCreate = { screenModel.showDialog(AnimeRepoDialog.Create) },
            onOpenWebsite = { context.openInBrowser(it.website) },
            onClickDelete = { screenModel.showDialog(AnimeRepoDialog.Delete(it)) },
            onClickEnable = {},
            onClickDisable = {},
            onClickRefresh = { screenModel.refreshRepos() },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            is AnimeRepoDialog.Create -> {
                ExtensionRepoCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(it) },
                    repoUrls = successState.repos.map { it.baseUrl }.toImmutableSet(),
                )
            }
            is AnimeRepoDialog.Delete -> {
                ExtensionRepoDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteRepo(dialog.repo) },
                    repo = dialog.repo,
                )
            }
            is AnimeRepoDialog.Conflict -> {
                ExtensionRepoConflictDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onMigrate = { screenModel.replaceRepo(dialog.newRepo) },
                    oldRepo = dialog.oldRepo,
                    newRepo = dialog.newRepo,
                )
            }
            is AnimeRepoDialog.Confirm -> {
                ExtensionRepoConfirmDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(dialog.url) },
                    repo = dialog.url,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is AnimeRepoEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
