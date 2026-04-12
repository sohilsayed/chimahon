package eu.kanade.tachiyomi.ui.dictionary

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import chimahon.DictionaryStyle
import chimahon.HoshiDicts
import chimahon.LookupResult
import chimahon.anki.AnkiCardCreator
import chimahon.anki.AnkiDroidBridge
import chimahon.anki.AnkiResult
import chimahon.dictionary.SimpleTextTypeDetector
import chimahon.dictionary.TextType
import chimahon.dictionary.TextTypeDetector
import chimahon.dictionary.arabic.ArabicDeinflector
import chimahon.dictionary.arabic.ArabicLookupMapper
import chimahon.dictionary.arabic.ArabicTextPreprocessors
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Collections.emptyList
import java.util.Collections.emptyMap
import java.util.Collections.emptySet
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale

fun getDictionaryPaths(context: android.content.Context): List<String> {
    val externalFilesDir = context.getExternalFilesDir(null) ?: return emptyList()
    val dictionariesDir = File(externalFilesDir, "dictionaries")

    if (!dictionariesDir.exists()) return emptyList()

    val allDicts = dictionariesDir.listFiles()
        ?.filter { it.isDirectory }
        ?.mapNotNull { it.name }
        .orEmpty()

    if (allDicts.isEmpty()) return emptyList()

    val orderPref = try {
        DictionaryPreferences(Injekt.get()).dictionaryOrder()
    } catch (_: Exception) {
        null
    }

    val currentOrder = orderPref?.get()?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val orderedNames = currentOrder.filter { it in allDicts }
    val remainingNames = allDicts.filter { it !in currentOrder }
    val finalOrder = orderedNames + remainingNames

    return finalOrder.map { name ->
        File(dictionariesDir, name).absolutePath
    }
}

