package com.canopus.chimareader.data

import kotlinx.serialization.Serializable

@Serializable
enum class StatisticsSyncMode(val value: String) {
    MERGE("Merge"),
    REPLACE("Replace"),
    ;

    companion object {
        fun fromValue(value: String): StatisticsSyncMode {
            return entries.find { it.value == value } ?: MERGE
        }
    }
}

@Serializable
data class Statistics(
    val title: String,
    val dateKey: String,
    var charactersRead: Int = 0,
    var readingTime: Double = 0.0,
    var minReadingSpeed: Int = 0,
    var altMinReadingSpeed: Int = 0,
    var lastReadingSpeed: Int = 0,
    var maxReadingSpeed: Int = 0,
    var lastStatisticModified: Long = 0,
    var completedBook: Int? = null,
    var completedData: Statistics? = null,
)

@Serializable
data class AnkiStats(
    val dateKey: String,
    var mangaCards: Int = 0,
    var novelCards: Int = 0,
    var profileId: String = "",
)

@Serializable
data class MangaStats(
    val dateKey: String,
    var charactersRead: Int = 0,
    var readingTime: Long = 0, // In ms
    var mangaId: Long = 0,
)
