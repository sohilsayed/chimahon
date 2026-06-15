package eu.kanade.tachiyomi.torrentServer

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.torrentServer.model.Torrent
import eu.kanade.tachiyomi.torrentServer.model.TorrentRequest
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.InputStream

object TorrentServerApi {
    private val network: NetworkHelper by injectLazy()
    private val hostUrl get() = TorrentServerUtils.hostUrl
    private val json = Json { ignoreUnknownKeys = true }

    fun echo(): String {
        return try {
            network.client.newCall(GET("$hostUrl/echo")).execute().body.string()
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { e.toString() }
            ""
        }
    }

    fun shutdown(): String {
        return try {
            network.client.newCall(GET("$hostUrl/shutdown")).execute().body.string()
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { e.toString() }
            ""
        }
    }

    fun addTorrent(
        link: String,
        title: String,
        poster: String = "",
        data: String = "",
        save: Boolean = false,
    ): Torrent {
        val req = TorrentRequest(
            "add",
            link = link,
            title = title,
            poster = poster,
            data = data,
            saveToDb = save,
        ).toString()
        val resp = network.client.newCall(
            POST("$hostUrl/torrents", body = req.toRequestBody("application/json".toMediaTypeOrNull())),
        ).execute()
        return json.decodeFromString(Torrent.serializer(), resp.body.string())
    }

    fun getTorrent(hash: String): Torrent {
        val req = TorrentRequest("get", hash).toString()
        val resp = network.client.newCall(
            POST("$hostUrl/torrents", body = req.toRequestBody("application/json".toMediaTypeOrNull())),
        ).execute()
        return json.decodeFromString(Torrent.serializer(), resp.body.string())
    }

    fun remTorrent(hash: String) {
        val req = TorrentRequest("rem", hash).toString()
        network.client.newCall(
            POST("$hostUrl/torrents", body = req.toRequestBody("application/json".toMediaTypeOrNull())),
        ).execute()
    }

    fun listTorrent(): List<Torrent> {
        val req = TorrentRequest("list").toString()
        val resp = network.client.newCall(
            POST("$hostUrl/torrents", body = req.toRequestBody("application/json".toMediaTypeOrNull())),
        ).execute()
        return json.decodeFromString<List<Torrent>>(resp.body.string())
    }

    fun uploadTorrent(
        file: InputStream,
        title: String,
        poster: String = "",
        data: String = "",
        save: Boolean = false,
    ): Torrent {
        val fileBytes = file.readBytes()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("title", title)
            .addFormDataPart("poster", poster)
            .addFormDataPart("data", data)
            .addFormDataPart("save", save.toString())
            .addFormDataPart(
                "file1",
                "filename",
                fileBytes.toRequestBody("application/x-bittorrent".toMediaTypeOrNull()),
            )
            .build()
        val request = okhttp3.Request.Builder()
            .url("$hostUrl/torrent/upload")
            .post(body)
            .build()
        val resp = network.client.newCall(request).execute()
        return json.decodeFromString(Torrent.serializer(), resp.body.string())
    }
}
