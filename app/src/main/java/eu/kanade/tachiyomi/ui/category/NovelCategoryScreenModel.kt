package eu.kanade.tachiyomi.ui.category

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.canopus.chimareader.data.NovelCategory
import com.canopus.chimareader.data.NovelCategoryStorage
import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCategoryScreenModel(
    private val categoryStorage: NovelCategoryStorage = Injekt.get(),
) : StateScreenModel<NovelCategoryScreenState>(NovelCategoryScreenState.Loading) {

    private val _events: Channel<NovelCategoryEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        screenModelScope.launch {
            val categories = categoryStorage.loadAllCategories()
            mutableState.update {
                NovelCategoryScreenState.Success(
                    categories = categories.toImmutableList(),
                )
            }
        }
    }

    fun createCategory(name: String) {
        screenModelScope.launch {
            categoryStorage.createCategory(name)
            loadCategories()
        }
    }

    fun deleteCategory(category: NovelCategory) {
        if (category.isSystemCategory) return
        screenModelScope.launch {
            categoryStorage.deleteCategory(category.id)
            loadCategories()
        }
    }

    fun renameCategory(category: NovelCategory, name: String) {
        if (category.isSystemCategory) return
        screenModelScope.launch {
            val categories = categoryStorage.loadAllCategories()
            val updated = categories.map {
                if (it.id == category.id) it.copy(name = name) else it
            }
            categoryStorage.saveCategories(updated)
            loadCategories()
        }
    }

    fun reorderCategory(category: NovelCategory, newIndex: Int) {
        screenModelScope.launch {
            val categories = categoryStorage.loadAllCategories().toMutableList()
            val oldIndex = categories.indexOfFirst { it.id == category.id }
            if (oldIndex != -1) {
                val item = categories.removeAt(oldIndex)
                categories.add(newIndex, item)
                // Update order values
                val reordered = categories.mapIndexed { index, cat ->
                    cat.copy(order = index)
                }
                categoryStorage.saveCategories(reordered)
                loadCategories()
            }
        }
    }

    fun showDialog(dialog: NovelCategoryDialog) {
        mutableState.update {
            when (it) {
                NovelCategoryScreenState.Loading -> it
                is NovelCategoryScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                NovelCategoryScreenState.Loading -> it
                is NovelCategoryScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface NovelCategoryDialog {
    data object Create : NovelCategoryDialog
    data class Rename(val category: NovelCategory) : NovelCategoryDialog
    data class Delete(val category: NovelCategory) : NovelCategoryDialog
}

sealed interface NovelCategoryEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : NovelCategoryEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface NovelCategoryScreenState {
    @Immutable
    data object Loading : NovelCategoryScreenState

    @Immutable
    data class Success(
        val categories: ImmutableList<NovelCategory>,
        val dialog: NovelCategoryDialog? = null,
    ) : NovelCategoryScreenState {
        val isEmpty: Boolean
            get() = categories.isEmpty()
    }
}
