package chimahon

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

class DictionaryRepository(
    private val externalFilesDir: File?,
) {
    private var session: Long? = null
    private var configuredPaths: DictionaryPaths = DictionaryPaths()
    private var cachedStyles: List<DictionaryStyle> = emptyList()
    /** ID of the profile that was last used to call [warmUp].  Prevents redundant engine rebuilds. */
    private var lastWarmedProfileId: String = ""



    /**
     * Warm up the query engine for [paths].
     * An engine rebuild is only triggered when [paths] or [profileId] differ
     * from the last warmed state, so calling this at every reader-open is safe.
     *
     * @param profileId optional profile ID for dedup tracking (empty = legacy callers)
     */
    @Synchronized
    fun warmUp(paths: DictionaryPaths, profileId: String = "") {
        val activeSession = session ?: HoshiDicts.createLookupObject().also { session = it }

        val pathsChanged = paths != configuredPaths
        val profileChanged = profileId.isNotEmpty() && profileId != lastWarmedProfileId

        if (pathsChanged || profileChanged) {
            HoshiDicts.rebuildQuery(
                session = activeSession,
                termPaths = paths.termPaths.toTypedArray(),
                freqPaths = paths.freqPaths.toTypedArray(),
                pitchPaths = paths.pitchPaths.toTypedArray(),
            )
            cachedStyles = HoshiDicts.getStyles(activeSession).toList()
            configuredPaths = paths
            if (profileId.isNotEmpty()) lastWarmedProfileId = profileId
        }
    }

    @Synchronized
    fun lookup(query: String, paths: DictionaryPaths, languageCode: String = ""): LookupResult2 {
        val t0 = SystemClock.elapsedRealtime()

        warmUp(paths)

        val activeSession = session ?: HoshiDicts.createLookupObject().also { session = it }

        val tLookupStart = SystemClock.elapsedRealtime()

        val effectiveLang = languageCode.lowercase()

        val genericDeinflector = chimahon.dictionary.DeinflectorRegistry.get(effectiveLang)

        val results = if (effectiveLang == "ja") {
            HoshiDicts.lookup(activeSession, query, 20, 25).toList()
        } else if (genericDeinflector != null) {
            val finalResults = mutableListOf<chimahon.LookupResult>()
            for (i in query.length downTo 1) {
                val substring = query.substring(0, i)
                val candidates = mutableMapOf<String, chimahon.dictionary.DeinflectionResult>()
                for (preprocessed in genericDeinflector.preProcess(substring).distinct()) {
                    for (deinflected in genericDeinflector.deinflect(preprocessed, effectiveLang)) {
                        if (deinflected.text !in candidates) {
                            candidates[deinflected.text] = deinflected
                        }
                    }
                }
                if (candidates.isNotEmpty()) {
                    val substringResults = candidates.flatMap { (candidateText, deinflected) ->
                        HoshiDicts.query(activeSession, candidateText).map { termResult ->
                            chimahon.LookupResult(
                                matched = substring,
                                deinflected = candidateText,
                                process = emptyArray(),
                                term = termResult,
                                preprocessorSteps = 0,
                            )
                        }
                    }
                    finalResults.addAll(substringResults)
                }
            }
            finalResults.distinctBy { it.term.expression to it.term.reading }.take(20)
        } else {
            HoshiDicts.lookup(activeSession, query, 20, 25).toList()
        }

        val lookupMs = SystemClock.elapsedRealtime() - tLookupStart

        // Media loading is now deferred — don't block on I/O here
        val totalMs = SystemClock.elapsedRealtime() - t0
        Log.i(
            "DictionaryRepo",
            "lookup_ms=$totalMs lookup_hoshidicts_ms=$lookupMs results=${results.size}",
        )
        return LookupResult2(
            results = results,
            styles = cachedStyles,
            mediaDataUris = emptyMap(),  // Empty on critical path
            error = null,
        )
    }

    @Synchronized
    fun loadMediaAsync(query: String, results: List<LookupResult>): Map<String, String> {
        val t0 = SystemClock.elapsedRealtime()
        val activeSession = session ?: return emptyMap()
        val mediaDataUris = buildMediaDataUris(activeSession, results)
        val mediaMs = SystemClock.elapsedRealtime() - t0
        Log.i(
            "DictionaryRepo",
            "loadMediaAsync_ms=$mediaMs query='$query' media_count=${mediaDataUris.size}",
        )
        return mediaDataUris
    }

    @Synchronized
    fun close() {
        session?.let(HoshiDicts::destroyLookupObject)
        session = null
        configuredPaths = DictionaryPaths()
        cachedStyles = emptyList()
        lastWarmedProfileId = ""
    }

    private fun buildMediaDataUris(
        activeSession: Long,
        results: List<LookupResult>,
    ): Map<String, String> {
        val requested = linkedSetOf<Pair<String, String>>()
        run loop@{
            results.forEach { lookup ->
                lookup.term.glossaries.forEach { glossary ->
                    extractImagePaths(glossary.glossary)
                        .forEach { path ->
                            requested += glossary.dictName to path
                            if (requested.size >= MAX_PRELOADED_MEDIA_ITEMS) return@loop
                        }
                }
            }
        }

        val limitedRequested = requested.take(MAX_PRELOADED_MEDIA_ITEMS)
        val out = LinkedHashMap<String, String>(limitedRequested.size)
        limitedRequested.forEach { (dictName, path) ->
            val bytes = HoshiDicts.getMediaFile(activeSession, dictName, path) ?: return@forEach
            if (bytes.size > MAX_PRELOADED_MEDIA_BYTES) return@forEach

            val mime = guessMimeType(path)
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUri = "data:$mime;base64,$b64"
            for (candidate in normalizeMediaPathCandidates(path)) {
                out[mediaKey(dictName, candidate)] = dataUri
            }
        }

        return out
    }

    private fun extractImagePaths(glossary: String): Set<String> {
        val text = glossary.trim()
        if (text.isEmpty() || !(text.startsWith("{") || text.startsWith("["))) return emptySet()
        if (!text.contains("\"img\"") && !text.contains("\"image\"")) return emptySet()

        return runCatching {
            val root: Any = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
            val found = linkedSetOf<String>()
            walkJson(root) { node ->
                val isImageNode = node.optString("tag") == "img" || node.optString("type") == "image"
                if (isImageNode) {
                    val candidate = node.optString("path").ifBlank { node.optString("src") }
                    candidate.takeIf { it.isNotBlank() }?.let(found::add)
                }
            }
            found
        }.getOrDefault(emptySet())
    }

    private fun walkJson(element: Any?, onObject: (JSONObject) -> Unit) {
        when (element) {
            is JSONObject -> {
                onObject(element)
                val keys = element.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val next = element.opt(key)
                    if (next is JSONObject || next is JSONArray) walkJson(next, onObject)
                }
            }
            is JSONArray -> {
                for (i in 0 until element.length()) {
                    val next = element.opt(i)
                    if (next is JSONObject || next is JSONArray) walkJson(next, onObject)
                }
            }
        }
    }

    private fun guessMimeType(path: String): String {
        return when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "bmp" -> "image/bmp"
            else -> "image/png"
        }
    }

    private fun mediaKey(dictName: String, path: String): String = "$dictName\u0000$path"

    private fun normalizeMediaPathCandidates(rawPath: String): Set<String> {
        val candidates = linkedSetOf(
            rawPath,
            rawPath.removePrefix("./"),
            rawPath.removePrefix("/"),
            rawPath.replace('\\', '/'),
        )
        runCatching { URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name()) }
            .getOrNull()
            ?.let {
                candidates += it
                candidates += it.removePrefix("./")
                candidates += it.removePrefix("/")
                candidates += it.replace('\\', '/')
            }
        return candidates
    }

    private fun currentUsedRamMb(): Long {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L)
    }

    data class LookupResult2(
        val results: List<LookupResult>,
        val styles: List<DictionaryStyle>,
        val mediaDataUris: Map<String, String>,
        val error: String?,
    )

    companion object {
        private const val MAX_PRELOADED_MEDIA_ITEMS = 2
        private const val MAX_PRELOADED_MEDIA_BYTES = 64 * 1024

        private val TYPE_SUBDIRS = listOf("term", "frequency", "pitch")

        @Synchronized
        fun migrateFlatDictionaries(dictionariesDir: File) {
            if (!dictionariesDir.isDirectory) return

            val typeSubdirSet = TYPE_SUBDIRS.toSet()
            val flatDirs = dictionariesDir.listFiles()
                ?.filter { it.isDirectory && it.name !in typeSubdirSet }
                .orEmpty()

            if (flatDirs.isEmpty()) return

            for (flatDir in flatDirs) {
                migrateSingleFlatDictionary(flatDir, dictionariesDir)
            }
        }

        private fun migrateSingleFlatDictionary(flatDir: File, dictionariesDir: File) {
            val dictName = flatDir.name
            if (!File(flatDir, "hash.table").isFile) {
                Log.w("DictMigration", "Skipping '$dictName' — no hash.table")
                return
            }
            try {
                val counts = HoshiDicts.probeEntryTypes(flatDir.absolutePath)
                val types = buildList {
                    if (counts[0] > 0) add("term")
                    if (counts[1] > 0) add("frequency")
                    if (counts[2] > 0) add("pitch")
                }.ifEmpty { TYPE_SUBDIRS }

                for (type in types) {
                    val typeDir = File(dictionariesDir, type)
                    typeDir.mkdirs()
                    val targetDir = File(typeDir, dictName)
                    if (targetDir.exists()) targetDir.deleteRecursively()
                    flatDir.copyRecursively(targetDir, overwrite = true)
                }

                flatDir.deleteRecursively()
                Log.i("DictMigration", "Migrated '$dictName' into ${types.joinToString(", ")}")
            } catch (e: Exception) {
                Log.e("DictMigration", "Failed to migrate '$dictName': ${e.message}")
            }
        }
    }
}
