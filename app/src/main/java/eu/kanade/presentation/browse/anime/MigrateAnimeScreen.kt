package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.anime.components.BaseAnimeListItem
import eu.kanade.tachiyomi.ui.browse.animemigration.anime.MigrateAnimeScreenModel
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MigrateAnimeScreen(
    navigateUp: () -> Unit,
    title: String,
    state: MigrateAnimeScreenModel.State,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        MigrateAnimeContent(
            contentPadding = contentPadding,
            state = state,
            onClickItem = onClickItem,
            onClickCover = onClickCover,
        )
    }
}

@Composable
private fun MigrateAnimeContent(
    contentPadding: PaddingValues,
    state: MigrateAnimeScreenModel.State,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
) {
    FastScrollLazyColumn(
        state = rememberLazyListState(),
        contentPadding = contentPadding,
    ) {
        items(
            items = state.titles,
            key = { "migrate-anime-${it.id}" },
        ) { anime ->
            BaseAnimeListItem(
                anime = anime,
                onClickItem = { onClickItem(anime) },
                onClickCover = { onClickCover(anime) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}
