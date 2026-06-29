package eu.kanade.tachiyomi.ui.player.utils

private const val COMPLETED_RESUME_GRACE_MS = 1_500L

internal fun Long.safeResumePositionMillis(
    totalMillis: Long,
    seen: Boolean = false,
    resetWhenDurationUnknown: Boolean = seen,
): Long {
    val position = coerceAtLeast(0L)
    if (position == 0L) return 0L

    val total = totalMillis.coerceAtLeast(0L)
    if (total == 0L) {
        return if (resetWhenDurationUnknown) 0L else position
    }

    val completedThreshold = (total - COMPLETED_RESUME_GRACE_MS).coerceAtLeast(0L)
    return if (position >= completedThreshold) {
        0L
    } else {
        position
    }
}
