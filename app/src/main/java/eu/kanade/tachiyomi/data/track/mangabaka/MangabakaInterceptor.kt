package eu.kanade.tachiyomi.data.track.mangabaka

import eu.kanade.tachiyomi.data.track.Tracker
import okhttp3.Interceptor
import okhttp3.Response

class MangaBakaInterceptor(private val tracker: Tracker) : Interceptor {

    private var apiKey: String? = null

    fun setKey(key: String?) {
        apiKey = key
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val key = apiKey ?: tracker.getPassword().takeIf { it.isNotBlank() }
        if (key != null) {
            return chain.proceed(
                chain.request().newBuilder()
                    .header("x-api-key", key)
                    .build(),
            )
        }
        return chain.proceed(chain.request())
    }
}
