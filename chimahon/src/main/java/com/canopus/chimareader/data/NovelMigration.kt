package com.canopus.chimareader.data

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

object NovelMigration {
    private const val TAG = "NovelMigration"

    fun migrateOldBooks(context: Context) {
        val prefs = context.getSharedPreferences("novel_sync_migration", Context.MODE_PRIVATE)
        // v2: re-runs to handle books that had no metadata.json (skipped by v1)
        if (prefs.getBoolean("novel_migration_v2_done", false)) {
            return
        }

        Log.d(TAG, "Starting Novel Migration to stable IDs")

        val booksDir = BookStorage.getBooksDirectory(context)
        if (!booksDir.exists()) {
            prefs.edit().putBoolean("novel_migration_v2_done", true).apply()
            return
        }

        val folders = booksDir.listFiles()?.filter { it.isDirectory } ?: emptyList()

        for (bookDir in folders) {
            val metadata = BookStorage.loadMetadata(bookDir)

            when {
                // Case 1: Already fully migrated (has metadata with hash)
                metadata != null && metadata.hash != null -> {
                    Log.d(TAG, "${bookDir.name} already migrated, skipping")
                }

                // Case 2: Has metadata.json but no hash yet
                metadata != null && metadata.hash == null -> {
                    try {
                        val book = BookStorage.loadEpub(bookDir)
                        val title = book.title ?: metadata.title ?: "Unknown"
                        val author = book.author ?: ""

                        val hash = md5Hex("${title.trim().lowercase()}|${author.trim().lowercase()}")
                        Log.d(TAG, "Migrating (has meta) ${bookDir.name} -> $hash ($title | $author)")

                        val newMetadata = metadata.copy(
                            hash = hash,
                            id = hash,
                            folder = hash,
                            cover = metadata.cover?.replace(bookDir.name, hash),
                        )
                        BookStorage.saveMetadata(newMetadata, bookDir)

                        val newBookDir = File(booksDir, hash)
                        if (!newBookDir.exists()) {
                            if (!bookDir.renameTo(newBookDir)) {
                                Log.e(TAG, "Failed to rename folder from ${bookDir.name} to $hash")
                                BookStorage.saveMetadata(metadata.copy(hash = hash), bookDir)
                            } else {
                                Log.d(TAG, "Renamed folder successfully to $hash")
                            }
                        } else {
                            // Target already exists — merge this dir's data into it, then remove the duplicate
                            Log.w(TAG, "Target $hash already exists; merging ${bookDir.name} into it and removing duplicate")
                            mergeIntoTarget(sourceDir = bookDir, targetDir = newBookDir)
                            if (!bookDir.deleteRecursively()) {
                                Log.e(TAG, "Failed to delete duplicate source dir ${bookDir.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to migrate ${bookDir.name}: ${e.message}")
                    }
                }

                // Case 3: No metadata.json at all — parse EPUB to create it
                metadata == null -> {
                    try {
                        val book = BookStorage.loadEpub(bookDir)
                        val title = book.title ?: "Unknown"
                        val author = book.author ?: ""

                        val hash = md5Hex("${title.trim().lowercase()}|${author.trim().lowercase()}")
                        Log.d(TAG, "Migrating (no meta) ${bookDir.name} -> $hash ($title | $author)")

                        val coverAbsPath = book.coverPath?.let { File(bookDir, it).absolutePath }
                        val newMetadata = BookMetadata(
                            id = hash,
                            title = title,
                            cover = coverAbsPath,
                            folder = hash,
                            lastAccess = System.currentTimeMillis(),
                            hash = hash,
                            isGhost = false,
                        )
                        BookStorage.saveMetadata(newMetadata, bookDir)

                        val newBookDir = File(booksDir, hash)
                        if (!newBookDir.exists()) {
                            if (!bookDir.renameTo(newBookDir)) {
                                Log.e(TAG, "Failed to rename folder from ${bookDir.name} to $hash")
                            } else {
                                Log.d(TAG, "Renamed folder successfully to $hash")
                            }
                        } else {
                            // Target already exists — merge this dir's data into it, then remove the duplicate
                            Log.w(TAG, "Target $hash already exists; merging ${bookDir.name} into it and removing duplicate")
                            mergeIntoTarget(sourceDir = bookDir, targetDir = newBookDir)
                            if (!bookDir.deleteRecursively()) {
                                Log.e(TAG, "Failed to delete duplicate source dir ${bookDir.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "No metadata and EPUB parse failed for ${bookDir.name}: ${e.message}")
                    }
                }
            }
        }

        prefs.edit().putBoolean("novel_migration_v2_done", true).apply()
        Log.d(TAG, "Novel Migration v2 complete")
    }

    /**
     * Merges bookmark and statistics from [sourceDir] into [targetDir], keeping whichever
     * version of each record is newer. The EPUB content files in [targetDir] are left
     * untouched — only user-progress data is merged.
     */
    private fun mergeIntoTarget(sourceDir: File, targetDir: File) {
        // Bookmark: keep the more recently modified one
        val sourceBookmark = BookStorage.loadBookmark(sourceDir)
        val targetBookmark = BookStorage.loadBookmark(targetDir)
        if (sourceBookmark != null) {
            val sourceMod = sourceBookmark.lastModified ?: 0L
            val targetMod = targetBookmark?.lastModified ?: 0L
            if (sourceMod > targetMod) {
                BookStorage.saveBookmark(sourceBookmark, targetDir)
                Log.d(TAG, "mergeIntoTarget: used source bookmark (newer)")
            }
        }

        // Statistics: merge by dateKey, keeping the entry with the later lastStatisticModified
        val sourceStats = BookStorage.loadStatistics(sourceDir) ?: emptyList()
        if (sourceStats.isNotEmpty()) {
            val targetStatsMap = (BookStorage.loadStatistics(targetDir) ?: emptyList())
                .associateBy { it.dateKey }
                .toMutableMap()
            var changed = false
            for (stat in sourceStats) {
                val existing = targetStatsMap[stat.dateKey]
                if (existing == null || stat.lastStatisticModified > existing.lastStatisticModified) {
                    targetStatsMap[stat.dateKey] = stat
                    changed = true
                }
            }
            if (changed) {
                BookStorage.saveStatistics(targetStatsMap.values.toList(), targetDir)
                Log.d(TAG, "mergeIntoTarget: merged ${sourceStats.size} source stat entries")
            }
        }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
