package tachiyomi.presentation.widget.util

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import tachiyomi.presentation.widget.R
import tachiyomi.presentation.widget.components.GridHorizontalPadding
import tachiyomi.presentation.widget.components.GridItemGap

fun GlanceModifier.appWidgetBackgroundRadius(): GlanceModifier {
    return this.cornerRadius(R.dimen.appwidget_background_radius)
}

fun GlanceModifier.appWidgetInnerRadius(): GlanceModifier {
    return this.cornerRadius(R.dimen.appwidget_inner_radius)
}

const val BookCoverAspect = 2f / 3f

const val UpdatesGridColumns = 4

/** Gaps only between covers so first/last sit flush with outer padding. */
fun DpSize.coverSizeForGrid(columnCount: Int): Pair<Dp, Dp> {
    val cols = columnCount.coerceAtLeast(1)
    val totalChrome = GridHorizontalPadding * 2 + GridItemGap * (cols - 1)
    val coverWidth = ((width - totalChrome) / cols).coerceAtLeast(1.dp)
    val coverHeight = coverWidth / BookCoverAspect
    return coverWidth to coverHeight
}

fun DpSize.columnCountForWidth(): Int {
    val usableWidth = (width - GridHorizontalPadding * 2).coerceAtLeast(1.dp)
    return when {
        usableWidth < 160.dp -> 2
        usableWidth < 220.dp -> 3
        else -> UpdatesGridColumns
    }
}
