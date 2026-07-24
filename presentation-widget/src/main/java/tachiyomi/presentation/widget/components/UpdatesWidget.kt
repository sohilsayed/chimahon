package tachiyomi.presentation.widget.components

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.widget.util.columnCountForWidth
import tachiyomi.presentation.widget.util.coverSizeForGrid

const val UpdatesGridLimit = 12

@Composable
fun UpdatesWidget(
    data: ImmutableList<UpdatesWidgetItem>?,
    contentColor: ColorProvider,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: GlanceModifier = GlanceModifier,
    showHeader: Boolean = true,
) {
    val context = LocalContext.current
    val headerTitle = context.stringResource(MR.strings.label_recent_updates)
    val emptyText = context.stringResource(MR.strings.information_no_recent)

    val openUpdates = Intent(context, Class.forName(Constants.MAIN_ACTIVITY)).apply {
        action = Constants.SHORTCUT_UPDATES
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showHeader) {
            UpdatesWidgetHeader(
                title = headerTitle,
                contentColor = contentColor,
                onClick = actionStartActivity(openUpdates),
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxWidth(),
        ) {
            when {
                data == null -> {
                    CircularProgressIndicator(color = contentColor)
                }
                data.isEmpty() -> {
                    Text(
                        text = emptyText,
                        style = TextStyle(color = contentColor),
                    )
                }
                else -> {
                    val cols = LocalSize.current.columnCountForWidth()
                    val (_, coverHeight) = LocalSize.current.coverSizeForGrid(cols)
                    val rows = data.chunked(cols)

                    LazyColumn(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(
                                start = GridHorizontalPadding,
                                end = GridHorizontalPadding,
                                bottom = 12.dp,
                            ),
                    ) {
                        itemsIndexed(
                            items = rows,
                            itemId = { index, row ->
                                row.firstOrNull()?.mangaId ?: index.toLong()
                            },
                        ) { _, coverRow ->
                            UpdatesCoverRow(
                                coverRow = coverRow,
                                columnCount = cols,
                                coverHeight = coverHeight,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesCoverRow(
    coverRow: List<UpdatesWidgetItem>,
    columnCount: Int,
    coverHeight: Dp,
) {
    val context = LocalContext.current
    val cols = columnCount.coerceAtLeast(1)

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = GridRowGap / 2),
        verticalAlignment = Alignment.Top,
    ) {
        coverRow.forEachIndexed { index, item ->
            val intent = Intent(
                context,
                Class.forName(Constants.MAIN_ACTIVITY),
            ).apply {
                action = Constants.SHORTCUT_MANGA
                putExtra(Constants.MANGA_EXTRA, item.mangaId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // https://issuetracker.google.com/issues/238793260
                addCategory(item.mangaId.toString())
            }
            val endPad = if (index < coverRow.lastIndex) GridItemGap else 0.dp
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .padding(end = endPad),
            ) {
                UpdatesMangaCover(
                    cover = item.cover,
                    height = coverHeight,
                    unreadCount = item.unreadCount,
                    modifier = GlanceModifier.clickable(actionStartActivity(intent)),
                    // KMK -->
                    color = MangaCover.dominantCoverColorMap[item.mangaId]?.first
                        ?.let { Color(it) },
                    // KMK <--
                )
            }
        }
        repeat(cols - coverRow.size) {
            Box(modifier = GlanceModifier.defaultWeight()) {}
        }
    }
}

val GridHorizontalPadding = 12.dp
val GridItemGap = 8.dp
val GridRowGap = 8.dp
