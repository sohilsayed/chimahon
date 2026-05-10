package eu.kanade.tachiyomi.ui.browse.animesource.globalsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.LoadingScreen

class GlobalAnimeSearchScreen(
    val searchQuery: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel {
            GlobalAnimeSearchScreenModel(initialQuery = searchQuery)
        }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Anime Search: ${state.searchQuery.orEmpty()}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = navigator::pop) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                    if (state.progress in 1..<state.total) {
                        LinearProgressIndicator(
                            progress = { state.progress / state.total.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
        ) { contentPadding ->
            if (state.items.isEmpty()) {
                LoadingScreen(modifier = Modifier.padding(contentPadding))
            } else {
                LazyColumn(contentPadding = contentPadding) {
                    state.items.forEach { (source, result) ->
                        item(key = source.id) {
                            AnimeSearchSourceResult(
                                source = source,
                                result = result,
                                onAnimeClicked = { sAnime ->
                                    screenModel.addAnimeToDatabase(sAnime, source.id) { animeId ->
                                        navigator.push(AnimeScreen(animeId))
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeSearchSourceResult(
    source: AnimeCatalogueSource,
    result: AnimeSearchItemResult,
    onAnimeClicked: (SAnime) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.titleSmall,
            )
        }

        when (result) {
            AnimeSearchItemResult.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is AnimeSearchItemResult.Error -> {
                Text(
                    text = result.throwable.message ?: "Error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                )
            }
            is AnimeSearchItemResult.Success -> {
                if (result.isEmpty) {
                    Text(
                        text = "No results",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        ),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = result.result,
                            key = { it.url },
                        ) { anime ->
                            AnimeSearchCard(
                                anime = anime,
                                onClick = { onAnimeClicked(anime) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeSearchCard(
    anime: SAnime,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        AsyncImage(
            model = anime.thumbnail_url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp)),
        )
        Text(
            text = anime.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
