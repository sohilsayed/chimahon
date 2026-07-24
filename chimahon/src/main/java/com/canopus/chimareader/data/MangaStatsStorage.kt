package com.canopus.chimareader.data

import android.content.Context
import java.io.File
import java.time.LocalDate

object MangaStatsStorage {

    private fun getMangaStatsFile(context: Context): File {
        return File(context.filesDir, FileNames.mangaStats)
    }

    fun loadAll(context: Context): List<MangaStats> {
        val file = getMangaStatsFile(context)
        if (!file.exists()) return emptyList()
        return BookStorage.load<List<MangaStats>>(context.filesDir, FileNames.mangaStats) ?: emptyList()
    }

    fun saveAll(context: Context, stats: List<MangaStats>) {
        BookStorage.save(stats, context.filesDir, FileNames.mangaStats)
        chimahon.widget.ImmersionWidgetSignals.notifyStatsChanged()
    }

    fun addStats(context: Context, characters: Int, timeMs: Long, mangaId: Long = 0, date: LocalDate = LocalDate.now()) {
        if (characters <= 0 && timeMs <= 0) return

        val dateKey = date.toString()
        val allStats = loadAll(context).toMutableList()
        val existing = allStats.find { it.dateKey == dateKey && it.mangaId == mangaId }
        if (existing != null) {
            existing.charactersRead += characters
            existing.readingTime += timeMs
        } else {
            allStats.add(MangaStats(dateKey, characters, timeMs, mangaId))
        }
        saveAll(context, allStats)
    }

    fun merge(context: Context, incoming: List<MangaStats>) {
        if (incoming.isEmpty()) return
        val local = loadAll(context).toMutableList()
        var changed = false
        incoming.forEach { remote ->
            val existing = local.find { it.dateKey == remote.dateKey && it.mangaId == remote.mangaId }
            if (existing != null) {
                // If remote has higher values, we assume it's more complete or we should merge them?
                // Actually, if these are daily stats, we should probably take the max or sum them depending on if they are from the same device.
                // But since we don't track device ID here, let's take the max of each field as a safe bet for "most complete record for that day".
                if (remote.charactersRead > existing.charactersRead) {
                    existing.charactersRead = remote.charactersRead
                    changed = true
                }
                if (remote.readingTime > existing.readingTime) {
                    existing.readingTime = remote.readingTime
                    changed = true
                }
            } else {
                local.add(remote)
                changed = true
            }
        }
        if (changed) saveAll(context, local)
    }
}
