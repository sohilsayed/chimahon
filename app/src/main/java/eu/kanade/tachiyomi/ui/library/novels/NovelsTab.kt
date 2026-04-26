package eu.kanade.tachiyomi.ui.library.novels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.canopus.chimareader.ui.library.BookshelfScreen
import eu.kanade.presentation.util.Tab
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object NovelsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 5u,
                title = stringResource(MR.strings.label_novels),
                icon = rememberVectorPainter(Icons.Outlined.Book),
            )
        }

    @Composable
    override fun Content() {
        val context = androidx.compose.ui.platform.LocalContext.current
        BookshelfScreen(
            onSyncRequest = {
                eu.kanade.tachiyomi.data.sync.SyncDataJob.startNow(context, manual = true)
                true
            }
        )
    }
}
