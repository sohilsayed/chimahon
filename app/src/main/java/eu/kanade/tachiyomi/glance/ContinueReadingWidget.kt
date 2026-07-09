package eu.kanade.tachiyomi.glance

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import eu.kanade.tachiyomi.R
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ContinueReadingWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode
        get() = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val lockedMessage = lockedWidgetMessage(context)
        if (isAppLocked()) {
            provideContent { WidgetLockedState(lockedMessage) }
            return
        }

        val lastHistory = runCatching {
            Injekt.get<GetHistory>().awaitLast()
        }.getOrNull()

        val cover = lastHistory?.let {
            loadCoverBitmap(
                context = context,
                data = it.coverData,
                widthPx = COVER_LOAD_WIDTH_PX,
                heightPx = COVER_LOAD_HEIGHT_PX,
            )
        }

        val emptyText = context.getString(R.string.widget_no_recent_history)
        val resumeLabel = context.stringResource(MR.strings.action_resume)
        val headerTitle = context.getString(R.string.widget_continue_reading_title)

        provideContent {
            val intent = if (lastHistory != null) {
                readerIntent(context, lastHistory.mangaId, lastHistory.chapterId)
            } else {
                mainActivityIntent(context, Constants.SHORTCUT_LIBRARY)
            }

            Box(
                modifier = GlanceModifier
                    .widgetContainer()
                    .clickable(actionStartActivity(intent)),
            ) {
                if (lastHistory == null) {
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        WidgetHeader(title = headerTitle, showIcon = false)
                        WidgetEmptyState(emptyText)
                    }
                } else {
                    ContinueReadingContent(
                        history = lastHistory,
                        cover = cover,
                        resumeLabel = resumeLabel,
                        headerTitle = headerTitle,
                    )
                }
            }
        }
    }

    companion object {
        private const val COVER_LOAD_WIDTH_PX = 320
        private const val COVER_LOAD_HEIGHT_PX = 480
    }
}

@Composable
private fun ContinueReadingContent(
    history: HistoryWithRelations,
    cover: Bitmap?,
    resumeLabel: String,
    headerTitle: String,
) {
    val coverWidth = (LocalSize.current.width * 0.35f).coerceAtLeast(80.dp)

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
    ) {
        if (cover != null) {
            Image(
                provider = ImageProvider(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width(coverWidth),
            )
        } else {
            CoverPlaceholder(
                modifier = GlanceModifier
                    .fillMaxHeight()
                    .width(coverWidth),
            )
        }

        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            WidgetHeader(
                title = headerTitle,
                horizontalPadding = 0.dp,
                verticalPadding = 4.dp,
                showIcon = false,
            )
            Text(
                text = history.title,
                style = TextStyle(
                    color = GlanceTheme.OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 2,
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = formatWidgetChapterLabel(history.chapterNumber),
                style = TextStyle(
                    color = GlanceTheme.OnSurfaceVariant,
                    fontSize = 14.sp,
                ),
                maxLines = 1,
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .background(GlanceTheme.Primary)
                    .cornerRadius(16.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = resumeLabel,
                    style = TextStyle(
                        color = GlanceTheme.OnPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}
