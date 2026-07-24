package eu.kanade.presentation.entries.anime.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.components.DefaultDropdownMenuOffset
import eu.kanade.presentation.components.DropdownMenu
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AnimeDownloadDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DefaultDropdownMenuOffset,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        content = {
            AnimeDownloadDropdownMenuItems(
                onDismissRequest = onDismissRequest,
                onDownloadClicked = onDownloadClicked,
            )
        },
    )
}

@Composable
private fun AnimeDownloadDropdownMenuItems(
    onDismissRequest: () -> Unit,
    onDownloadClicked: (DownloadAction) -> Unit,
) {
    val options = persistentListOf(
        DownloadAction.NEXT_1_ITEM to pluralStringResource(MR.plurals.download_amount_episodes, 1, 1),
        DownloadAction.NEXT_5_ITEMS to pluralStringResource(MR.plurals.download_amount_episodes, 5, 5),
        DownloadAction.NEXT_10_ITEMS to pluralStringResource(MR.plurals.download_amount_episodes, 10, 10),
        DownloadAction.NEXT_25_ITEMS to pluralStringResource(MR.plurals.download_amount_episodes, 25, 25),
        DownloadAction.UNVIEWED_ITEMS to stringResource(MR.strings.download_unseen),
    )

    options.forEach { (downloadAction, label) ->
        DropdownMenuItem(
            text = { Text(text = label) },
            onClick = {
                onDownloadClicked(downloadAction)
                onDismissRequest()
            },
        )
    }
}
