package tachiyomi.presentation.widget.components

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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

@Composable
fun UpdatesWidgetHeader(
    title: String,
    contentColor: ColorProvider,
    modifier: GlanceModifier = GlanceModifier,
    onClick: Action? = null,
) {
    val context = LocalContext.current
    // presentation-widget has no app R for ic_chimahon
    val iconRes = context.resources.getIdentifier(
        "ic_chimahon",
        "drawable",
        context.packageName,
    )

    val base = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    Row(
        modifier = if (onClick != null) base.clickable(onClick) else base,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != 0) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(contentColor),
                modifier = GlanceModifier.size(24.dp).cornerRadius(8.dp),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
        }
        Text(
            text = title,
            style = TextStyle(
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
