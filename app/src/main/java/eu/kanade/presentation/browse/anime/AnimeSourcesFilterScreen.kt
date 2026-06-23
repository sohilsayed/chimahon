package eu.kanade.presentation.browse.anime

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.presentation.browse.components.BaseBrowseItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourcesFilterScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeSourcesFilterScreen(
    navigateUp: () -> Unit,
    state: AnimeSourcesFilterScreenModel.State.Success,
    onClickLanguage: (String) -> Unit,
    onClickSource: (AnimeSource) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_sources),
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.source_filter_empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }
        AnimeSourcesFilterContent(
            contentPadding = contentPadding,
            state = state,
            onClickLanguage = onClickLanguage,
            onClickSource = onClickSource,
        )
    }
}

@Composable
private fun AnimeSourcesFilterContent(
    contentPadding: PaddingValues,
    state: AnimeSourcesFilterScreenModel.State.Success,
    onClickLanguage: (String) -> Unit,
    onClickSource: (AnimeSource) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        state.items.forEach { (language, sources) ->
            val enabled = language in state.enabledLanguages
            item(
                key = language,
                contentType = "source-filter-header",
            ) {
                SwitchPreferenceWidget(
                    title = LocaleHelper.getSourceDisplayName(language, LocalContext.current),
                    checked = enabled,
                    onCheckedChanged = { onClickLanguage(language) },
                )
            }
            if (enabled) {
                items(
                    items = sources,
                    key = { "source-filter-${it.id}" },
                    contentType = { "source-filter-item" },
                ) { source ->
                    AnimeSourcesFilterItem(
                        source = source,
                        isEnabled = source.id.toString() !in state.disabledSources,
                        onClickItem = onClickSource,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeSourcesFilterItem(
    source: AnimeSource,
    isEnabled: Boolean,
    onClickItem: (AnimeSource) -> Unit,
) {
    BaseBrowseItem(
        onClickItem = { onClickItem(source) },
        content = {
            Column(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.medium)
                    .weight(1f),
            ) {
                Text(
                    text = source.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        action = {
            Checkbox(checked = isEnabled, onCheckedChange = null)
        },
    )
}
