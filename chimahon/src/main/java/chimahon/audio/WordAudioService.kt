package chimahon.audio

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class WordAudioService(
    private val context: Context,
    private val preferences: WordAudioPreferences = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
) {
    private val localDatabase = WordAudioDatabase(context)
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val TAG = "WordAudioService"
    }

    /**
     * Scans all enabled sources in priority order to find audio for a term.
     */
    suspend fun findWordAudio(term: String, reading: String): List<WordAudioResult> = withContext(Dispatchers.IO) {
        if (!preferences.wordAudioEnabled().get()) return@withContext emptyList()

        val results = mutableListOf<WordAudioResult>()
        val sources = getEnabledSources()

        for (source in sources) {
            try {
                when (source.type) {
                    WordAudioSource.SourceType.LOCAL -> {
                        val localEntries = findLocalAudio(term, reading)
                        results.addAll(
                            localEntries.map { entry ->
                                WordAudioResult(
                                    name = "${entry.sourceId} (${entry.speaker ?: entry.display ?: "Local"})",
                                    url = "chimahon-local://${entry.sourceId}/${entry.file}",
                                    source = source,
                                    data = null, // Fetched on demand via getAudioData
                                )
                            },
                        )
                    }
                    WordAudioSource.SourceType.ONLINE -> {
                        val onlineResults = fetchOnlineAudio(term, reading, source)
                        results.addAll(onlineResults)
                    }
                }

                if (results.isNotEmpty()) break
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching audio from source: ${source.name}", e)
            }
        }

        results
    }

    suspend fun getAudioData(filePath: String, sourceId: String): ByteArray? = withContext(Dispatchers.IO) {
        localDatabase.getAudioData(filePath, sourceId)
    }

    private fun getEnabledSources(): List<WordAudioSource> {
        val rawSources = preferences.wordAudioSources().get()
        val sources = try {
            json.decodeFromString<List<WordAudioSource>>(rawSources)
        } catch (e: Exception) {
            emptyList()
        }

        val localEnabled = preferences.wordAudioLocalEnabled().get()
        val finalSources = sources.toMutableList()

        if (localEnabled && finalSources.none { it.type == WordAudioSource.SourceType.LOCAL }) {
            finalSources.add(0, WordAudioSource.createLocal())
        }

        return finalSources.filter { it.isEnabled }
    }

    private fun findLocalAudio(term: String, reading: String): List<WordAudioDatabase.LocalEntry> {
        val path = preferences.wordAudioLocalPath().get()
        if (path.isBlank()) return emptyList()

        localDatabase.updatePath(path)
        return localDatabase.findEntries(term, reading)
    }

    private suspend fun fetchOnlineAudio(term: String, reading: String, source: WordAudioSource): List<WordAudioResult> {
        val url = source.url
            .replace("{term}", term)
            .replace("{reading}", reading)

        val request = Request.Builder().url(url).build()

        return try {
            network.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()

                val body = response.body?.string() ?: return emptyList()

                if (body.trim().startsWith("{")) {
                    parseYomitanJson(body, source)
                } else {
                    listOf(WordAudioResult(name = source.name, url = url, source = source))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error fetching online audio", e)
            emptyList()
        }
    }

    private fun parseYomitanJson(jsonStr: String, source: WordAudioSource): List<WordAudioResult> {
        return try {
            val jsonObj = json.parseToJsonElement(jsonStr) as? JsonObject ?: return emptyList()

            if ((jsonObj["type"] as? JsonPrimitive)?.content != "audioSourceList") {
                return emptyList()
            }

            val sourcesArray = jsonObj["audioSources"] as? JsonArray ?: return emptyList()

            sourcesArray.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = (obj["name"] as? JsonPrimitive)?.content ?: source.name
                val url = (obj["url"] as? JsonPrimitive)?.content ?: return@mapNotNull null

                WordAudioResult(name = name, url = url, source = source)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Yomitan JSON", e)
            emptyList()
        }
    }

    suspend fun fetchRemoteAudioData(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        try {
            network.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching remote audio data", e)
            null
        }
    }
}
