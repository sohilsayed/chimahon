package com.canopus.chimareader.ttusync

import kotlinx.serialization.Serializable

@Serializable
data class TtuProgress(
    val dataId: Int = 0,
    val exploredCharCount: Int = 0,
    val progress: Double = 0.0,
    val lastBookmarkModified: Long = 0L,
)

@Serializable
data class TtuAudioBook(
    val title: String = "",
    val playbackPosition: Double = 0.0,
    val lastAudioBookModified: Long = 0L,
)

enum class SyncDirection {
    IMPORT, EXPORT, AUTO, SYNCED
}

sealed interface SyncResult {
    data object Skipped : SyncResult
    data class Synced(val title: String) : SyncResult
    data class Imported(val title: String, val characterCount: Int) : SyncResult
    data class Exported(val title: String, val characterCount: Int) : SyncResult
    data class Failed(val title: String, val error: String) : SyncResult
}

data class DriveFile(
    val id: String,
    val name: String,
)

data class DriveSyncFiles(
    val progress: DriveFile? = null,
    val statistics: DriveFile? = null,
    val audioBook: DriveFile? = null,
)

enum class DriveAuthStatus {
    NOT_CONFIGURED,
    AWAITING_CODE,
    CONNECTED,
    ERROR,
}

data class DriveAuthorizationResult(
    val userCode: String,
    val verificationUrl: String,
    val deviceCode: String,
    val interval: Int,
)

sealed class DriveAuthException(message: String) : Exception(message) {
    data object AccessDenied : DriveAuthException("Access denied by user")
    data object ExpiredToken : DriveAuthException("Device code expired")
    data class Unknown(val detail: String) : DriveAuthException(detail)
}
