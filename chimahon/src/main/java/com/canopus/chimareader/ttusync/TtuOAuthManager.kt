package com.canopus.chimareader.ttusync

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class TtuOAuthManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "ttu_sync_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_CLIENT_SECRET = "client_secret"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val TOKEN_REFRESH_MARGIN_MS = 60000L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value).apply()

    var clientSecret: String
        get() = prefs.getString(KEY_CLIENT_SECRET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLIENT_SECRET, value).apply()

    val isConfigured: Boolean get() = clientId.isNotBlank()

    val isConnected: Boolean get() = accessToken.isNotBlank()

    private val accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""

    private val refreshToken: String
        get() = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""

    private val expiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun requestDeviceCode(): DriveAuthorizationResult {
        val body = "client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}" +
            "&scope=${java.net.URLEncoder.encode(SCOPE, "UTF-8")}"
        val response = postForm(DEVICE_CODE_URL, body)
        val obj = json.parseToJsonElement(response).jsonObject
        return DriveAuthorizationResult(
            userCode = obj["user_code"]?.jsonPrimitive?.content ?: throw Exception("Missing user_code"),
            verificationUrl = obj["verification_url"]?.jsonPrimitive?.content ?: obj["verification_url"]?.jsonPrimitive?.content ?: "https://www.google.com/device",
            deviceCode = obj["device_code"]?.jsonPrimitive?.content ?: throw Exception("Missing device_code"),
            interval = obj["interval"]?.jsonPrimitive?.long?.toInt() ?: 5,
        )
    }

    fun pollAuthorization(deviceCode: String, interval: Int): Boolean {
        val body = "client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}" +
            "&client_secret=${java.net.URLEncoder.encode(clientSecret, "UTF-8")}" +
            "&code=$deviceCode&grant_type=urn:ietf:params:oauth:grant-type:device_code"
        val response = postForm(TOKEN_URL, body)
        val obj = json.parseToJsonElement(response).jsonObject
        val error = obj["error"]?.jsonPrimitive?.content
        when (error) {
            null -> {
                val access = obj["access_token"]?.jsonPrimitive?.content ?: return false
                val refresh = obj["refresh_token"]?.jsonPrimitive?.content ?: ""
                val expiresIn = obj["expires_in"]?.jsonPrimitive?.long ?: 3600L
                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, access)
                    .putString(KEY_REFRESH_TOKEN, refresh)
                    .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000)
                    .apply()
                return true
            }
            "authorization_pending" -> return false
            "slow_down" -> return false
            "access_denied" -> throw DriveAuthException.AccessDenied
            "expired_token" -> throw DriveAuthException.ExpiredToken
            else -> throw DriveAuthException.Unknown(error)
        }
    }

    fun getValidAccessToken(): String {
        if (accessToken.isBlank()) throw Exception("Not authenticated")
        if (System.currentTimeMillis() + TOKEN_REFRESH_MARGIN_MS < expiresAt) {
            return accessToken
        }
        return refreshAccessToken()
    }

    private fun refreshAccessToken(): String {
        if (refreshToken.isBlank()) throw Exception("No refresh token available")
        val body = "client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}" +
            "&client_secret=${java.net.URLEncoder.encode(clientSecret, "UTF-8")}" +
            "&refresh_token=${java.net.URLEncoder.encode(refreshToken, "UTF-8")}" +
            "&grant_type=refresh_token"
        val response = postForm(TOKEN_URL, body)
        val obj = json.parseToJsonElement(response).jsonObject
        val newAccess = obj["access_token"]?.jsonPrimitive?.content
            ?: throw DriveAuthException.Unknown("Failed to refresh token")
        val expiresIn = obj["expires_in"]?.jsonPrimitive?.long ?: 3600L
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, newAccess)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + expiresIn * 1000)
            .apply()
        return newAccess
    }

    fun revokeAccess() {
        try {
            if (accessToken.isNotBlank()) {
                val url = URL("https://oauth2.googleapis.com/revoke?token=${java.net.URLEncoder.encode(accessToken, "UTF-8")}")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.outputStream.write("".toByteArray())
                conn.responseCode
                conn.disconnect()
            }
        } catch (_: Exception) {
        }
        prefs.edit().clear().apply()
    }

    fun clearAuth() {
        prefs.edit().clear().apply()
    }

    private fun postForm(urlString: String, body: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Accept", "application/json")
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream
            }
            return BufferedReader(InputStreamReader(stream)).readText()
        } finally {
            conn.disconnect()
        }
    }
}
