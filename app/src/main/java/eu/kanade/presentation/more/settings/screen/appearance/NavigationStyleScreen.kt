package eu.kanade.presentation.more.settings.screen.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.preference.asState
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavSection
import eu.kanade.domain.ui.model.NavTabEntry
import eu.kanade.domain.ui.model.NavTabLayout
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NavigationStyleScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val scope = rememberCoroutineScope()

        val navTabLayoutPref = uiPreferences.navTabLayout()
        val navTabLayoutStr by navTabLayoutPref.collectAsState()
        val navStartScreenPref = uiPreferences.navStartScreen()

        // Resolve tab titles
        val resolvedTabTitles = mapOf(
            NavTabLayout.KEY_LIBRARY to stringResource(MR.strings.label_library),
            NavTabLayout.KEY_UPDATES to stringResource(MR.strings.label_recent_updates),
            NavTabLayout.KEY_HISTORY to stringResource(MR.strings.label_recent_manga),
            NavTabLayout.KEY_BROWSE to stringResource(MR.strings.browse),
            NavTabLayout.KEY_DICTIONARY to stringResource(MR.strings.label_dictionary),
            NavTabLayout.KEY_NOVELS to stringResource(MR.strings.label_novels),
        )

        // Section titles
        val navbarTitle = stringResource(SYMR.strings.pref_nav_section_navbar)
        val moreTitle = stringResource(SYMR.strings.pref_nav_section_more)
        val disabledTitle = stringResource(SYMR.strings.pref_nav_section_disabled)


        // Build flat list: headers + tab entries
        val flatItems = remember(navTabLayoutStr) {
            val layout = NavTabLayout.parse(navTabLayoutStr)
            buildFlatList(layout, resolvedTabTitles, navbarTitle, moreTitle, disabledTitle)
        }

        val listState = remember { flatItems.toMutableStateList() }

        // Sync when preference changes externally
        LaunchedEffect(flatItems) {
            listState.clear()
            listState.addAll(flatItems)
        }

        val lazyListState = rememberLazyListState()

        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            if (from.index in listState.indices && to.index in listState.indices) {
                listState.apply {
                    add(to.index, removeAt(from.index))
                }
            }
        }

        // Persist when drag ends
        LaunchedEffect(reorderableState) {
            snapshotFlow { reorderableState.isAnyItemDragging }
                .drop(1) // skip initial value
                .collectLatest { isDragging ->
                    if (!isDragging) {
                        val newLayout = rebuildLayoutFromFlatList(listState)
                        navTabLayoutPref.set(newLayout.serialize())

                        // Auto-fix start screen
                        val navbarKeys = newLayout.getKeysForSection(NavSection.NAVBAR)
                        val currentStart = navStartScreenPref.get()
                        if (currentStart !in navbarKeys && navbarKeys.isNotEmpty()) {
                            navStartScreenPref.set(navbarKeys.first())
                        }
                    }
                }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(SYMR.strings.pref_navigation_style),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                // Single flat reorderable list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    itemsIndexed(
                        items = listState,
                        key = { _, item ->
                            when (item) {
                                is NavListItem.Header -> "header_${item.section.name}"
                                is NavListItem.TabEntry -> "tab_${item.key}"
                            }
                        },
                    ) { _, item ->
                        when (item) {
                            is NavListItem.Header -> {
                                ReorderableItem(reorderableState, key = "header_${item.section.name}") {
                                    SectionHeader(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                    )
                                }
                            }
                            is NavListItem.TabEntry -> {
                                ReorderableItem(reorderableState, key = "tab_${item.key}") {
                                    DraggableNavTabListItem(
                                        entry = item,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }

                    item(key = "footer_spacer") {
                        ReorderableItem(reorderableState, key = "footer_spacer") {
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(vertical = 120.dp))
                        }
                    }
                }
            }
        }
    }
}

// region Data

sealed class NavListItem {
    data class Header(
        val section: NavSection,
        val title: String,
        val subtitle: String? = null,
    ) : NavListItem()

    data class TabEntry(
        val key: String,
        val title: String,
        val icon: ImageVector,
    ) : NavListItem()
}

private fun getTabIcon(key: String): ImageVector {
    return when (key) {
        NavTabLayout.KEY_LIBRARY -> Icons.Outlined.CollectionsBookmark
        NavTabLayout.KEY_UPDATES -> Icons.Outlined.NewReleases
        NavTabLayout.KEY_HISTORY -> Icons.Outlined.History
        NavTabLayout.KEY_BROWSE -> Icons.Outlined.Public
        NavTabLayout.KEY_DICTIONARY -> Icons.Outlined.Search
        NavTabLayout.KEY_NOVELS -> Icons.Outlined.Book
        else -> Icons.Outlined.Public
    }
}

// endregion

// region Build / Rebuild

private fun buildFlatList(
    layout: NavTabLayout,
    tabTitles: Map<String, String>,
    navbarTitle: String,
    moreTitle: String,
    disabledTitle: String,
): List<NavListItem> {
    val items = mutableListOf<NavListItem>()

    items.add(NavListItem.Header(NavSection.NAVBAR, navbarTitle))
    layout.entries.filter { it.section == NavSection.NAVBAR }.forEach {
        items.add(NavListItem.TabEntry(it.key, tabTitles[it.key] ?: it.key, getTabIcon(it.key)))
    }

    items.add(NavListItem.Header(NavSection.MORE, moreTitle))
    layout.entries.filter { it.section == NavSection.MORE }.forEach {
        items.add(NavListItem.TabEntry(it.key, tabTitles[it.key] ?: it.key, getTabIcon(it.key)))
    }

    items.add(NavListItem.Header(NavSection.DISABLED, disabledTitle))
    layout.entries.filter { it.section == NavSection.DISABLED }.forEach {
        items.add(NavListItem.TabEntry(it.key, tabTitles[it.key] ?: it.key, getTabIcon(it.key)))
    }

    return items
}

private fun rebuildLayoutFromFlatList(items: List<NavListItem>): NavTabLayout {
    var currentSection = NavSection.NAVBAR
    val entries = mutableListOf<NavTabEntry>()

    for (item in items) {
        when (item) {
            is NavListItem.Header -> currentSection = item.section
            is NavListItem.TabEntry -> entries.add(NavTabEntry(item.key, currentSection))
        }
    }

    return NavTabLayout(entries)
}

// endregion

// region Components

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            )
            .padding(top = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun sh.calvin.reorderable.ReorderableCollectionItemScope.DraggableNavTabListItem(
    entry: NavListItem.TabEntry,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.medium,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .padding(MaterialTheme.padding.small)
                    .draggableHandle(),
            )
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                modifier = Modifier.padding(end = MaterialTheme.padding.medium),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = MaterialTheme.padding.medium),
            )
        }
    }
}

// endregion
