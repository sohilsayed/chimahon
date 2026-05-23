package chimahon.jiten

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class JitenApiClient(
    private val okHttp: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()
    private val parseCache = LinkedHashMap<String, JitenParseResponse>(MAX_CACHE_SIZE, 0.75f, true)

    private val client = okHttp.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun parse(
        endpoint: String,
        apiKey: String,
        text: String,
    ): JitenParseResponse? {
        val cacheKey = sha256(text)
        parseCache[cacheKey]?.let {
            android.util.Log.d("TextColoring", "JitenApiClient.parse: in-memory cache hit")
            return it
        }
        android.util.Log.d("TextColoring", "JitenApiClient.parse: endpoint=$endpoint text.length=${text.length} apiKey=${if (apiKey.isBlank()) "EMPTY" else "SET"}")
        val requestBody = JitenParseRequest(text = listOf(text))
        val bodyJson = json.encodeToString(requestBody)
        val result = requestParse(endpoint, apiKey, "reader/parse", bodyJson)
        android.util.Log.d("TextColoring", "JitenApiClient.parse: result=${if (result != null) "SUCCESS (${result.tokens.size} paragraphs, ${result.vocabulary.size} vocab)" else "NULL/FAILED"}")
        if (result != null) {
            parseCache[cacheKey] = result
            // Evict oldest entries if cache is too large
            while (parseCache.size > MAX_CACHE_SIZE) {
                parseCache.entries.iterator().let { it.next(); it.remove() }
            }
        }
        return result
    }

    suspend fun lookupVocabulary(
        endpoint: String,
        apiKey: String,
        words: List<Pair<Int, Int>>,
    ): JitenLookupVocabularyResponse? {
        val requestBody = JitenLookupVocabularyRequest(
            words = words.map { listOf(it.first, it.second) }
        )
        val bodyJson = json.encodeToString(requestBody)
        return requestLookup(endpoint, apiKey, "reader/lookup-vocabulary", bodyJson)
    }

    suspend fun ping(
        endpoint: String,
        apiKey: String,
    ): Boolean {
        android.util.Log.d("TextColoring", "JitenApiClient.ping: endpoint=$endpoint apiKey=${if (apiKey.isBlank()) "EMPTY" else "SET"}")
        val bodyJson = "{}"
        val result = requestRaw(endpoint, apiKey, "reader/ping", bodyJson)
        android.util.Log.d("TextColoring", "JitenApiClient.ping: result=${result != null}")
        return result != null
    }

    suspend fun review(
        endpoint: String,
        apiKey: String,
        wordId: Int,
        readingIndex: Int,
        rating: Int,
    ): JitenReviewResponse? {
        val requestBody = JitenReviewRequest(wordId = wordId, readingIndex = readingIndex, rating = rating)
        val bodyJson = json.encodeToString(requestBody)
        return requestReview(endpoint, apiKey, "srs/review", bodyJson)
    }

    suspend fun setVocabularyState(
        endpoint: String,
        apiKey: String,
        wordId: Int,
        readingIndex: Int,
        state: String,
    ): JitenSetVocabularyStateResponse? {
        val requestBody = JitenSetVocabularyStateRequest(wordId = wordId, readingIndex = readingIndex, state = state)
        val bodyJson = json.encodeToString(requestBody)
        return requestSetVocabularyState(endpoint, apiKey, "srs/set-vocabulary-state", bodyJson)
    }

    private suspend fun requestParse(
        endpoint: String,
        apiKey: String,
        action: String,
        bodyJson: String,
    ): JitenParseResponse? {
        return try {
            val responseBody = requestRaw(endpoint, apiKey, action, bodyJson) ?: return null
            android.util.Log.d("TextColoring", "requestParse: response first 1000 chars=${responseBody.take(1000)}")
            // Check for error response first (like the reference JitenReader does)
            try {
                val errorResponse = json.decodeFromString<JitenErrorResponse>(responseBody)
                if (errorResponse.error_message != null) {
                    android.util.Log.e("TextColoring", "requestParse: API returned error: ${errorResponse.error_message}")
                    return null
                }
            } catch (_: Exception) {
                // Not an error response, continue with normal parsing
            }
            json.decodeFromString(responseBody)
        } catch (e: Exception) {
            android.util.Log.e("TextColoring", "requestParse: JSON deserialization failed for $action", e)
            null
        }
    }

    private suspend fun requestLookup(
        endpoint: String,
        apiKey: String,
        action: String,
        bodyJson: String,
    ): JitenLookupVocabularyResponse? {
        return try {
            val responseBody = requestRaw(endpoint, apiKey, action, bodyJson) ?: return null
            json.decodeFromString(responseBody)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun requestReview(
        endpoint: String,
        apiKey: String,
        action: String,
        bodyJson: String,
    ): JitenReviewResponse? {
        return try {
            val responseBody = requestRaw(endpoint, apiKey, action, bodyJson) ?: return null
            json.decodeFromString(responseBody)
        } catch (e: Exception) {
            android.util.Log.e("TextColoring", "requestReview: JSON deserialization failed", e)
            null
        }
    }

    private suspend fun requestSetVocabularyState(
        endpoint: String,
        apiKey: String,
        action: String,
        bodyJson: String,
    ): JitenSetVocabularyStateResponse? {
        return try {
            val responseBody = requestRaw(endpoint, apiKey, action, bodyJson) ?: return null
            json.decodeFromString(responseBody)
        } catch (e: Exception) {
            android.util.Log.e("TextColoring", "requestSetVocabularyState: JSON deserialization failed", e)
            null
        }
    }

    private fun requestRaw(
        endpoint: String,
        apiKey: String,
        action: String,
        bodyJson: String,
    ): String? {
        val url = "${endpoint.trimEnd('/')}/$action"
        val body = bodyJson.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "ApiKey $apiKey")
            .addHeader("Accept", "application/json")
            .post(body)
            .build()

        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    android.util.Log.e("TextColoring", "requestRaw: HTTP ${response.code} for $url body=${errorBody?.take(500)}")
                    if (response.code == 429 || response.code >= 500) {
                        attempt++
                        if (attempt < MAX_RETRIES) {
                            Thread.sleep(RETRY_DELAY_MS * (1L shl attempt) + (Math.random() * 500).toLong())
                            continue
                        }
                    }
                    return null
                }
                val responseBody = response.body?.string()
                android.util.Log.d("TextColoring", "requestRaw: HTTP 200 for $url body.length=${responseBody?.length}")
                return responseBody
            } catch (e: Exception) {
                android.util.Log.e("TextColoring", "requestRaw: exception for $url", e)
                attempt++
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(RETRY_DELAY_MS * (1L shl attempt) + (Math.random() * 500).toLong())
                    continue
                }
                return null
            }
        }
        return null
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 500L
        private const val MAX_CACHE_SIZE = 5

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
