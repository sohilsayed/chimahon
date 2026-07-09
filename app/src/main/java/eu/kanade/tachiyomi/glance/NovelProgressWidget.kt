package eu.kanade.tachiyomi.glance

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.ui.reader.NovelReaderActivity
import eu.kanade.tachiyomi.R
import tachiyomi.core.common.Constants
import java.text.DecimalFormat

class NovelProgressWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val lockedMessage = lockedWidgetMessage(context)
        if (isAppLocked()) {
            provideContent { WidgetLockedState(lockedMessage) }
            return
        }

        val lastNovel = BookStorage.loadAllBooks(context).maxByOrNull { it.lastAccess }
        val bookDir = lastNovel?.let { book ->
            BookStorage.getBookDirectory(context, book.folder ?: book.id)
        }
        val bookmark = bookDir?.let { BookStorage.loadBookmark(it) }
        // TOC 1-based number (same basis as reader chapterStarts / chapter list), not spine index.
        val chapterText = if (bookDir != null && bookmark != null) {
            runCatching {
                val epub = BookStorage.loadEpub(bookDir)
                "Chapter ${epub.tocChapterNumber(bookmark.chapterIndex)}"
            }.getOrNull()
        } else {
            null
        }

        val noRecentNovels = context.getString(R.string.widget_no_recent_novels)
        val currentNovelLabel = context.getString(R.string.widget_current_novel)
        val unknownTitle = context.getString(R.string.widget_unknown_title)
        val chapterUnknown = context.getString(R.string.widget_chapter_unknown)

        provideContent {
            val intent = if (bookDir != null) {
                Intent(context, NovelReaderActivity.activityClass).apply {
                    putExtra("extra_book_dir", bookDir.absolutePath)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                mainActivityIntent(context, Constants.SHORTCUT_NOVELS)
            }

            Box(
                modifier = GlanceModifier
                    .widgetContainer()
                    .clickable(actionStartActivity(intent)),
            ) {
                if (lastNovel == null) {
                    WidgetEmptyState(noRecentNovels)
                } else {
                    Column(
                        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
                    ) {
                        Text(
                            text = currentNovelLabel,
                            style = TextStyle(
                                color = GlanceTheme.Primary,
                                fontSize = WidgetHeaderTitleSize,
                                fontWeight = FontWeight.Medium,
                            ),
                            modifier = GlanceModifier.padding(bottom = 4.dp),
                        )
                        Text(
                            text = lastNovel.title ?: unknownTitle,
                            style = TextStyle(
                                color = GlanceTheme.OnSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 2,
                        )

                        Spacer(modifier = GlanceModifier.defaultWeight())

                        val chapterLabel = chapterText ?: chapterUnknown
                        val progressPercent = PROGRESS_FORMAT.format((bookmark?.progress ?: 0.0) * 100)

                        Row(
                            modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = chapterLabel,
                                style = TextStyle(
                                    color = GlanceTheme.OnSurfaceVariant,
                                    fontSize = 12.sp,
                                ),
                                modifier = GlanceModifier.defaultWeight(),
                                maxLines = 1,
                            )
                            Text(
                                text = "$progressPercent%",
                                style = TextStyle(
                                    color = GlanceTheme.OnSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val PROGRESS_FORMAT = DecimalFormat("#.##")
    }
}
