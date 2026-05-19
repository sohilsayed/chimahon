package com.canopus.chimareader.ttusync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class TtuDriveClient(
    private val authManager: TtuOAuthManager,
) {
    private companion object {
        const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val ROOT_FOLDER_NAME = "ttu-reader-data"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var rootFolderId: String? = null
    private val folderCache = mutableMapOf<String, String>()

    fun findOrCreateRootFolder(): String {
        rootFolderId?.let { return it }
        val existing = findFolder(null, ROOT_FOLDER_NAME)
        if (existing != null) {
            rootFolderId = existing
            return existing
        }
        return createFolder(null, ROOT_FOLDER_NAME).also { rootFolderId = it }
    }

    fun findOrCreateBookFolder(rootId: String, folderName: String): String {
        folderCache[folderName]?.let { return it }
        val existing = findFolder(rootId, folderName)
        if (existing != null) {
            folderCache[folderName] = existing
            return existing
        }
        return createFolder(rootId, folderName).also { folderCache[folderName] = it }
    }

    fun listSyncFiles(folderId: String): DriveSyncFiles {
        val query = "'$folderId' in parents and trashed=false"
        val files = listFiles(query)
        var progress: DriveFile? = null
        var statistics: DriveFile? = null
        var audioBook: DriveFile? = null
        for (f in files) {
            when {
                TtuSyncRules.isProgressFile(f.name) -> progress = f
                TtuSyncRules.isStatisticsFile(f.name) -> statistics = f
                TtuSyncRules.isAudioBookFile(f.name) -> audioBook = f
            }
        }
        return DriveSyncFiles(progress, statistics, audioBook)
    }

    fun downloadFile(fileId: String): String {
        val token = authManager.getValidAccessToken()
        val url = URL("$DRIVE_API/files/$fileId?alt=media")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            checkResponse(conn)
            return BufferedReader(InputStreamReader(conn.inputStream)).readText()
        } finally {
            conn.disconnect()
        }
    }

    fun uploadFile(parentId: String, fileName: String, content: String, mimeType: String = "application/json") {
        val token = authManager.getValidAccessToken()
        val metadata = """{"name":"${escapeJson(fileName)}","parents":["$parentId"],"mimeType":"$mimeType"}"""
        val boundary = "-------${System.currentTimeMillis()}"
        val body = buildMultipartBody(boundary, metadata, content, mimeType)

        val url = URL("$DRIVE_UPLOAD/files?uploadType=multipart")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
            checkResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun updateFile(fileId: String, content: String, mimeType: String = "application/json") {
        val token = authManager.getValidAccessToken()
        val url = URL("$DRIVE_UPLOAD/files/$fileId?uploadType=media")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", mimeType)
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.outputStream.write(content.toByteArray(Charsets.UTF_8))
            checkResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun uploadCover(parentId: String, imageBytes: ByteArray, mimeType: String) {
        val token = authManager.getValidAccessToken()
        val fileName = "cover_1_6${coverExtension(mimeType)}"
        val metadata = """{"name":"${escapeJson(fileName)}","parents":["$parentId"],"mimeType":"$mimeType"}"""
        val boundary = "-------${System.currentTimeMillis()}"
        val body = buildMultipartBytesBody(boundary, metadata, imageBytes, mimeType)

        val url = URL("$DRIVE_UPLOAD/files?uploadType=multipart")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            conn.doOutput = true
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.outputStream.write(body)
            checkResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun deleteFile(fileId: String) {
        val token = authManager.getValidAccessToken()
        val url = URL("$DRIVE_API/files/$fileId")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            checkResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    fun clearCache() {
        rootFolderId = null
        folderCache.clear()
    }

    private fun findFolder(parentId: String?, name: String): String? {
        val token = authManager.getValidAccessToken()
        val parentClause = if (parentId != null) "'$parentId' in parents and " else ""
        val escapedName = name.replace("'", "\\'")
        val query = "${parentClause}name='$escapedName' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val files = listFiles(query)
        return files.firstOrNull()?.id
    }

    private fun createFolder(parentId: String?, name: String): String {
        val token = authManager.getValidAccessToken()
        val parentsJson = if (parentId != null) ""","parents":["$parentId"]""" else ""
        val body = """{"name":"${escapeJson(name)}","mimeType":"application/vnd.google-apps.folder"$parentsJson}"""
        val url = URL("$DRIVE_API/files")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
            checkResponse(conn)
            val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            val obj = json.parseToJsonElement(response).jsonObject
            return obj["id"]?.jsonPrimitive?.content
                ?: throw Exception("Failed to create folder: no id in response")
        } finally {
            conn.disconnect()
        }
    }

    private fun listFiles(query: String): List<DriveFile> {
        val token = authManager.getValidAccessToken()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = URL("$DRIVE_API/files?q=$encodedQuery&fields=files(id,name)&pageSize=100")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            checkResponse(conn)
            val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            val obj = json.parseToJsonElement(response).jsonObject
            return obj["files"]?.jsonArray?.map { element ->
                val fileObj = element.jsonObject
                DriveFile(
                    id = fileObj["id"]?.jsonPrimitive?.content ?: "",
                    name = fileObj["name"]?.jsonPrimitive?.content ?: "",
                )
            } ?: emptyList()
        } finally {
            conn.disconnect()
        }
    }

    private fun buildMultipartBody(boundary: String, metadata: String, content: String, mimeType: String): String {
        val sb = StringBuilder()
        sb.append("--$boundary\r\n")
        sb.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        sb.append(metadata)
        sb.append("\r\n--$boundary\r\n")
        sb.append("Content-Type: $mimeType\r\n\r\n")
        sb.append(content)
        sb.append("\r\n--$boundary--\r\n")
        return sb.toString()
    }

    private fun buildMultipartBytesBody(boundary: String, metadata: String, content: ByteArray, mimeType: String): ByteArray {
        val bos = ByteArrayOutputStream()
        bos.write("--$boundary\r\n".toByteArray())
        bos.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
        bos.write(metadata.toByteArray(Charsets.UTF_8))
        bos.write("\r\n--$boundary\r\n".toByteArray())
        bos.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
        bos.write(content)
        bos.write("\r\n--$boundary--\r\n".toByteArray())
        return bos.toByteArray()
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun coverExtension(mimeType: String): String = when {
        mimeType.contains("png") -> ".png"
        mimeType.contains("gif") -> ".gif"
        mimeType.contains("bmp") -> ".bmp"
        mimeType.contains("webp") -> ".webp"
        else -> ".jpeg"
    }

    private fun checkResponse(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code in 200..299) return
        val errorBody = try {
            BufferedReader(InputStreamReader(conn.errorStream)).readText()
        } catch (_: Exception) { "" }
        if (code == 404) throw DriveFileNotFoundException()
        if (code == 401) throw DriveAuthException.Unknown("Unauthorized - token may need refresh")
        throw Exception("Drive API error $code: $errorBody")
    }
}

class DriveFileNotFoundException : Exception("File not found")
