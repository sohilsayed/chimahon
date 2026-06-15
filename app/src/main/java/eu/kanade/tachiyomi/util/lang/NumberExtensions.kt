package eu.kanade.tachiyomi.util.lang

import java.text.DecimalFormat
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Formats a number to a more readable count string (e.g. 1.2k, 3.4m).
 */
fun Long.toCountString(): String {
    if (this < 100_000) return this.toString()
    val exp = (log10(this.toDouble()) / 3).toInt().coerceAtMost(6)
    val suffixes = arrayOf("k", "m", "b", "t", "q", "Q")
    val suffix = suffixes[exp - 1]
    val value = this / 1000.0.pow(exp.toDouble())
    
    val pattern = when (suffix) {
        "k" -> "0.#"
        else -> "0.###"
    }
    return "${DecimalFormat(pattern).format(value)}$suffix"
}

fun Int.toCountString(): String = this.toLong().toCountString()
