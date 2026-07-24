package tachiyomi.domain.history.model

import java.util.Date

data class ReadingSession(
    val id: Long,
    val chapterId: Long,
    val readAt: Date,
    val duration: Long,
)
