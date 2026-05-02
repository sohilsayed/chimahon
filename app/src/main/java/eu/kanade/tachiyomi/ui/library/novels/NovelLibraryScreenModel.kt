package eu.kanade.tachiyomi.ui.library.novels

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.NovelCategory
import com.canopus.chimareader.data.NovelCategoryStorage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelLibraryScreenModel(
    private val context: Context = Injekt.get(),
    private val categoryStorage: NovelCategoryStorage = Injekt.get(),
) : StateScreenModel<NovelLibraryScreenModel.State>(State()) {

    init {
        loadLibrary()
    }

    fun loadLibrary() {
        screenModelScope.launch {
            val categories = categoryStorage.loadAllCategories()
            val books = BookStorage.loadAllBooks(context)
            
            mutableState.update { 
                it.copy(
                    isLoading = false,
                    categories = categories.toImmutableList(),
                    books = books.toImmutableList()
                )
            }
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun updateActiveCategoryIndex(index: Int) {
        mutableState.update { it.copy(activeCategoryIndex = index) }
    }

    fun toggleSelection(bookId: String) {
        mutableState.update { state ->
            val selection = state.selection.toMutableList()
            if (selection.contains(bookId)) {
                selection.remove(bookId)
            } else {
                selection.add(bookId)
            }
            state.copy(selection = selection.toImmutableList())
        }
    }

    fun clearSelection() {
        mutableState.update { it.copy(selection = persistentListOf()) }
    }

    fun selectAll() {
        mutableState.update { state ->
            state.copy(selection = state.books.map { it.id }.toImmutableList())
        }
    }

    fun invertSelection() {
        mutableState.update { state ->
            val allIds = state.books.map { it.id }.toSet()
            val newSelection = allIds.minus(state.selection).toList().toImmutableList()
            state.copy(selection = newSelection)
        }
    }

    fun deleteSelected() {
        screenModelScope.launch {
            val state = mutableState.value
            state.selection.forEach { bookId ->
                BookStorage.deleteBook(context, bookId)
            }
            clearSelection()
            loadLibrary()
        }
    }

    fun moveSelectedToCategory(categoryId: String) {
        screenModelScope.launch {
            val state = mutableState.value
            state.selection.forEach { bookId ->
                val bookDir = BookStorage.getBookDirectory(context, bookId)
                val metadata = BookStorage.loadMetadata(bookDir)
                if (metadata != null) {
                    val updatedMetadata = metadata.copy(categoryIds = listOf(categoryId))
                    BookStorage.saveMetadata(updatedMetadata, bookDir)
                }
            }
            clearSelection()
            loadLibrary()
        }
    }

    fun resetStatsForSelected() {
        screenModelScope.launch {
            val state = mutableState.value
            state.selection.forEach { bookId ->
                val bookDir = BookStorage.getBookDirectory(context, bookId)
                // Delete statistics file — BookStorage.save will recreate it fresh on next read
                val statsFile = java.io.File(bookDir, com.canopus.chimareader.data.FileNames.statistics)
                if (statsFile.exists()) statsFile.delete()
                // Also delete bookmark so reading position resets
                val bookmarkFile = java.io.File(bookDir, com.canopus.chimareader.data.FileNames.bookmark)
                if (bookmarkFile.exists()) bookmarkFile.delete()
            }
            clearSelection()
        }
    }

    fun showChangeCategoryDialog() {
        mutableState.update { it.copy(dialog = Dialog.ChangeCategory) }
    }

    fun showDeleteConfirmDialog() {
        mutableState.update { it.copy(dialog = Dialog.DeleteConfirm) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    enum class SortMode {
        Alphabetical, DateAdded, LastRead
    }

    sealed interface Dialog {
        data object ChangeCategory : Dialog
        data object DeleteConfirm : Dialog
        data object SortFilter : Dialog
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val categories: ImmutableList<NovelCategory> = persistentListOf(),
        val books: ImmutableList<BookMetadata> = persistentListOf(),
        val searchQuery: String? = null,
        val selection: ImmutableList<String> = persistentListOf(),
        val activeCategoryIndex: Int = 0,
        val dialog: Dialog? = null,
        val sortMode: SortMode = SortMode.DateAdded,
        val sortDescending: Boolean = true,
    ) {
        val hasActiveFilters: Boolean = false
        val isLibraryEmpty: Boolean = books.isEmpty()
        val selectionMode: Boolean = selection.isNotEmpty()
        
        val activeCategory: NovelCategory?
            get() = categories.getOrNull(activeCategoryIndex)

        fun getBooksForCategory(category: NovelCategory): List<BookMetadata> {
            val filteredBooks = if (searchQuery.isNullOrBlank()) {
                books
            } else {
                books.filter { it.title?.contains(searchQuery, ignoreCase = true) == true }
            }
            
            val categoryBooks = filteredBooks.filter { it.categoryIds.contains(category.id) }
            
            val comparator = when (sortMode) {
                SortMode.Alphabetical -> compareBy<BookMetadata>(String.CASE_INSENSITIVE_ORDER) { it.title ?: "" }
                SortMode.DateAdded -> compareBy { it.dateAdded }
                SortMode.LastRead -> compareBy { it.lastAccess }
            }

            return if (sortDescending) {
                categoryBooks.sortedWith(comparator.reversed())
            } else {
                categoryBooks.sortedWith(comparator)
            }
        }

        fun getItemCountForCategory(category: NovelCategory): Int {
            return getBooksForCategory(category).size
        }
    }
    
    fun setSort(mode: SortMode, descending: Boolean) {
        mutableState.update { it.copy(sortMode = mode, sortDescending = descending) }
    }
    
    fun showSortDialog() {
        mutableState.update { it.copy(dialog = Dialog.SortFilter) }
    }
}
