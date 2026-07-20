package eu.kanade.tachiyomi.data.track.mangabaka.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Serializable
data class MangaBakaOAuth(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Long = 0,
    @SerialName("expires_at")
    val expiresAt: Long = 0,
    @SerialName("token_type")
    val tokenType: String = "Bearer",
    val scope: String = "",
) {
    fun isExpired(): Boolean = expiresAt > 0L && Clock.System.now().plus(1.minutes).epochSeconds > expiresAt

    fun withCalculatedExpiry(): MangaBakaOAuth {
        return if (expiresAt == 0L && expiresIn > 0L) {
            copy(expiresAt = Clock.System.now().epochSeconds + expiresIn)
        } else {
            this
        }
    }
}
