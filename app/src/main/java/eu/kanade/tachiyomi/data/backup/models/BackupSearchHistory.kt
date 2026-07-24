package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupSearchHistory(
    @ProtoNumber(1) val scope: String,
    @ProtoNumber(2) val query: String,
    @ProtoNumber(3) val lastSearchedAt: Long,
)
