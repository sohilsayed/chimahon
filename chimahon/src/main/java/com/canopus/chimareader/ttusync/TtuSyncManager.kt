package com.canopus.chimareader.ttusync

import android.content.Context
import com.canopus.chimareader.data.Bookmark
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.SasayakiPlaybackData
import com.canopus.chimareader.data.Statistics
import kotlinx.serialization.json.Json
import java.io.File

class TtuSyncManager(
    private val context: Context,
    private val authManager: TtuOAuthManager,
    private val driveClient: TtuDriveClient = TtuDriveClient(authManager),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {

    private var enabled: Boolean = false
    var autoSyncEnabled: Boolean = false
    var statisticsSyncEnabled: Boolean = false
    var statisticsSyncMode: String = "Merge"
    var audioBookSyncEnabled: Boolean = false

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    val isEnabled: Boolean get() = enabled && authManager.isConnected

    suspend fun syncBook(
        bookMetadata: BookMetadata,
        direction: SyncDirection = SyncDirection.AUTO,
        importOnly: Boolean = false,
    ): SyncResult {
        if (!isEnabled) return SyncResult.Skipped

        val bookDir = BookStorage.getBookDirectory(context, bookMetadata.id)
        if (!bookDir.exists()) return SyncResult.Skipped

        if (bookMetadata.title.isNullOrBlank()) return SyncResult.Skipped

        return try {
            performSync(bookMetadata, bookDir, direction, importOnly)
        } catch (e: DriveFileNotFoundException) {
            driveClient.clearCache()
            try {
                performSync(bookMetadata, bookDir, direction, importOnly)
            } catch (e2: Exception) {
                SyncResult.Failed(bookMetadata.title ?: "Unknown", e2.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            SyncResult.Failed(bookMetadata.title ?: "Unknown", e.message ?: "Unknown error")
        }
    }

    private fun performSync(
        bookMetadata: BookMetadata,
        bookDir: File,
        direction: SyncDirection,
        importOnly: Boolean,
    ): SyncResult {
        val rootId = driveClient.findOrCreateRootFolder()
        val folderName = bookMetadata.ttuFolderName
            ?: TtuSyncRules.sanitizeTtuFilename(bookMetadata.title ?: "")
        val bookFolderId = driveClient.findOrCreateBookFolder(rootId, folderName)

        if (bookMetadata.ttuFolderName == null) {
            saveTtuFolderName(bookMetadata, folderName, bookDir)
        }

        val remoteFiles = driveClient.listSyncFiles(bookFolderId)
        val localBookmark = BookStorage.loadBookmark(bookDir)

        val resolvedDirection = resolveDirection(direction, localBookmark, remoteFiles)

        if (importOnly && resolvedDirection != SyncDirection.IMPORT) {
            return SyncResult.Synced(bookMetadata.title ?: "")
        }

        return when (resolvedDirection) {
            SyncDirection.IMPORT -> importFromTtu(bookMetadata, bookDir, localBookmark, remoteFiles)
            SyncDirection.EXPORT -> exportToTtu(bookMetadata, bookDir, localBookmark, remoteFiles, bookFolderId)
            SyncDirection.SYNCED -> SyncResult.Synced(bookMetadata.title ?: "")
            SyncDirection.AUTO -> SyncResult.Skipped
        }
    }

    private fun resolveDirection(
        direction: SyncDirection,
        local: Bookmark?,
        remote: DriveSyncFiles,
    ): SyncDirection {
        if (direction != SyncDirection.AUTO) return direction

        val localTs = local?.lastModified
        val remoteTs = remote.progress?.name?.let { TtuSyncRules.parseProgressTimestamp(it) }

        return when {
            local == null && remote.progress == null -> SyncDirection.SYNCED
            local == null -> SyncDirection.IMPORT
            remote.progress == null -> SyncDirection.EXPORT
            localTs != null && remoteTs != null -> {
                when {
                    localTs > remoteTs -> SyncDirection.EXPORT
                    remoteTs > localTs -> SyncDirection.IMPORT
                    else -> SyncDirection.SYNCED
                }
            }
            localTs != null -> SyncDirection.EXPORT
            else -> SyncDirection.IMPORT
        }
    }

    private fun importFromTtu(
        bookMetadata: BookMetadata,
        bookDir: File,
        localBookmark: Bookmark?,
        remoteFiles: DriveSyncFiles,
    ): SyncResult {
        var imported = false

        if (remoteFiles.progress != null) {
            try {
                val content = driveClient.downloadFile(remoteFiles.progress.id)
                val ttuProgress = json.decodeFromString<TtuProgress>(content)
                val bookInfo = BookStorage.loadBookInfo(bookDir)
                val resolved = bookInfo?.resolveCharacterPosition(ttuProgress.exploredCharCount)
                val bookmark = Bookmark(
                    chapterIndex = resolved?.first ?: 0,
                    progress = (resolved?.second ?: ttuProgress.progress).coerceIn(0.0, 1.0),
                    characterCount = ttuProgress.exploredCharCount,
                    lastModified = ttuProgress.lastBookmarkModified,
                )
                BookStorage.saveBookmark(bookmark, bookDir)
                imported = true
            } catch (_: Exception) {
            }
        }

        if (statisticsSyncEnabled && remoteFiles.statistics != null) {
            try {
                val content = driveClient.downloadFile(remoteFiles.statistics.id)
                importStatistics(bookDir, content)
            } catch (_: Exception) {
            }
        }

        if (audioBookSyncEnabled && remoteFiles.audioBook != null) {
            try {
                val content = driveClient.downloadFile(remoteFiles.audioBook.id)
                val ttuAudio = json.decodeFromString<TtuAudioBook>(content)
                val playback = SasayakiPlaybackData(
                    lastPosition = ttuAudio.playbackPosition,
                )
                BookStorage.saveSasayakiPlaybackData(playback, bookDir)
            } catch (_: Exception) {
            }
        }

        val title = bookMetadata.title ?: "Unknown"
        val count = localBookmark?.characterCount ?: 0
        return if (imported) SyncResult.Imported(title, count) else SyncResult.Synced(title)
    }

    private fun exportToTtu(
        bookMetadata: BookMetadata,
        bookDir: File,
        localBookmark: Bookmark?,
        remoteFiles: DriveSyncFiles,
        bookFolderId: String,
    ): SyncResult {
        val title = bookMetadata.title ?: "Unknown"
        val bookInfo = BookStorage.loadBookInfo(bookDir)

        if (localBookmark != null && bookInfo != null) {
            // iOS: progress = totalChars / totalBookChars
            val charBasedProgress = if (bookInfo.characterCount > 0) {
                localBookmark.characterCount.toDouble() / bookInfo.characterCount
            } else 0.0

            val ttuProgress = TtuProgress(
                dataId = 0,
                exploredCharCount = localBookmark.characterCount,
                progress = charBasedProgress,
                lastBookmarkModified = localBookmark.lastModified ?: System.currentTimeMillis(),
            )
            val content = json.encodeToString(TtuProgress.serializer(), ttuProgress)
            val fileName = TtuSyncRules.progressFileName(ttuProgress)

            if (remoteFiles.progress != null) {
                driveClient.updateFile(remoteFiles.progress.id, content)
            } else {
                driveClient.uploadFile(bookFolderId, fileName, content)
            }
        }

        if (statisticsSyncEnabled) {
            val localStats = BookStorage.loadStatistics(bookDir)
            if (localStats != null) {
                val mergedStats = if (remoteFiles.statistics != null) {
                    try {
                        // iOS export: start with remote, overlay with local (newer wins)
                        val remoteContent = driveClient.downloadFile(remoteFiles.statistics.id)
                        val remoteStats = json.decodeFromString<List<Statistics>>(remoteContent)
                        mergeStatisticsForExport(remoteStats, localStats)
                    } catch (_: Exception) {
                        localStats
                    }
                } else {
                    localStats
                }
                val content = json.encodeToString(mergedStats)
                val fileName = TtuSyncRules.statisticsFileName(mergedStats)
                if (remoteFiles.statistics != null) {
                    driveClient.updateFile(remoteFiles.statistics.id, content)
                } else {
                    driveClient.uploadFile(bookFolderId, fileName, content)
                }
            }
        }

        if (audioBookSyncEnabled) {
            val playback = BookStorage.loadSasayakiPlaybackData(bookDir)
            if (playback != null) {
                val ttuAudio = TtuAudioBook(
                    title = title,
                    playbackPosition = playback.lastPosition,
                    lastAudioBookModified = System.currentTimeMillis(),
                )
                val content = json.encodeToString(TtuAudioBook.serializer(), ttuAudio)
                val fileName = TtuSyncRules.audioBookFileName(ttuAudio)
                if (remoteFiles.audioBook != null) {
                    driveClient.updateFile(remoteFiles.audioBook.id, content)
                } else {
                    driveClient.uploadFile(bookFolderId, fileName, content)
                }
            }
        }

        return SyncResult.Exported(title, localBookmark?.characterCount ?: 0)
    }

    // iOS merge(localStatistics, externalStatistics): start with local, overlay with external (newer wins)
    private fun mergeStatisticsForExport(base: List<Statistics>, overlay: List<Statistics>): List<Statistics> {
        if (statisticsSyncMode == "Replace") return overlay
        val grouped = mutableMapOf<String, Statistics>()
        for (stat in base) grouped[stat.dateKey] = stat
        for (stat in overlay) {
            val existing = grouped[stat.dateKey]
            if (existing == null || stat.lastStatisticModified > existing.lastStatisticModified) {
                grouped[stat.dateKey] = stat
            }
        }
        return grouped.values.toList()
    }

    // iOS import: start with local, overlay with remote
    private fun importStatistics(bookDir: File, remoteContent: String) {
        val remoteStats = try {
            json.decodeFromString<List<Statistics>>(remoteContent)
        } catch (_: Exception) {
            return
        }
        val localStats = BookStorage.loadStatistics(bookDir) ?: emptyList()
        val merged = if (statisticsSyncMode == "Replace") {
            remoteStats
        } else {
            val grouped = mutableMapOf<String, Statistics>()
            for (stat in localStats) grouped[stat.dateKey] = stat
            for (stat in remoteStats) {
                val existing = grouped[stat.dateKey]
                if (existing == null || stat.lastStatisticModified > existing.lastStatisticModified) {
                    grouped[stat.dateKey] = stat
                }
            }
            grouped.values.toList()
        }
        BookStorage.saveStatistics(merged, bookDir)
    }

    private fun saveTtuFolderName(metadata: BookMetadata, folderName: String, bookDir: File) {
        val updated = metadata.copy(ttuFolderName = folderName)
        BookStorage.saveMetadata(updated, bookDir)
    }
}
