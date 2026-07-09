package tachiyomi.presentation.widget.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import tachiyomi.presentation.widget.R
import tachiyomi.presentation.widget.util.appWidgetInnerRadius

val CoverWidth = 72.dp
val CoverHeight = 108.dp

@Composable
fun UpdatesMangaCover(
    cover: Bitmap?,
    height: Dp,
    modifier: GlanceModifier = GlanceModifier,
    unreadCount: Int = 0,
    // KMK -->
    color: Color? = null,
    // KMK <--
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .height(height)
    Box(
        modifier = (if (color != null) baseModifier.background(color) else baseModifier)
            .appWidgetInnerRadius(),
    ) {
        if (cover != null) {
            Image(
                provider = ImageProvider(cover),
                contentDescription = null,
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetInnerRadius(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.appwidget_cover_error),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        if (unreadCount > 0) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Text(
                    text = "+$unreadCount",
                    style = TextStyle(
                        color = ColorProvider(R.color.appwidget_on_primary),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier
                        .background(R.color.appwidget_primary)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
