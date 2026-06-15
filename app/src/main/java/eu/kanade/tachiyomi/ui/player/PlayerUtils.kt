package eu.kanade.tachiyomi.ui.player

import java.util.Locale

fun formatTime(seconds: Long): String {
    if (seconds < 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun buildProgressString(lastSecondSeen: Long, totalSeconds: Long): String? {
    if (lastSecondSeen <= 0 && totalSeconds <= 0) return null
    return if (totalSeconds > 0) {
        "${formatTime(lastSecondSeen)} / ${formatTime(totalSeconds)}"
    } else {
        formatTime(lastSecondSeen)
    }
}
