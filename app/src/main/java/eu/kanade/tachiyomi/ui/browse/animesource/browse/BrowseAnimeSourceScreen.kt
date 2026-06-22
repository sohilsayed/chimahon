package eu.kanade.tachiyomi.ui.browse.animesource.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.entries.anime.model.titleOrUrl
import eu.kanade.presentation.library.components.AnimeComfortableGridItem
import eu.kanade.presentation.library.components.CommonAnimeItemDefaults
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.browse.animesource.browse.BrowseAnimeSourceScreenModel.Listing
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import tachiyomi.domain.entries.anime.model.AnimeCover as DomainAnimeCover

class BrowseAnimeSourceScreen(
    private val sourceId: Long,
    private val sourceName: String,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { BrowseAnimeSourceScreenModel(sourceId) }
        val state by screenModel.state.collectAsState()

        var searchQuery by rememberSaveable { mutableStateOf("") }
        var showSearch by rememberSaveable { mutableStateOf(false) }
        var showFilterDialog by rememberSaveable { mutableStateOf(false) }

        fun openAnime(anime: SAnime) {
            screenModel.addAnimeToDatabase(anime) { animeId ->
                navigator.push(AnimeScreen(animeId))
            }
        }

        fun closeSearch() {
            showSearch = false
            searchQuery = ""
            if (state.listing == Listing.Search) {
                screenModel.loadPopular()
            }
        }

        BackHandler(enabled = showSearch) {
            closeSearch()
        }

        LaunchedEffect(sourceId) {
            screenModel.loadFeed()
        }

        Scaffold(
            topBar = { scrollBehavior ->
                eu.kanade.presentation.components.AppBar(
                    title = sourceName,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = {
                                if (showSearch) {
                                    closeSearch()
                                } else {
                                    showSearch = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (showSearch) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = stringResource(MR.strings.action_search),
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(modifier = Modifier.padding(contentPadding)) {
                if (showSearch) {
                    SearchHeader(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = { screenModel.search(searchQuery, state.filters) },
                    )
                } else {
                    AnimeSourceBrowseChips(
                        listing = state.listing,
                        supportsLatest = state.supportsLatest,
                        hasFilters = state.hasFilters,
                        onPopularClick = {
                            searchQuery = ""
                            screenModel.loadPopular()
                        },
                        onLatestClick = {
                            searchQuery = ""
                            screenModel.loadLatest()
                        },
                        onFilterClick = { showFilterDialog = true },
                    )
                }

                when {
                    state.isLoading -> LoadingScreen()
                    state.error -> EmptyScreen(MR.strings.internal_error)
                    state.listing == Listing.Feed -> AnimeSourceFeed(
                        state = state,
                        sourceId = sourceId,
                        onClickLatest = { screenModel.loadLatest() },
                        onClickBrowse = { screenModel.loadPopular() },
                        onClickAnime = ::openAnime,
                    )
                    state.items.isEmpty() -> EmptyScreen(MR.strings.no_results_found)
                    else -> AnimeSourceGrid(
                        sourceId = sourceId,
                        items = state.items,
                        hasNextPage = state.hasNextPage,
                        onLoadNextPage = screenModel::loadNextPage,
                        onClickAnime = ::openAnime,
                    )
                }
            }
        }

        if (showFilterDialog) {
            AnimeSourceFilterDialog(
                onDismissRequest = { showFilterDialog = false },
                filters = state.filters,
                onReset = screenModel::resetFilters,
                onFilter = {
                    showSearch = false
                    searchQuery = ""
                    screenModel.applyFilters()
                },
                onUpdate = screenModel::setFilters,
            )
        }
    }
}

@Composable
private fun AnimeSourceBrowseChips(
    listing: Listing,
    supportsLatest: Boolean,
    hasFilters: Boolean,
    onPopularClick: () -> Unit,
    onLatestClick: () -> Unit,
    onFilterClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.padding.small, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        FilterChip(
            selected = listing == Listing.Popular,
            onClick = onPopularClick,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            label = { Text(stringResource(MR.strings.popular)) },
        )
        if (supportsLatest) {
            FilterChip(
                selected = listing == Listing.Latest,
                onClick = onLatestClick,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.NewReleases,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                label = { Text(stringResource(MR.strings.latest)) },
            )
        }
        if (hasFilters) {
            FilterChip(
                selected = listing == Listing.Search,
                onClick = onFilterClick,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                label = { Text(stringResource(MR.strings.action_filter)) },
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
        )
        IconButton(
            onClick = onSearch,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = stringResource(MR.strings.action_search),
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun AnimeSourceFeed(
    state: BrowseAnimeSourceScreenModel.State,
    sourceId: Long,
    onClickLatest: () -> Unit,
    onClickBrowse: () -> Unit,
    onClickAnime: (SAnime) -> Unit,
) {
    if (state.latestItems.isEmpty() && state.popularItems.isEmpty()) {
        EmptyScreen(MR.strings.no_results_found)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        if (state.latestItems.isNotEmpty()) {
            item(key = "latest") {
                AnimeSourceRail(
                    title = stringResource(MR.strings.latest),
                    sourceId = sourceId,
                    items = state.latestItems,
                    onClickMore = onClickLatest,
                    onClickAnime = onClickAnime,
                )
            }
        }
        if (state.popularItems.isNotEmpty()) {
            item(key = "browse") {
                AnimeSourceRail(
                    title = stringResource(MR.strings.browse),
                    sourceId = sourceId,
                    items = state.popularItems,
                    onClickMore = onClickBrowse,
                    onClickAnime = onClickAnime,
                )
            }
        }
    }
}

@Composable
private fun AnimeSourceRail(
    title: String,
    sourceId: Long,
    items: List<SAnime>,
    onClickMore: () -> Unit,
    onClickAnime: (SAnime) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClickMore) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = title,
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items.take(20), key = { it.url }) { anime ->
                Box(modifier = Modifier.width(96.dp)) {
                    BrowseAnimeSourceGridItem(
                        sourceId = sourceId,
                        anime = anime,
                        titleMaxLines = 3,
                        onClick = { onClickAnime(anime) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeSourceGrid(
    sourceId: Long,
    items: List<SAnime>,
    hasNextPage: Boolean,
    onLoadNextPage: () -> Unit,
    onClickAnime: (SAnime) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(128.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp) +
            PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(CommonAnimeItemDefaults.GridHorizontalSpacer),
        verticalArrangement = Arrangement.spacedBy(CommonAnimeItemDefaults.GridVerticalSpacer),
    ) {
        gridItems(items, key = { it.url }) { anime ->
            BrowseAnimeSourceGridItem(
                sourceId = sourceId,
                anime = anime,
                onClick = { onClickAnime(anime) },
            )
        }

        if (hasNextPage) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(Unit) {
                    onLoadNextPage()
                }
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(MaterialTheme.padding.medium)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BrowseAnimeSourceGridItem(
    sourceId: Long,
    anime: SAnime,
    titleMaxLines: Int = 2,
    onClick: () -> Unit,
) {
    AnimeComfortableGridItem(
        title = anime.titleOrUrl(),
        titleMaxLines = titleMaxLines,
        coverData = DomainAnimeCover(
            animeId = stableBrowseAnimeId(sourceId, anime),
            sourceId = sourceId,
            isAnimeFavorite = false,
            ogUrl = anime.thumbnail_url,
            lastModified = 0L,
        ),
        onLongClick = onClick,
        onClick = onClick,
    )
}

private fun stableBrowseAnimeId(sourceId: Long, anime: SAnime): Long {
    return 31L * sourceId + anime.url.hashCode().toLong()
}
