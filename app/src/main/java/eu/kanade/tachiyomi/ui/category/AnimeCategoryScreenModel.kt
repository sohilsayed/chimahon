package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.category.interactor.CreateAnimeCategory
import tachiyomi.domain.category.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.category.interactor.HideAnimeCategory
import tachiyomi.domain.category.interactor.RenameAnimeCategory
import tachiyomi.domain.category.interactor.ReorderAnimeCategory
import tachiyomi.domain.category.model.AnimeCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeCategoryScreenModel(
    private val getAnimeCategories: GetAnimeCategories = Injekt.get(),
    private val createAnimeCategory: CreateAnimeCategory = Injekt.get(),
    private val deleteAnimeCategory: DeleteAnimeCategory = Injekt.get(),
    private val reorderAnimeCategory: ReorderAnimeCategory = Injekt.get(),
    private val renameAnimeCategory: RenameAnimeCategory = Injekt.get(),
    private val hideAnimeCategory: HideAnimeCategory = Injekt.get(),
) : StateScreenModel<AnimeCategoryScreenState>(AnimeCategoryScreenState.Loading) {

    private val _events: Channel<AnimeCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            getAnimeCategories.subscribe()
                .collectLatest { animeCategories ->
                    mutableState.update {
                        AnimeCategoryScreenState.Success(
                            categories = animeCategories
                                .filterNot(AnimeCategory::isSystemCategory)
                                .map { it.toCategory() }
                                .toImmutableList(),
                        )
                    }
                }
        }
    }

    fun createCategory(name: String) {
        screenModelScope.launch {
            when (createAnimeCategory.await(name)) {
                is CreateAnimeCategory.Result.InternalError -> _events.send(AnimeCategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun hideCategory(category: Category) {
        screenModelScope.launch {
            val animeCategory = AnimeCategory(
                id = category.id,
                name = category.name,
                order = category.order,
                flags = category.flags,
                hidden = category.hidden,
            )
            when (hideAnimeCategory.await(animeCategory)) {
                is HideAnimeCategory.Result.InternalError -> _events.send(AnimeCategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun deleteCategory(categoryId: Long) {
        screenModelScope.launch {
            when (deleteAnimeCategory.await(categoryId = categoryId)) {
                is DeleteAnimeCategory.Result.InternalError -> _events.send(AnimeCategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun changeOrder(category: Category, newIndex: Int) {
        screenModelScope.launch {
            val animeCategory = AnimeCategory(
                id = category.id,
                name = category.name,
                order = category.order,
                flags = category.flags,
                hidden = category.hidden,
            )
            when (reorderAnimeCategory.await(animeCategory, newIndex)) {
                is ReorderAnimeCategory.Result.InternalError -> _events.send(AnimeCategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun renameCategory(category: Category, name: String) {
        screenModelScope.launch {
            when (renameAnimeCategory.await(category.id, name)) {
                is RenameAnimeCategory.Result.InternalError -> _events.send(AnimeCategoryEvent.InternalError)
                else -> {}
            }
        }
    }

    fun showDialog(dialog: AnimeCategoryDialog) {
        mutableState.update {
            when (it) {
                AnimeCategoryScreenState.Loading -> it
                is AnimeCategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                AnimeCategoryScreenState.Loading -> it
                is AnimeCategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }

    private fun AnimeCategory.toCategory() = Category(
        id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = hidden,
    )
}

sealed interface AnimeCategoryDialog {
    data object Create : AnimeCategoryDialog
    data class Rename(val category: Category) : AnimeCategoryDialog
    data class Delete(val category: Category) : AnimeCategoryDialog
}

sealed interface AnimeCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : AnimeCategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface AnimeCategoryScreenState {

    @Immutable
    data object Loading : AnimeCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<Category>,
        val dialog: AnimeCategoryDialog? = null,
    ) : AnimeCategoryScreenState {

        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