data object DictionaryTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 4u,
                title = stringResource(MR.strings.label_dictionary),
                icon = rememberVectorPainter(Icons.Outlined.Search),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val tabNavigator = LocalTabNavigator.current
        val scope = rememberCoroutineScope()
        val sessionManager = remember { DictionarySessionManager() }

        var query by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var results by remember { mutableStateOf<List<LookupResult>>(emptyList()) }
        var styles by remember { mutableStateOf<List<DictionaryStyle>>(emptyList()) }
        var mediaDataUris by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var existingExpressions by remember { mutableStateOf<Set<String>>(emptySet()) }
        var hasSearched by remember { mutableStateOf(false) }
        var shouldMountWebView by remember { mutableStateOf(false) }
        var searchJob by remember { mutableStateOf<Job?>(null) }
        var retainedWebView by remember { mutableStateOf<WebView?>(null) }

        val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
        val ankiEnabled by dictionaryPreferences.ankiEnabled().collectAsState()
        val ankiDeck by dictionaryPreferences.ankiDeck().collectAsState()
        val ankiModel by dictionaryPreferences.ankiModel().collectAsState()
        val ankiFieldMap by dictionaryPreferences.ankiFieldMap().collectAsState()
        val ankiDupCheck by dictionaryPreferences.ankiDuplicateCheck().collectAsState()
        val ankiDupScope by dictionaryPreferences.ankiDuplicateScope().collectAsState()
        val ankiDupAction by dictionaryPreferences.ankiDuplicateAction().collectAsState()
        val ankiTags by dictionaryPreferences.ankiDefaultTags().collectAsState()
        val showFreqHarmonic by dictionaryPreferences.showFrequencyHarmonic().collectAsState()

        // Simple callback for Anki lookup - index maps to results array, glossaryIndex is optional
        val onAnkiLookup: ((Int, Int?) -> Unit)? = if (ankiEnabled) {
            { resultIndex, glossaryIndex ->
                val result = results.getOrNull(resultIndex)
                if (result != null) {
                    scope.launch {
                        val ankiResult = AnkiCardCreator.addToAnki(
                            context = context,
                            result = result,
                            deck = ankiDeck,
                            model = ankiModel,
                            fieldMapJson = ankiFieldMap,
                            tags = ankiTags,
                            dupCheck = ankiDupCheck,
                            dupScope = ankiDupScope,
                            dupAction = ankiDupAction,
                            glossaryIndex = glossaryIndex,
                        )
                        when (ankiResult) {
                            is AnkiResult.Success -> context.toast(MR.strings.anki_card_added)
                            is AnkiResult.CardExists -> context.toast(MR.strings.anki_card_exists)
                            is AnkiResult.OpenCard -> AnkiDroidBridge(context).guiEditNote(ankiResult.noteId)
                            is AnkiResult.PermissionDenied -> context.toast(MR.strings.pref_anki_permission_denied)
                            is AnkiResult.Error -> context.toast(
                                context.stringResource(MR.strings.anki_card_error, ankiResult.message),
                            )
                            is AnkiResult.NotConfigured -> context.toast(MR.strings.anki_not_configured)
                        }
                    }
                }
            }
        } else {
            null
        }

        LaunchedEffect(tabNavigator.current) {
            (context as? MainActivity)?.ready = true
        }

        LaunchedEffect(Unit) {
            // Mount after first frame so the search field appears immediately,
            // then keep the WebView warm for the first lookup payload.
            yield()
            shouldMountWebView = true
        }

        DisposableEffect(Unit) {
            onDispose {
                searchJob?.cancel()
                sessionManager.close()
                retainedWebView?.runCatching {
                    stopLoading()
                    destroy()
                }
                retainedWebView = null
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Search bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(MR.strings.action_search)) },
                    placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
                    singleLine = true,
                )

                Button(
                    onClick = {
                        val trimmedQuery = query.trim()
                        if (trimmedQuery.isEmpty()) {
                            results = emptyList()
                            mediaDataUris = emptyMap()
                            errorMessage = null
                            hasSearched = true
                            return@Button
                        }

                        searchJob?.cancel()
                        searchJob = scope.launch {
                            isLoading = true
                            errorMessage = null

                            val lookupResult = performLookup(
                                query = trimmedQuery,
                                context = context,
                                sessionManager = sessionManager,
                            )
                            results = lookupResult.results
                            styles = lookupResult.styles
                            mediaDataUris = lookupResult.mediaDataUris
                            errorMessage = lookupResult.error
                            hasSearched = true

                            // Check which expressions are already in Anki
                            if (ankiEnabled && ankiModel.isNotBlank() && results.isNotEmpty()) {
                                val uniqueExpressions = results.map { it.term.expression }.distinct()
                                existingExpressions = AnkiCardCreator.checkExistingCards(context, uniqueExpressions, ankiModel)
                            }

                            lookupResult.diagnostics?.let { diagnostics ->
                                Log.i(
                                    "DictionaryPerf",
                                    "query='$trimmedQuery' total_ms=${diagnostics.totalMs} " +
                                        "create_session_ms=${diagnostics.sessionCreateMs} rebuild_ms=${diagnostics.rebuildMs} " +
                                        "lookup_ms=${diagnostics.lookupMs} media_ms=${diagnostics.mediaMs} " +
                                        "ram_mb_before=${diagnostics.ramBeforeMb} ram_mb_after=${diagnostics.ramAfterMb} " +
                                        "results=${diagnostics.resultCount} json_glossaries=${diagnostics.jsonGlossaryCount} " +
                                        "css_bytes=${diagnostics.cssBytes} dump_dir=${lookupResult.debugDumpDir.orEmpty()}",
                                )
                            }

                            isLoading = false
                        }
                    },
                ) {
                    Text(stringResource(MR.strings.action_search))
                }
            }

            // Status / body
            when {
                isLoading -> Text(text = "Searching...")
                errorMessage != null -> Text(errorMessage.orEmpty())
                results.isEmpty() && hasSearched -> Text(stringResource(MR.strings.no_results_found))
            }

            if (shouldMountWebView) {
                // Create the WebView only when the user starts searching, so the search bar
                // renders immediately when opening the tab.
                DictionaryEntryWebView(
                    results = results,
                    styles = styles,
                    mediaDataUris = mediaDataUris,
                    placeholder = if (hasSearched) {
                        stringResource(MR.strings.no_results_found)
                    } else {
                        "Search to view dictionary entries"
                    },
                    showFrequencyHarmonic = showFreqHarmonic,
                    existingExpressions = existingExpressions,
                    webViewProvider = { context ->
                        retainedWebView ?: WebView(context).also { retainedWebView = it }
                    },
                    onAnkiLookup = onAnkiLookup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {}
            }
        }
    }

    // -------------------------------------------------------------------------

    private suspend fun performLookup(
        query: String,
        context: android.content.Context,
        sessionManager: DictionarySessionManager,
    ): LookupUiResult {
        return withContext(Dispatchers.IO) {
            val externalFilesDir = context.getExternalFilesDir(null)
            if (externalFilesDir == null) {
                return@withContext LookupUiResult(
                    results = emptyList(),
                    styles = emptyList(),
                    mediaDataUris = emptyMap(),
                    error = "Unable to access app storage.",
                    diagnostics = null,
                    debugDumpDir = null,
                )
            }

            val dictionaryPaths = getDictionaryPaths(context)

            if (dictionaryPaths.isEmpty()) {
                return@withContext LookupUiResult(
                    results = emptyList(),
                    styles = emptyList(),
                    mediaDataUris = emptyMap(),
                    error = "No imported dictionaries found. Import one in Settings > Dictionary.",
                    diagnostics = null,
                    debugDumpDir = null,
                )
            }

            try {
                val lookup = sessionManager.lookup(query = query, termPaths = dictionaryPaths)
                val dumpDir = dumpSearchDebugArtifacts(
                    externalFilesDir = externalFilesDir,
                    query = query,
                    result = lookup,
                )
                lookup.copy(debugDumpDir = dumpDir)
            } catch (e: Throwable) {
                LookupUiResult(
                    results = emptyList(),
                    styles = emptyList(),
                    mediaDataUris = emptyMap(),
                    error = e.message ?: "Dictionary lookup failed",
                    diagnostics = null,
                    debugDumpDir = null,
                )
            }
        }
    }

    private fun dumpSearchDebugArtifacts(
        externalFilesDir: File,
        query: String,
        result: LookupUiResult,
    ): String? {
        return runCatching {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val safeQuery = query.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(48).ifBlank { "query" }
            val dumpDir = File(externalFilesDir, "dictionary-debug/$timestamp-$safeQuery")
            if (!dumpDir.exists()) {
                dumpDir.mkdirs()
            }

            val stylesText = result.styles.joinToString("\n\n/* --- */\n\n") { it.styles }
            File(dumpDir, "styles.css").writeText(stylesText)

            val jsonGlossaries = mutableListOf<JSONObject>()
            result.results.forEach { lookup ->
                lookup.term.glossaries.forEach { glossary ->
                    val raw = glossary.glossary.trim()
                    if (raw.startsWith("{") || raw.startsWith("[")) {
                        jsonGlossaries += JSONObject().apply {
                            put("dictName", glossary.dictName)
                            put("term", lookup.term.expression)
                            put("reading", lookup.term.reading)
                            put("rawGlossary", raw)
                        }
                    }
                }
            }
            val jsonText = jsonGlossaries.joinToString("\n") { it.toString() }
            File(dumpDir, "glossaries.jsonl").writeText(jsonText)

            val metricsText = result.diagnostics?.let {
                """
                query=$query
                total_ms=${it.totalMs}
                create_session_ms=${it.sessionCreateMs}
                rebuild_ms=${it.rebuildMs}
                lookup_ms=${it.lookupMs}
                media_ms=${it.mediaMs}
                ram_mb_before=${it.ramBeforeMb}
                ram_mb_after=${it.ramAfterMb}
                result_count=${it.resultCount}
                json_glossary_count=${it.jsonGlossaryCount}
                css_bytes=${it.cssBytes}
                """.trimIndent()
            } ?: "no diagnostics"
            File(dumpDir, "metrics.txt").writeText(metricsText)

            dumpDir.absolutePath
        }.onFailure {
            Log.w("DictionaryPerf", "Failed to dump search debug artifacts", it)
        }.getOrNull()
    }

    private class DictionarySessionManager {
        private companion object {
            private const val MAX_PRELOADED_MEDIA_ITEMS = 4
            private const val MAX_PRELOADED_MEDIA_BYTES = 64 * 1024
        }

        private var session: Long? = null
        private var configuredTermPaths: List<String> = emptyList()
        private var cachedStyles: List<DictionaryStyle> = emptyList()

        fun lookup(query: String, termPaths: List<String>): LookupUiResult {
            val t0 = SystemClock.elapsedRealtime()
            val ramBefore = currentUsedRamMb()
            var sessionCreateMs = 0L
            var rebuildMs = 0L

            val hadSession = session != null
            val activeSession = session ?: HoshiDicts.createLookupObject().also { session = it }
            if (!hadSession) {
                sessionCreateMs = SystemClock.elapsedRealtime() - t0
            }

            if (termPaths != configuredTermPaths) {
                val rebuildStart = SystemClock.elapsedRealtime()
                HoshiDicts.rebuildQuery(
                    session = activeSession,
                    termPaths = termPaths.toTypedArray(),
                    freqPaths = termPaths.toTypedArray(),
                    pitchPaths = termPaths.toTypedArray(),
                )
                cachedStyles = HoshiDicts.getStyles(activeSession).toList()
                configuredTermPaths = termPaths
                rebuildMs = SystemClock.elapsedRealtime() - rebuildStart
            }

            val lookupStart = SystemClock.elapsedRealtime()
            val results = lookupByTextType(activeSession, query, 50)
            val lookupMs = SystemClock.elapsedRealtime() - lookupStart

            val mediaStart = SystemClock.elapsedRealtime()
            val mediaDataUris = buildMediaDataUris(activeSession, results)
            val mediaMs = SystemClock.elapsedRealtime() - mediaStart

            val totalMs = SystemClock.elapsedRealtime() - t0
            val cssBytes = cachedStyles.sumOf { it.styles.length }
            val jsonGlossaryCount = countJsonGlossaries(results)
            val diagnostics = LookupDiagnostics(
                totalMs = totalMs,
                sessionCreateMs = sessionCreateMs,
                rebuildMs = rebuildMs,
                lookupMs = lookupMs,
                mediaMs = mediaMs,
                ramBeforeMb = ramBefore,
                ramAfterMb = currentUsedRamMb(),
                resultCount = results.size,
                jsonGlossaryCount = jsonGlossaryCount,
                cssBytes = cssBytes,
            )

            return LookupUiResult(
                results = results,
                styles = cachedStyles,
                mediaDataUris = mediaDataUris,
                error = null,
                diagnostics = diagnostics,
                debugDumpDir = null,
            )
        }

        private fun countJsonGlossaries(results: List<LookupResult>): Int {
            var count = 0
            results.forEach { lookup ->
                lookup.term.glossaries.forEach { glossary ->
                    val text = glossary.glossary.trim()
                    if (text.startsWith("{") || text.startsWith("[")) {
                        count++
                    }
                }
            }
            return count
        }

        private val textTypeDetector: TextTypeDetector = SimpleTextTypeDetector()

        private fun lookupByTextType(session: Long, query: String, maxResults: Int): List<LookupResult> {
            return when (textTypeDetector.detect(query)) {
                TextType.ARABIC -> lookupArabic(session, query, maxResults)
                TextType.JAPANESE -> HoshiDicts.lookup(session, query, maxResults).toList()
            }
        }

        private fun lookupArabic(session: Long, query: String, maxResults: Int): List<LookupResult> {
            val preprocessed = ArabicTextPreprocessors.process(query)
            val deinflected = preprocessed.flatMap { ArabicDeinflector.deinflect(it) }
            val candidates = deinflected.map { it.text }.distinct()

            if (candidates.isEmpty()) return emptyList()

            val terms = HoshiDicts.query(session, candidates, maxResults)
            return ArabicLookupMapper.wrapAll(query, candidates, terms)
        }

        private fun buildMediaDataUris(activeSession: Long, results: List<LookupResult>): Map<String, String> {
            val requested = linkedSetOf<Pair<String, String>>()

            results.forEach { lookup ->
                lookup.term.glossaries.forEach { glossary ->
                    extractImagePaths(glossary.glossary)
                        .forEach { path -> requested += glossary.dictName to path }
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

            return runCatching {
                val root: Any = if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
                val found = linkedSetOf<String>()
                walkJson(root) { node ->
                    val isImageNode = node.optString("tag") == "img" || node.optString("type") == "image"
                    if (isImageNode) {
                        val candidate = node.optString("path").ifBlank { node.optString("src") }
                        candidate
                            .takeIf { it.isNotBlank() }
                            ?.let(found::add)
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
                        if (next is JSONObject || next is JSONArray) {
                            walkJson(next, onObject)
                        }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until element.length()) {
                        val next = element.opt(i)
                        if (next is JSONObject || next is JSONArray) {
                            walkJson(next, onObject)
                        }
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
            val usedBytes = rt.totalMemory() - rt.freeMemory()
            return usedBytes / (1024L * 1024L)
        }

        fun close() {
            session?.let(HoshiDicts::destroyLookupObject)
            session = null
        }
    }

    private data class LookupUiResult(
        val results: List<LookupResult>,
        val styles: List<DictionaryStyle>,
        val mediaDataUris: Map<String, String>,
        val error: String?,
        val diagnostics: LookupDiagnostics?,
        val debugDumpDir: String?,
    )

    private data class LookupDiagnostics(
        val totalMs: Long,
        val sessionCreateMs: Long,
        val rebuildMs: Long,
        val lookupMs: Long,
        val mediaMs: Long,
        val ramBeforeMb: Long,
        val ramAfterMb: Long,
        val resultCount: Int,
        val jsonGlossaryCount: Int,
        val cssBytes: Int,
    )
}
