package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import logcat.LogPriority
import okhttp3.Interceptor
import okhttp3.Response
import okio.buffer
import okio.source
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.security.MessageDigest

/**
 * Intercepts `.mokuro` responses from any source and caches the raw JSON file.
 * This allows the reader to use precomputed OCR data without calling the Lens API.
 *
 * Cached files are stored in `{filesDir}/mokuro_ocr/{url_hash}.json`.
 */
class MokuroOcrInterceptor(
    context: Context,
) : Interceptor {

    private val cacheDir = File(context.filesDir, MOKURO_OCR_CACHE_DIR).apply {
        mkdirs()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        if (!url.endsWith(".mokuro", ignoreCase = true)) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)

        if (!response.isSuccessful) {
            return response
        }

        val cacheKey = url.sha256()
        val cacheFile = File(cacheDir, "$cacheKey.json")

        try {
            response.peekBody(Long.MAX_VALUE).source().buffer().use { source ->
                cacheFile.outputStream().buffered().use { out ->
                    out.write(source.readByteArray())
                }
            }
            logcat { "MokuroOcrInterceptor: cached .mokuro for $url" }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "MokuroOcrInterceptor: failed to cache .mokuro for $url" }
        }

        return response
    }

    companion object {
        private const val MOKURO_OCR_CACHE_DIR = "mokuro_ocr"

        fun getCacheFile(cacheDir: File, url: String): File? {
            if (!cacheDir.exists()) return null
            val cacheKey = url.sha256()
            val cacheFile = File(cacheDir, "$cacheKey.json")
            return if (cacheFile.exists()) cacheFile else null
        }

        private fun String.sha256(): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(this.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
