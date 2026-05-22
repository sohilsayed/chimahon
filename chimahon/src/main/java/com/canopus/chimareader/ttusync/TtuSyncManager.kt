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
    private val settingsRepository: SyncSettingsRepository,
    private val driveClient: TtuDriveClient = TtuDriveClient(context, authManager),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {

    val settingsFlow: kotlinx.coroutines.flow.Flow<SyncSettings>
        get() = settingsRepository.settings

    val autoSyncEnabled: Boolean
        get() = settingsRepository.currentSettings().autoSyncEnabled

    val statisticsSyncEnabled: Boolean
        get() = settingsRepository.currentSettings().statisticsSyncEnabled

    val audioBookSyncEnabled: Boolean
        get() = settingsRepository.currentSettings().audioBookSyncEnabled

    val statisticsSyncMode: StatisticsSyncMode
        get() = settingsRepository.currentSettings().statisticsSyncMode

    fun loadSettings(): SyncSettings = settingsRepository.currentSettings()

    fun saveSettings(settings: SyncSettings) {
        settingsRepository.update { settings }
    }

    fun updateSettings(transform: (SyncSettings) -> SyncSettings) {
        settingsRepository.update(transform)
    }

    val isEnabled: Boolean get() = settingsRepository.currentSettings().enabled && authManager.isConnected

    fun clearCache() {
        driveClient.clearCache()
    }

    suspend fun syncBook(
        bookMetadata: BookMetadata,
        direction: SyncDirection = SyncDirection.AUTO,
        importOnly: Boolean = false,
    ): SyncResult {
        if (!isEnabled) return SyncResult.Skipped

        val bookDir = BookStorage.getBookDirectory(context, bookMetadata.id)
        if (!bookDir.exists()) return SyncResult.Skipped

        if (bookMetadata.title.isNullOrBlank()) return SyncResult.Skipped
        val displayTitle = bookMetadata.title

        return try {
            performSync(bookMetadata, bookDir, direction, importOnly, displayTitle)
        } catch (e: DriveFileNotFoundException) {
            driveClient.clearCache()
            try {
                performSync(bookMetadata, bookDir, direction, importOnly, displayTitle)
            } catch (e2: Exception) {
                SyncResult.Failed(displayTitle, e2.message ?: "Unknown error")
            }
        } catch (e: Exception) {
            SyncResult.Failed(displayTitle, e.message ?: "Unknown error")
        }
    }

    private fun performSync(
        bookMetadata: BookMetadata,
        bookDir: File,
        direction: SyncDirection,
        importOnly: Boolean,
        displayTitle: String,
    ): SyncResult {
        val rootId = driveClient.findOrCreateRootFolder()
        val sanitizedTitle = TtuSyncRules.sanitizeTtuFilename(bookMetadata.title ?: "")
        val folderName = bookMetadata.ttuFolderName ?: sanitizedTitle

        val bookFolderId = driveClient.findOrCreateBookFolder(
            rootId = rootId,
            folderName = folderName,
            coverDataProvider = bookMetadata.cover?.let { coverPath ->
                val coverFile = if (File(coverPath).isAbsolute) {
                    File(coverPath)
                } else {
                    File(bookDir, coverPath)
                }
                if (coverFile.isFile) {
                    { coverFile.readBytes() }
                } else {
                    null
                }
            },
        )

        if (bookMetadata.ttuFolderName == null) {
            saveTtuFolderName(bookMetadata, folderName, bookDir)
        }

        val remoteFiles = driveClient.listSyncFiles(bookFolderId)
        val localBookmark = BookStorage.loadBookmark(bookDir)

        val resolvedDirection = if (direction != SyncDirection.AUTO) {
            direction
        } else {
            val localTs = localBookmark?.lastModified
            TtuSyncRules.determineDirection(localTs, remoteFiles.progress)
        }

        if (importOnly && resolvedDirection != SyncDirection.IMPORT) {
            return SyncResult.Synced(displayTitle)
        }

        return when (resolvedDirection) {
            SyncDirection.IMPORT -> importFromTtu(bookMetadata, bookDir, localBookmark, remoteFiles, displayTitle)
            SyncDirection.EXPORT -> exportToTtu(bookMetadata, bookDir, localBookmark, remoteFiles, bookFolderId, displayTitle)
            SyncDirection.SYNCED -> SyncResult.Synced(displayTitle)
            SyncDirection.AUTO -> SyncResult.Skipped
        }
    }

    private fun importFromTtu(
        bookMetadata: BookMetadata,
        bookDir: File,
        localBookmark: Bookmark?,
        remoteFiles: DriveSyncFiles,
        displayTitle: String,
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

        if (settingsRepository.currentSettings().statisticsSyncEnabled && remoteFiles.statistics != null) {
            try {
                val content = driveClient.downloadFile(remoteFiles.statistics.id)
                importStatistics(bookDir, content)
            } catch (_: Exception) {
            }
        }

        if (settingsRepository.currentSettings().audioBookSyncEnabled && remoteFiles.audioBook != null) {
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

        val title = displayTitle
        val count = localBookmark?.characterCount ?: 0
        return if (imported) SyncResult.Imported(title, count) else SyncResult.Synced(title)
    }

    private fun exportToTtu(
        bookMetadata: BookMetadata,
        bookDir: File,
        localBookmark: Bookmark?,
        remoteFiles: DriveSyncFiles,
        bookFolderId: String,
        displayTitle: String,
    ): SyncResult {
        val title = displayTitle
        val bookInfo = BookStorage.loadBookInfo(bookDir)

        if (localBookmark != null && bookInfo != null) {
            val lastModified = localBookmark.lastModified ?: System.currentTimeMillis()
            // Round to millisecond precision (matching iOS behavior)
            val unixTimestamp = lastModified
            val charBasedProgress = if (bookInfo.characterCount > 0) {
                localBookmark.characterCount.toDouble() / bookInfo.characterCount
            } else 0.0

            // Fetch remote progress to preserve dataId
            val remoteProgress = try {
                remoteFiles.progress?.let { driveClient.downloadFile(it.id) }
                    ?.let { json.decodeFromString<TtuProgress>(it) }
            } catch (_: Exception) {
                null
            }

            val ttuProgress = TtuProgress(
                dataId = remoteProgress?.dataId ?: 0,
                exploredCharCount = localBookmark.characterCount,
                progress = charBasedProgress,
                lastBookmarkModified = unixTimestamp,
            )
            val content = json.encodeToString(TtuProgress.serializer(), ttuProgress)
            val fileName = TtuSyncRules.progressFileName(ttuProgress)

            if (remoteFiles.progress != null) {
                driveClient.updateFile(remoteFiles.progress.id, content)
            } else {
                driveClient.uploadFile(bookFolderId, fileName, content)
            }

            // Save rounded timestamp back to local bookmark (matching iOS behavior)
            BookStorage.saveBookmark(
                localBookmark.copy(lastModified = unixTimestamp),
                bookDir,
            )
        }

        if (settingsRepository.currentSettings().statisticsSyncEnabled) {
            val localStats = BookStorage.loadStatistics(bookDir)
            if (localStats != null) {
                val mergedStats = if (remoteFiles.statistics != null) {
                    try {
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

        if (settingsRepository.currentSettings().audioBookSyncEnabled) {
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

    // iOS export: start with remote, overlay with local (newer wins)
    private fun mergeStatisticsForExport(base: List<Statistics>, overlay: List<Statistics>): List<Statistics> {
        if (settingsRepository.currentSettings().statisticsSyncMode == StatisticsSyncMode.Replace) return overlay
        val grouped = linkedMapOf<String, Statistics>()
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
        val merged = if (settingsRepository.currentSettings().statisticsSyncMode == StatisticsSyncMode.Replace) {
            remoteStats
        } else {
            val grouped = linkedMapOf<String, Statistics>()
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
