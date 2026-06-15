package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.domain.extension.interactor.GetExtensionLanguages.Companion.getLanguageIconID
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.animeextension.AnimeExtensionFilterState
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeExtensionFilterScreen(
    navigateUp: () -> Unit,
    state: AnimeExtensionFilterState.Success,
    onClickToggle: (String) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_anime_extensions),
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
        AnimeExtensionFilterContent(
            contentPadding = contentPadding,
            state = state,
            onClickLang = onClickToggle,
        )
    }
}

@Composable
private fun AnimeExtensionFilterContent(
    contentPadding: PaddingValues,
    state: AnimeExtensionFilterState.Success,
    onClickLang: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier
            .padding(start = MaterialTheme.padding.small),
    ) {
        items(state.languages) { language ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val iconResId = getLanguageIconID(language) ?: R.drawable.globe
                Icon(
                    painter = painterResource(id = iconResId),
                    tint = Color.Unspecified,
                    contentDescription = language,
                    modifier = Modifier
                        .width(48.dp)
                        .height(32.dp),
                )
                SwitchPreferenceWidget(
                    modifier = Modifier.animateItem(),
                    title = LocaleHelper.getSourceDisplayName(language, context) +
                        (
                            " (${LocaleHelper.getDisplayName(language)})"
                                .takeIf { language !in listOf("all", "other") } ?: ""
                            ),
                    checked = language in state.enabledLanguages,
                    onCheckedChanged = { onClickLang(language) },
                )
            }
        }
    }
}
