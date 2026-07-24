package eu.kanade.tachiyomi.glance

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.flow.first
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RecentHistoryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val lockedMessage = lockedWidgetMessage(context)
        if (isAppLocked()) {
            provideContent { WidgetLockedState(lockedMessage) }
            return
        }

        val historyList = runCatching {
            Injekt.get<GetHistory>()
                .subscribe("", null, null, null)
                .first()
                .take(HISTORY_LIMIT)
        }.getOrDefault(emptyList())

        val covers = buildMap {
            for (history in historyList) {
                if (!containsKey(history.mangaId)) {
                    put(history.mangaId, loadCoverBitmap(context, history.coverData, sizePx = COVER_PX))
                }
            }
        }

        val headerTitle = context.stringResource(MR.strings.history)
        val emptyText = context.getString(R.string.widget_no_recent_history)

        provideContent {
            val openHistory = actionStartActivity(
                mainActivityIntent(context, Constants.SHORTCUT_HISTORY),
            )
            Box(modifier = GlanceModifier.widgetContainer()) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    WidgetHeader(
                        title = headerTitle,
                        onClick = openHistory,
                    )

                    if (historyList.isEmpty()) {
                        WidgetEmptyState(emptyText)
                    } else {
                        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                            items(
                                items = historyList,
                                itemId = { it.id },
                            ) { history ->
                                HistoryItem(
                                    history = history,
                                    coverBitmap = covers[history.mangaId],
                                    onOpen = readerIntent(context, history.mangaId, history.chapterId),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val HISTORY_LIMIT = 8
        private const val COVER_PX = 96
    }
}

@Composable
private fun HistoryItem(
    history: HistoryWithRelations,
    coverBitmap: Bitmap?,
    onOpen: Intent,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(actionStartActivity(onOpen)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverBitmap != null) {
            Image(
                provider = ImageProvider(coverBitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.size(48.dp).cornerRadius(12.dp),
            )
        } else {
            CoverPlaceholder(
                modifier = GlanceModifier.size(48.dp).cornerRadius(12.dp),
            )
        }

        Spacer(modifier = GlanceModifier.width(16.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = history.title,
                style = TextStyle(
                    color = GlanceTheme.OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = formatWidgetChapterLabel(history.chapterNumber),
                style = TextStyle(
                    color = GlanceTheme.OnSurfaceVariant,
                    fontSize = 12.sp,
                ),
                maxLines = 1,
            )
        }
    }
}
