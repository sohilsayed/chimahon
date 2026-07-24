package eu.kanade.tachiyomi.glance

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.dictionary.ScreenLookupPermissionActivity

class ScreenOcrWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(Size1x1, Size2x1),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val label = context.getString(R.string.widget_system_lookup)

        provideContent {
            val intent = Intent(context, ScreenLookupPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val size = LocalSize.current
            val isIconOnly = size.width <= Size1x1.width

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackgroundRadius()
                    .background(GlanceTheme.Primary)
                    .clickable(actionStartActivity(intent)),
                contentAlignment = Alignment.Center,
            ) {
                if (isIconOnly) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_photo_24dp),
                        contentDescription = label,
                        colorFilter = ColorFilter.tint(GlanceTheme.OnPrimary),
                        modifier = GlanceModifier.size(32.dp),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier.padding(16.dp),
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_photo_24dp),
                            contentDescription = label,
                            colorFilter = ColorFilter.tint(GlanceTheme.OnPrimary),
                            modifier = GlanceModifier.size(32.dp),
                        )
                        Spacer(modifier = GlanceModifier.width(12.dp))
                        Text(
                            text = label,
                            style = TextStyle(
                                color = GlanceTheme.OnPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val Size1x1 = DpSize(40.dp, 40.dp)
        private val Size2x1 = DpSize(110.dp, 40.dp)
    }
}
