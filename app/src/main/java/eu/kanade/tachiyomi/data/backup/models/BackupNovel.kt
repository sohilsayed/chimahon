package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupNovel(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3) val author: String? = null,
    @ProtoNumber(4) val cover: String? = null,
    @ProtoNumber(5) val chapterIndex: Int = 0,
    @ProtoNumber(6) val progress: Double = 0.0,
    @ProtoNumber(7) val characterCount: Int = 0,
    @ProtoNumber(8) val lastModified: Long = 0L,
    @ProtoNumber(9) val stats: List<BackupStatEntry> = emptyList(),
)

@Serializable
data class BackupStatEntry(
    @ProtoNumber(1) val dateKey: String,
    @ProtoNumber(2) val charactersRead: Int,
    @ProtoNumber(3) val readingTime: Double,
    @ProtoNumber(4) val minReadingSpeed: Int,
    @ProtoNumber(5) val altMinReadingSpeed: Int,
    @ProtoNumber(6) val lastReadingSpeed: Int,
    @ProtoNumber(7) val maxReadingSpeed: Int,
    @ProtoNumber(8) val lastStatisticModified: Long,
)
