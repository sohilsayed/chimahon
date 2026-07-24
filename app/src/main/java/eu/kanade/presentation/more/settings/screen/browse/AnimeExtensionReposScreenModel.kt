package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.animeextension.AnimeExtensionManager
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import mihon.domain.animeextensionrepo.interactor.CreateAnimeExtensionRepo
import mihon.domain.animeextensionrepo.interactor.DeleteAnimeExtensionRepo
import mihon.domain.animeextensionrepo.interactor.GetAnimeExtensionRepo
import mihon.domain.animeextensionrepo.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.animeextensionrepo.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionReposScreenModel(
    private val getAnimeExtensionRepo: GetAnimeExtensionRepo = Injekt.get(),
    private val createAnimeExtensionRepo: CreateAnimeExtensionRepo = Injekt.get(),
    private val deleteAnimeExtensionRepo: DeleteAnimeExtensionRepo = Injekt.get(),
    private val replaceAnimeExtensionRepo: ReplaceAnimeExtensionRepo = Injekt.get(),
    private val updateAnimeExtensionRepo: UpdateAnimeExtensionRepo = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
) : StateScreenModel<AnimeRepoScreenState>(AnimeRepoScreenState.Loading) {

    private val _events: Channel<AnimeRepoEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getAnimeExtensionRepo.subscribeAll()
                .collectLatest { repos ->
                    mutableState.update {
                        AnimeRepoScreenState.Success(repos = repos.toImmutableSet())
                    }
                }
        }
    }

    fun createRepo(baseUrl: String) {
        screenModelScope.launchIO {
            when (val result = createAnimeExtensionRepo.await(baseUrl)) {
                CreateAnimeExtensionRepo.Result.Success -> animeExtensionManager.findAvailableExtensions()
                CreateAnimeExtensionRepo.Result.InvalidUrl -> _events.send(AnimeRepoEvent.InvalidUrl)
                CreateAnimeExtensionRepo.Result.RepoAlreadyExists -> _events.send(AnimeRepoEvent.RepoAlreadyExists)
                is CreateAnimeExtensionRepo.Result.DuplicateFingerprint -> {
                    showDialog(AnimeRepoDialog.Conflict(result.oldRepo, result.newRepo))
                }
                else -> {}
            }
        }
    }

    fun replaceRepo(newRepo: ExtensionRepo) {
        screenModelScope.launchIO {
            replaceAnimeExtensionRepo.await(newRepo)
        }
    }

    fun refreshRepos() {
        val status = state.value
        if (status is AnimeRepoScreenState.Success) {
            screenModelScope.launchIO {
                updateAnimeExtensionRepo.awaitAll()
            }
        }
    }

    fun deleteRepo(baseUrl: String) {
        screenModelScope.launchIO {
            deleteAnimeExtensionRepo.await(baseUrl)
            animeExtensionManager.findAvailableExtensions()
        }
    }

    fun showDialog(dialog: AnimeRepoDialog) {
        mutableState.update {
            when (it) {
                AnimeRepoScreenState.Loading -> it
                is AnimeRepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                AnimeRepoScreenState.Loading -> it
                is AnimeRepoScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class AnimeRepoEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : AnimeRepoEvent()
    data object InvalidUrl : LocalizedMessage(MR.strings.invalid_repo_name)
    data object RepoAlreadyExists : LocalizedMessage(MR.strings.error_repo_exists)
}

sealed class AnimeRepoDialog {
    data object Create : AnimeRepoDialog()
    data class Delete(val repo: String) : AnimeRepoDialog()
    data class Conflict(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : AnimeRepoDialog()
    data class Confirm(val url: String) : AnimeRepoDialog()
}

sealed class AnimeRepoScreenState {

    @Immutable
    data object Loading : AnimeRepoScreenState()

    @Immutable
    data class Success(
        val repos: ImmutableSet<ExtensionRepo>,
        val dialog: AnimeRepoDialog? = null,
    ) : AnimeRepoScreenState() {
        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
