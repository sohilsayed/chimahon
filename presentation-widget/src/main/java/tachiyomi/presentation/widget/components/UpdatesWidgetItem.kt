package tachiyomi.presentation.widget.components

import android.graphics.Bitmap

data class UpdatesWidgetItem(
    val mangaId: Long,
    val cover: Bitmap?,
    val unreadCount: Int,
)
