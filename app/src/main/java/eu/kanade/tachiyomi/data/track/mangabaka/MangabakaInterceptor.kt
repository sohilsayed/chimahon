package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaOAuth
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class MangaBakaInterceptor(private val tracker: MangaBaka) : Interceptor {

    private val json: Json by injectLazy()
    private var oauth: MangaBakaOAuth? = tracker.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val current = oauth ?: throw IOException("MangaBaka: User is not authenticated")
        if (current.isExpired()) {
            refreshToken(chain)
        }

        return chain.proceed(
            chain.request().newBuilder()
                .header("Authorization", "Bearer ${oauth!!.accessToken}")
                .build(),
        )
    }

    fun setAuth(oauth: MangaBakaOAuth?) {
        this.oauth = oauth?.withCalculatedExpiry()
        tracker.saveToken(this.oauth)
    }

    private fun refreshToken(chain: Interceptor.Chain) = synchronized(this) {
        val current = oauth ?: throw IOException("MangaBaka: User is not authenticated")
        if (!current.isExpired()) return@synchronized

        val refreshToken = current.refreshToken ?: throw IOException("MangaBaka: Missing refresh token")
        val response = chain.proceed(MangaBakaApi.refreshTokenRequest(refreshToken))
        if (!response.isSuccessful) {
            response.close()
            throw IOException("MangaBaka: Failed to refresh token")
        }

        val refreshed = with(json) { response.parseAs<MangaBakaOAuth>() }
            .withCalculatedExpiry()
        setAuth(refreshed.copy(refreshToken = refreshed.refreshToken ?: current.refreshToken))
    }
}
