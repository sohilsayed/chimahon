package eu.kanade.tachiyomi.torrentServer.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Serializable
data class TorrentRequest(
    val action: String,
    val hash: String = "",
    val link: String = "",
    val title: String = "",
    val poster: String = "",
    val data: String = "",
    val saveToDb: Boolean = false,
) {
    override fun toString(): String {
        return Json.encodeToString(serializer(), this)
    }
}
