package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.preference.asState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connections.discord.DiscordScreen
import eu.kanade.tachiyomi.ui.browse.animeextension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.animeextension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.animesource.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.extensionsTab
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.feedTab
import eu.kanade.tachiyomi.ui.browse.migration.sources.migrateSourceTab
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.browse.source.sourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object BrowseTab : Tab {
    private fun readResolve(): Any = BrowseTab

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalSearchScreen())
    }

    private val switchToExtensionTabChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToExtensionTabChannel.trySend(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        // SY -->
        val hideFeedTab by remember { Injekt.get<UiPreferences>().hideFeedTab().asState(scope) }
        val feedTabInFront by remember { Injekt.get<UiPreferences>().feedTabInFront().asState(scope) }
        // SY <--

        // Hoisted for extensions tab's search bar
        val extensionsScreenModel = rememberScreenModel { ExtensionsScreenModel() }
        val extensionsState by extensionsScreenModel.state.collectAsState()

        val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
        val animeExtensionsState by animeExtensionsScreenModel.state.collectAsState()

        // KMK -->
        val feedScreenModel = rememberScreenModel { FeedScreenModel() }
        val bulkFavoriteScreenModel = rememberScreenModel { BulkFavoriteScreenModel() }
        // KMK <--

        // SY -->
        val tabs = when {
            hideFeedTab ->
                persistentListOf(
                    sourcesTab(),
                    extensionsTab(extensionsScreenModel),
                    animeSourcesTab(),
                    animeExtensionsTab(animeExtensionsScreenModel),
                    migrateSourceTab(),
                )

            feedTabInFront ->
                persistentListOf(
                    feedTab(
                        // KMK -->
                        feedScreenModel,
                        bulkFavoriteScreenModel,
                        // KMK <--
                    ),
                    sourcesTab(),
                    extensionsTab(extensionsScreenModel),
                    animeSourcesTab(),
                    animeExtensionsTab(animeExtensionsScreenModel),
                    migrateSourceTab(),
                )

            else ->
                persistentListOf(
                    sourcesTab(),
                    feedTab(
                        // KMK -->
                        feedScreenModel,
                        bulkFavoriteScreenModel,
                        // KMK <--
                    ),
                    extensionsTab(extensionsScreenModel),
                    animeSourcesTab(),
                    animeExtensionsTab(animeExtensionsScreenModel),
                    migrateSourceTab(),
                )
        }
        // SY <--

        val state = rememberPagerState { tabs.size }

        val currentTabTitleRes = tabs.getOrNull(state.currentPage)?.titleRes
        val searchQuery = when (currentTabTitleRes) {
            MR.strings.label_anime_extensions -> animeExtensionsState.searchQuery
            MR.strings.label_extensions -> extensionsState.searchQuery
            else -> null
        }
        val onChangeSearchQuery: (String?) -> Unit = when (currentTabTitleRes) {
            MR.strings.label_anime_extensions -> animeExtensionsScreenModel::search
            MR.strings.label_extensions -> extensionsScreenModel::search
            else -> { _ -> }
        }
        TabbedScreen(
            titleRes = MR.strings.browse,
            tabs = tabs,
            state = state,
            searchQuery = searchQuery,
            onChangeSearchQuery = onChangeSearchQuery,
            // KMK -->
            feedScreenModel = feedScreenModel,
            bulkFavoriteScreenModel = bulkFavoriteScreenModel,
            // KMK <--
        )
        LaunchedEffect(Unit) {
            switchToExtensionTabChannel.receiveAsFlow()
                .collectLatest {
                    val extensionsIndex = tabs.indexOfFirst { tab ->
                        tab.titleRes == MR.strings.label_extensions
                    }
                    if (extensionsIndex >= 0) {
                        state.scrollToPage(extensionsIndex)
                    }
                }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true

            // AM (DISCORD) -->
            with(DiscordRPCService) {
                discordScope.launchIO { setScreen(context, DiscordScreen.BROWSE) }
            }
            // <-- AM (DISCORD)
        }
    }
}
