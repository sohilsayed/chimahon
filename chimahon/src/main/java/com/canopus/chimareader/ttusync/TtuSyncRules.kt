package com.canopus.chimareader.ttusync

import com.canopus.chimareader.data.Statistics
import kotlin.math.ceil

object TtuSyncRules {

    private const val APPLE_EPOCH_OFFSET = 978307200000L

    fun appleReferenceSecondsToUnixMillis(appleSec: Double): Long {
        return (appleSec * 1000).toLong() + APPLE_EPOCH_OFFSET
    }

    fun unixMillisToAppleReferenceSeconds(unixMs: Long): Double {
        return ((unixMs - APPLE_EPOCH_OFFSET).toDouble() / 1000.0)
    }

    fun sanitizeTtuFilename(name: String): String {
        var result = name
        if (result.endsWith(" ")) {
            result = result.dropLast(1) + "~ttu-spc~"
        }
        if (result.endsWith(".")) {
            result = result.dropLast(1) + "~ttu-dend~"
        }
        result = result.replace("*", "~ttu-star~")
        result = result.replace(Regex("[/?<>\\\\:|%\"]")) { match ->
            java.net.URLEncoder.encode(match.value, "UTF-8")
        }
        return result
    }

    fun progressFileName(progress: TtuProgress): String {
        return "progress_1_6_${progress.lastBookmarkModified}_${progress.progress}.json"
    }

    fun statisticsFileName(stats: List<Statistics>): String {
        if (stats.isEmpty()) return "statistics_1_6_0_0_0_0_0_0_0_0_0_0_0_0_0_0_na.json"
        var readingTime = 0.0
        var charactersRead = 0
        var minReadingSpeed = 0
        var altMinReadingSpeed = 0
        var maxReadingSpeed = 0
        var weightedSum = 0L
        var validReadingDays = 0
        var lastStatisticModified = 0L

        for (stat in stats) {
            readingTime += stat.readingTime
            charactersRead += stat.charactersRead
            minReadingSpeed = if (minReadingSpeed > 0) minOf(minReadingSpeed, stat.minReadingSpeed) else stat.minReadingSpeed
            altMinReadingSpeed = if (altMinReadingSpeed > 0) minOf(altMinReadingSpeed, stat.altMinReadingSpeed) else stat.altMinReadingSpeed
            maxReadingSpeed = maxOf(maxReadingSpeed, stat.lastReadingSpeed)
            weightedSum += (stat.readingTime * stat.charactersRead).toLong()
            lastStatisticModified = maxOf(lastStatisticModified, stat.lastStatisticModified)
            if (stat.readingTime > 0) validReadingDays++
        }

        val averageReadingTime = if (validReadingDays > 0) ceil(readingTime / validReadingDays) else 0.0
        val averageWeightedReadingTime = if (charactersRead > 0) ceil(weightedSum.toDouble() / charactersRead) else 0.0
        val averageCharactersRead = if (validReadingDays > 0) ceil(charactersRead.toDouble() / validReadingDays) else 0.0
        val averageWeightedCharactersRead = if (readingTime > 0) ceil(weightedSum.toDouble() / readingTime) else 0.0
        val lastReadingSpeed = if (readingTime > 0) ceil((3600.0 * charactersRead) / readingTime) else 0.0
        val averageReadingSpeed = if (averageReadingTime > 0) ceil((3600.0 * averageCharactersRead) / averageReadingTime) else 0.0
        val averageWeightedReadingSpeed = if (averageWeightedReadingTime > 0) ceil((3600.0 * averageWeightedCharactersRead) / averageWeightedReadingTime) else 0.0

        return listOf(
            "statistics_1_6",
            lastStatisticModified, charactersRead, formatDouble(readingTime),
            minReadingSpeed, altMinReadingSpeed, formatDouble(lastReadingSpeed), maxReadingSpeed,
            formatDouble(averageReadingTime), formatDouble(averageWeightedReadingTime),
            formatDouble(averageCharactersRead), formatDouble(averageWeightedCharactersRead),
            formatDouble(averageReadingSpeed), formatDouble(averageWeightedReadingSpeed),
            "na",
        ).joinToString("_") + ".json"
    }

    fun audioBookFileName(audioBook: TtuAudioBook): String {
        return "audioBook_1_6_${audioBook.lastAudioBookModified}_${audioBook.playbackPosition}.json"
    }

    fun coverFileName(title: String): String {
        return "cover_1_6${coverExtension(title)}"
    }

    private fun coverExtension(title: String): String {
        return ".jpeg"
    }

    private fun formatDouble(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.1f", value)
        }
    }

    fun parseProgressTimestamp(filename: String): Long? {
        val parts = filename.removeSuffix(".json").split("_")
        if (parts.size >= 4 && parts[0] == "progress" && parts[1] == "1" && parts[2] == "6") {
            return parts[3].toLongOrNull()
        }
        return null
    }

    fun parseProgressValue(filename: String): Double? {
        val parts = filename.removeSuffix(".json").split("_")
        if (parts.size >= 5 && parts[0] == "progress" && parts[1] == "1" && parts[2] == "6") {
            return parts[4].toDoubleOrNull()
        }
        return null
    }

    fun parseAudioBookTimestamp(filename: String): Long? {
        val parts = filename.removeSuffix(".json").split("_")
        if (parts.size >= 4 && parts[0] == "audioBook" && parts[1] == "1" && parts[2] == "6") {
            return parts[3].toLongOrNull()
        }
        return null
    }

    fun isProgressFile(name: String): Boolean = name.startsWith("progress_1_6_") && name.endsWith(".json")
    fun isStatisticsFile(name: String): Boolean = name.startsWith("statistics_1_6_") && name.endsWith(".json")
    fun isAudioBookFile(name: String): Boolean = name.startsWith("audioBook_1_6_") && name.endsWith(".json")
    fun isCoverFile(name: String): Boolean = name.startsWith("cover_1_6.")
}
