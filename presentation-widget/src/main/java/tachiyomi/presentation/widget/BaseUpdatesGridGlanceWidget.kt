package tachiyomi.presentation.widget

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.presentation.widget.components.LockedWidget
import tachiyomi.presentation.widget.components.UpdatesGridLimit
import tachiyomi.presentation.widget.components.UpdatesWidget
import tachiyomi.presentation.widget.components.UpdatesWidgetItem
import tachiyomi.presentation.widget.util.appWidgetBackgroundRadius
import tachiyomi.presentation.widget.util.columnCountForWidth
import tachiyomi.presentation.widget.util.coverSizeForGrid
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.ZonedDateTime

abstract class BaseUpdatesGridGlanceWidget(
    private val context: Context = Injekt.get<Application>(),
    private val getUpdates: GetUpdates = Injekt.get(),
    private val preferences: SecurityPreferences = Injekt.get(),
) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    abstract val foreground: ColorProvider
    abstract val background: ImageProvider
    abstract val topPadding: Dp
    abstract val bottomPadding: Dp

    open val showHeader: Boolean = true

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val locked = preferences.useAuthenticator().get()
        val containerModifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .appWidgetBackground()
            .padding(top = topPadding, bottom = bottomPadding)
            .appWidgetBackgroundRadius()

        val manager = GlanceAppWidgetManager(context)
        val sizes = manager.getGlanceIds(javaClass)
            .flatMap { manager.getAppWidgetSizes(it) }
        // maxBy on empty list crashes provideGlance → "Can't show content"
        val widgetSize = sizes.maxByOrNull { it.height.value * it.width.value }
            ?: DpSize(180.dp, 110.dp)
        val columnCount = widgetSize.columnCountForWidth()
        val (coverWidthDp, coverHeightDp) = widgetSize.coverSizeForGrid(columnCount)

        val data: ImmutableList<UpdatesWidgetItem>? = if (locked) {
            null
        } else {
            runCatching {
                getUpdates
                    .subscribe(read = false, after = DateLimit.toEpochMilli())
                    .first()
                    .prepareData(
                        limit = UpdatesGridLimit,
                        coverWidthPx = coverWidthDp.value.toInt().dpToPx,
                        coverHeightPx = coverHeightDp.value.toInt().dpToPx,
                    )
            }.getOrElse { persistentListOf() }
        }

        provideContent {
            if (locked) {
                LockedWidget(
                    foreground = foreground,
                    modifier = containerModifier,
                )
            } else {
                UpdatesWidget(
                    data = data,
                    contentColor = foreground,
                    topPadding = topPadding,
                    bottomPadding = bottomPadding,
                    modifier = containerModifier,
                    showHeader = showHeader,
                )
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private suspend fun List<UpdatesWithRelations>.prepareData(
        limit: Int,
        coverWidthPx: Int,
        coverHeightPx: Int,
    ): ImmutableList<UpdatesWidgetItem> {
        val widthPx = coverWidthPx.coerceAtLeast(1)
        val heightPx = coverHeightPx.coerceAtLeast(1)
        val roundPx = context.resources.getDimension(R.dimen.appwidget_inner_radius)
        val unreadByManga = groupingBy { it.mangaId }.eachCount()
        return withIOContext {
            this@prepareData
                .distinctBy { it.mangaId }
                .take(limit)
                .map { updatesView ->
                    val request = ImageRequest.Builder(context)
                        .data(
                            MangaCover(
                                mangaId = updatesView.mangaId,
                                sourceId = updatesView.sourceId,
                                isMangaFavorite = true,
                                ogUrl = updatesView.coverData.url,
                                lastModified = updatesView.coverData.lastModified,
                            ),
                        )
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .precision(Precision.EXACT)
                        .size(widthPx, heightPx)
                        .scale(Scale.FILL)
                        .allowHardware(false)
                        .let {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                it.transformations(RoundedCornersTransformation(roundPx))
                            } else {
                                it
                            }
                        }
                        .build()
                    val bitmap = try {
                        context.imageLoader.execute(request)
                            .image
                            ?.asDrawable(context.resources)
                            ?.toBitmap()
                            ?.let { bmp ->
                                if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                    bmp.config == Bitmap.Config.HARDWARE
                                ) {
                                    bmp.copy(Bitmap.Config.ARGB_8888, false)?.also {
                                        if (!bmp.isRecycled) bmp.recycle()
                                    }
                                } else {
                                    bmp
                                }
                            }
                    } catch (_: Exception) {
                        null
                    }
                    UpdatesWidgetItem(
                        mangaId = updatesView.mangaId,
                        cover = bitmap,
                        unreadCount = unreadByManga[updatesView.mangaId] ?: 1,
                    )
                }
                .toImmutableList()
        }
    }

    companion object {
        val DateLimit: Instant
            get() = ZonedDateTime.now().minusMonths(3).toInstant()
    }
}
