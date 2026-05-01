package com.canopus.chimareader.data

import kotlinx.serialization.Serializable

@Serializable
data class SasayakiCue(
    val id: String,
    val startTime: Double,
    val endTime: Double,
    val text: String,
)

@Serializable
data class SasayakiMatch(
    val id: String,
    val startTime: Double,
    val endTime: Double,
    val text: String,
    val chapterIndex: Int,
    val start: Int,
    val length: Int,
)

@Serializable
data class SasayakiCueRange(
    val id: String,
    val start: Int,
    val length: Int,
)

@Serializable
data class SasayakiMatchData(
    val matches: List<SasayakiMatch>,
    val unmatched: Int,
)

@Serializable
data class SasayakiPlaybackData(
    var lastPosition: Double,
    var delay: Double = 0.0,
    var rate: Float = 1f,
    var audioBookmark: String? = null, // Using string paths in Android instead of NSData security scopes
)
