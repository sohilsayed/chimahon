package eu.kanade.tachiyomi.ui.library.novels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import kotlinx.coroutines.channels.Channel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object NovelsTab : Tab {

    @Suppress("unused")
    private fun readResolve(): Any = NovelsTab

    private val requestSortEvent = Channel<Unit>(Channel.BUFFERED)

    private suspend fun requestOpenSort() = requestSortEvent.send(Unit)

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 5u,
            title = stringResource(MR.strings.label_novels),
            icon = rememberVectorPainter(Icons.Outlined.Book),
        )

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSort()
    }

    @Composable
    override fun Content() {
        NovelLibraryScreen(requestSortEvent = requestSortEvent)
    }
}
