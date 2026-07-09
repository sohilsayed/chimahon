package eu.kanade.tachiyomi.glance

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
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
import androidx.glance.unit.ColorProvider
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object GlanceTheme {
    val SurfaceContainer = ColorProvider(R.color.appwidget_secondary_container)
    val SurfaceContainerHighest = ColorProvider(R.color.appwidget_surface_variant)
    val SurfaceContainerHighInner = ColorProvider(R.color.appwidget_surface_container_high)
    val OnSurface = ColorProvider(R.color.appwidget_on_background)
    val OnSurfaceVariant = ColorProvider(R.color.appwidget_on_surface_variant)
    val Primary = ColorProvider(R.color.appwidget_primary)
    val OnPrimary = ColorProvider(R.color.appwidget_on_primary)
    val OnSecondaryContainer = ColorProvider(R.color.appwidget_on_secondary_container)

    val RadiusOuter = 24.dp
    val RadiusInner = 16.dp
}

fun isAppLocked(): Boolean =
    Injekt.get<SecurityPreferences>().useAuthenticator().get()

fun GlanceModifier.appWidgetBackgroundRadius(): GlanceModifier =
    cornerRadius(GlanceTheme.RadiusOuter)

fun GlanceModifier.widgetContainer(): GlanceModifier =
    fillMaxSize()
        .appWidgetBackgroundRadius()
        .background(GlanceTheme.SurfaceContainer)

fun mainActivityIntent(context: Context, action: String? = null): Intent =
    Intent(context, MainActivity::class.java).apply {
        if (action != null) {
            this.action = action
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

fun readerIntent(context: Context, mangaId: Long, chapterId: Long): Intent =
    ReaderActivity.newIntent(context, mangaId, chapterId).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

fun formatWidgetChapterLabel(chapterNumber: Double, short: Boolean = false): String {
    if (chapterNumber <= -1.0) {
        return "Chapter"
    }
    val number = formatChapterNumber(chapterNumber)
    return if (short) "Ch. $number" else "Chapter $number"
}

/** Software bitmap — hardware bitmaps crash Glance/RemoteViews. */
suspend fun loadCoverBitmap(context: Context, data: Any?, sizePx: Int): Bitmap? =
    loadCoverBitmap(context, data, widthPx = sizePx, heightPx = sizePx)

suspend fun loadCoverBitmap(
    context: Context,
    data: Any?,
    widthPx: Int,
    heightPx: Int,
): Bitmap? {
    if (data == null) return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(data)
            .size(widthPx, heightPx)
            .allowHardware(false)
            .build()
        val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)?.also {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        } else {
            bitmap
        }
    } catch (_: Exception) {
        null
    }
}

val WidgetHeaderTitleSize = 14.sp

@Composable
fun WidgetHeader(
    title: String,
    modifier: GlanceModifier = GlanceModifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    showIcon: Boolean = true,
    onClick: Action? = null,
) {
    val base = modifier
        .fillMaxWidth()
        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    Row(
        modifier = if (onClick != null) base.clickable(onClick) else base,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            Image(
                provider = ImageProvider(R.drawable.ic_chimahon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.OnSurfaceVariant),
                modifier = GlanceModifier.size(24.dp).cornerRadius(8.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
        }
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.OnSurfaceVariant,
                fontSize = WidgetHeaderTitleSize,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
fun WidgetEmptyState(text: String) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(color = GlanceTheme.OnSurfaceVariant),
        )
    }
}

@Composable
fun WidgetLockedState(
    lockedMessage: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    val intent = mainActivityIntent(LocalContext.current)
    Box(
        modifier = modifier
            .widgetContainer()
            .clickable(actionStartActivity(intent))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = lockedMessage,
            style = TextStyle(
                color = GlanceTheme.OnSurfaceVariant,
                fontSize = 12.sp,
            ),
        )
    }
}

/** Real [Context] — Glance LocalContext is unsafe for string lookup here. */
fun lockedWidgetMessage(context: Context): String =
    context.stringResource(MR.strings.appwidget_unavailable_locked)

@Composable
fun CoverPlaceholder(modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier.background(GlanceTheme.SurfaceContainerHighest),
    ) {}
}

@Composable
fun StatCard(
    @DrawableRes iconRes: Int,
    value: String,
    label: String,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(
        modifier = modifier
            .background(GlanceTheme.SurfaceContainerHighInner)
            .cornerRadius(GlanceTheme.RadiusInner)
            .padding(12.dp),
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.Primary),
            modifier = GlanceModifier.size(24.dp),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = value,
            style = TextStyle(
                color = GlanceTheme.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.OnSurfaceVariant,
                fontSize = 11.sp,
            ),
            maxLines = 1,
        )
    }
}
