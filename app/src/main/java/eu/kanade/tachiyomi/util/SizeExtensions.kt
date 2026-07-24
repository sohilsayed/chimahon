package eu.kanade.tachiyomi.util

import com.hippo.unifile.UniFile

fun UniFile.size(): Long = length()

@Suppress("MagicNumber")
fun Long.toSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(this / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
