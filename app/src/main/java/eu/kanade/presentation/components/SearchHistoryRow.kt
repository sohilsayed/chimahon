package eu.kanade.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.domain.history.model.SearchHistory
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SearchHistoryRow(
    historyList: List<SearchHistory>,
    onSelectQuery: (String) -> Unit,
    onDeleteQuery: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (historyList.isEmpty()) return

    val itemsToShow = historyList.take(20)
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val latestSearchedAt = historyList.firstOrNull()?.lastSearchedAt
    androidx.compose.runtime.LaunchedEffect(latestSearchedAt) {
        if (latestSearchedAt != null) {
            lazyListState.scrollToItem(0)
        }
    }

    LazyRow(
        state = lazyListState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = itemsToShow,
            key = { item -> "${item.scope}_${item.query}" },
        ) { item ->
            InputChip(
                selected = false,
                onClick = { onSelectQuery(item.query) },
                label = { Text(text = item.query) },
                trailingIcon = {
                    IconButton(
                        onClick = { onDeleteQuery(item.query) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(MR.strings.action_delete),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
        }

        item(key = "clear_all_chip") {
            AssistChip(
                onClick = onClearAll,
                label = {
                    Text(
                        text = stringResource(MR.strings.action_clear_all),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }
}

@Composable
fun SearchHistoryChips(
    historyList: List<SearchHistory>,
    onSelectQuery: (String) -> Unit,
    onDeleteQuery: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchHistoryRow(
        historyList = historyList,
        onSelectQuery = onSelectQuery,
        onDeleteQuery = onDeleteQuery,
        onClearAll = onClearAll,
        modifier = modifier,
    )
}
