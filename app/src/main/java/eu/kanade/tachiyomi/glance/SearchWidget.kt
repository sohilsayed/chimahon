package eu.kanade.tachiyomi.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import eu.kanade.tachiyomi.R
import tachiyomi.core.common.Constants

class SearchWidget : GlanceAppWidget() {

    // Responsive: Exact mode reports host px that often exceed 80dp on 1×1.
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(Size1x1, Size2x1, Size3x1, Size4x1),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val searchDescription = context.getString(R.string.widget_description_search)
        val searchHint = context.getString(R.string.widget_search_dictionary_hint)

        provideContent {
            val intent = mainActivityIntent(context, Constants.SHORTCUT_DICTIONARY)
            val size = LocalSize.current
            val isIconOnly = size.width <= Size1x1.width

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(100.dp)
                    .background(GlanceTheme.SurfaceContainer)
                    .clickable(actionStartActivity(intent)),
                contentAlignment = if (isIconOnly) {
                    Alignment.Center
                } else {
                    Alignment.CenterStart
                },
            ) {
                if (isIconOnly) {
                    AppLogoOnAccent(
                        contentDescription = searchDescription,
                        circleSize = 36.dp,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = GlanceModifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        AppLogoOnAccent(
                            contentDescription = searchDescription,
                            circleSize = 28.dp,
                        )
                        Spacer(modifier = GlanceModifier.width(12.dp))
                        Text(
                            text = searchHint,
                            style = TextStyle(
                                color = GlanceTheme.OnSurfaceVariant,
                                fontSize = 16.sp,
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        // 70×n − 30 cell formula
        private val Size1x1 = DpSize(40.dp, 40.dp)
        private val Size2x1 = DpSize(110.dp, 40.dp)
        private val Size3x1 = DpSize(180.dp, 40.dp)
        private val Size4x1 = DpSize(250.dp, 40.dp)
    }
}

@Composable
private fun AppLogoOnAccent(
    contentDescription: String,
    circleSize: Dp,
) {
    val logoSize = (circleSize.value * 0.58f).dp
    Box(
        modifier = GlanceModifier
            .size(circleSize)
            .cornerRadius(circleSize / 2)
            .background(GlanceTheme.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_chimahon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(GlanceTheme.SurfaceContainer),
            modifier = GlanceModifier.size(logoSize),
        )
    }
}
