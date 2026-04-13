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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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
import eu.kanade.tachiyomi.ui.dictionary.TabInfo
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.apache.http.client.methods.RequestBuilder.put
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

/** One entry in the recursive-lookup history stack (shared by tab and popup). */
private data class TabLookupFrame(
    val query: String,
    val results: List<LookupResult>,
    val styles: List<DictionaryStyle>,
    val mediaDataUris: Map<String, String>,
    val existingExpressions: Set<String>,
)


fun getDictionaryPaths(context: android.content.Context, activeProfileOverride: chimahon.anki.AnkiProfile? = null): List<String> {
    val dictionariesDir = File(context.getExternalFilesDir(null), "dictionaries")
    if (!dictionariesDir.exists()) return emptyList()

    val allDicts = dictionariesDir.listFiles()
        ?.filter { it.isDirectory }
        ?.mapNotNull { it.name }
        .orEmpty()

    if (allDicts.isEmpty()) return emptyList()

    val prefs = try {
        DictionaryPreferences(Injekt.get())
    } catch (_: Exception) {
        return allDicts.map { File(dictionariesDir, it).absolutePath }
    }

    val activeProfile = activeProfileOverride ?: run {
        // One-time migration: create "Default" profile from legacy flat keys.
        prefs.profileStore.migrateIfEmpty(
            defaultName = "Default",
            legacyValues = chimahon.anki.AnkiProfileStore.LegacyAnkiValues(
                deck = prefs.legacyAnkiDeck().get(),
                model = prefs.legacyAnkiModel().get(),
                fieldMap = prefs.legacyAnkiFieldMap().get(),
                tags = prefs.legacyAnkiDefaultTags().get(),
                dupCheck = prefs.legacyAnkiDuplicateCheck().get(),
                dupScope = prefs.legacyAnkiDuplicateScope().get(),
                dupAction = prefs.legacyAnkiDuplicateAction().get(),
                cropMode = prefs.legacyAnkiCropMode().get(),
            ),
            allDictNames = allDicts,
        )
        prefs.profileStore.getActiveProfile()
    }

    // Per-profile dictionary order: start with the profile's order,
    // then append any new dicts added to disk since the profile was last saved.
    val profileOrder = activeProfile.dictionaryOrder.filter { it in allDicts }
    val newDicts = allDicts.filter { it !in activeProfile.dictionaryOrder }
    val orderedNames = profileOrder + newDicts

    // Per-profile activation: empty set means all enabled.
    val enabled = activeProfile.enabledDictionaries
    val finalNames = if (enabled.isEmpty()) orderedNames else orderedNames.filter { it in enabled }

    return finalNames.map { File(dictionariesDir, it).absolutePath }
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
        var hasSearched by remember { mutableStateOf(false) }
        var shouldMountWebView by remember { mutableStateOf(false) }
        var searchJob by remember { mutableStateOf<Job?>(null) }
        var retainedWebView by remember { mutableStateOf<WebView?>(null) }
        val focusManager = LocalFocusManager.current
        val focusRequester = remember { FocusRequester() }

        val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
        val rawProfiles by dictionaryPreferences.rawProfiles().collectAsState()
        val rawActiveProfileId by dictionaryPreferences.rawActiveProfileId().collectAsState()
        val profileStore = dictionaryPreferences.profileStore
        val activeProfile = remember(rawProfiles, rawActiveProfileId) { profileStore.getActiveProfile() }

        val ankiEnabled = activeProfile.ankiEnabled
        val ankiDeck = activeProfile.ankiDeck
        val ankiModel = activeProfile.ankiModel
        val ankiFieldMap = activeProfile.ankiFieldMap
        val ankiDupCheck = activeProfile.ankiDupCheck
        val ankiDupScope = activeProfile.ankiDupScope
        val ankiDupAction = activeProfile.ankiDupAction
        val ankiTags = activeProfile.ankiTags

        val showFreqHarmonic by dictionaryPreferences.showFrequencyHarmonic().collectAsState()
        val showPitchDiagram by dictionaryPreferences.showPitchDiagram().collectAsState()
        val showPitchNumber by dictionaryPreferences.showPitchNumber().collectAsState()
        val showPitchText by dictionaryPreferences.showPitchText().collectAsState()
        val groupTerms by dictionaryPreferences.groupTerms().collectAsState()
        val recursiveNavMode by dictionaryPreferences.recursiveLookupMode().collectAsState()

        // ── Lookup history stack ──────────────────────────────────────────────
        val lookupStack = remember { mutableStateListOf<TabLookupFrame>() }
        var activeTabIndex by remember { mutableIntStateOf(0) }

        val currentFrame: TabLookupFrame? = lookupStack.getOrNull(activeTabIndex)
        val results: List<LookupResult> = currentFrame?.results ?: emptyList()
        val styles: List<DictionaryStyle> = currentFrame?.styles ?: emptyList()
        val mediaDataUris: Map<String, String> = currentFrame?.mediaDataUris ?: emptyMap()
        val existingExpressions: Set<String> = currentFrame?.existingExpressions ?: emptySet()

        fun buildTabs(): List<TabInfo> = lookupStack.mapIndexed { i, frame ->
            TabInfo(label = frame.query.take(16), active = i == activeTabIndex)
        }

        /** Push a lookup onto the stack; cancels any in-flight search first. */
        fun stackLookup(rawQuery: String) {
            searchJob?.cancel()
            searchJob = scope.launch {
                isLoading = true
                errorMessage = null

                val lookupResult = performLookup(
                    query = rawQuery,
                    context = context,
                    activeProfile = activeProfile,
                    sessionManager = sessionManager,
                )

                var existing: Set<String> = emptySet()
                if (ankiEnabled && ankiModel.isNotBlank() && lookupResult.results.isNotEmpty()) {
                    val unique = lookupResult.results.map { it.term.expression }.distinct()
                    existing = AnkiCardCreator.checkExistingCards(context, unique, ankiModel)
                }

                val frame = TabLookupFrame(
                    query = rawQuery,
                    results = lookupResult.results,
                    styles = lookupResult.styles,
                    mediaDataUris = lookupResult.mediaDataUris,
                    existingExpressions = existing,
                )
                // Truncate forward history, then push
                while (lookupStack.size > activeTabIndex + 1) lookupStack.removeAt(lookupStack.size - 1)
                lookupStack.add(frame)
                activeTabIndex = lookupStack.size - 1

                errorMessage = lookupResult.error
                hasSearched = true
                isLoading = false
            }
        }

        // ── Auto-search effect ────────────────────────────────────────────────
        LaunchedEffect(query) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                if (hasSearched) {
                    lookupStack.clear()
                    activeTabIndex = 0
                    hasSearched = false
                }
                return@LaunchedEffect
            }
            // Debounce for 300ms
            delay(300)

            // If we are at the root or typing in the main box, reset history to start a new search
            // (Unless it was already the same query in the current frame)
            if (currentFrame?.query != trimmed) {
                lookupStack.clear()
                activeTabIndex = 0
                stackLookup(trimmed)
            }
        }

        // ── Auto-focus effect ──────────────────────────────────────────────────
        LaunchedEffect(Unit) {
            // Wait for tab animation/composition to settle
            delay(300)
            focusRequester.requestFocus()
        }

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
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    label = { Text(stringResource(MR.strings.action_search)) },
                    placeholder = { Text(stringResource(MR.strings.action_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = stringResource(MR.strings.action_reset),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val trimmed = query.trim()
                            if (trimmed.isNotEmpty()) {
                                lookupStack.clear()
                                activeTabIndex = 0
                                stackLookup(trimmed)
                                focusManager.clearFocus()
                            }
                        },
                    ),
                )

                IconButton(
                    onClick = {
                        val trimmedQuery = query.trim()
                        if (trimmedQuery.isEmpty()) {
                            lookupStack.clear()
                            activeTabIndex = 0
                            errorMessage = null
                            hasSearched = true
                        } else {
                            lookupStack.clear()
                            activeTabIndex = 0
                            stackLookup(trimmedQuery)
                        }
                        focusManager.clearFocus()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(MR.strings.action_search),
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
                    groupTerms = groupTerms,
                    showPitchDiagram = showPitchDiagram,
                    showPitchNumber = showPitchNumber,
                    showPitchText = showPitchText,
                    activeProfile = activeProfile,
                    existingExpressions = existingExpressions,
                    tabs = buildTabs(),
                    recursiveNavMode = recursiveNavMode,
                    webViewProvider = { context ->
                        retainedWebView ?: WebView(context).also { retainedWebView = it }
                    },
                    onAnkiLookup = onAnkiLookup,
                    onRecursiveLookup = { word -> stackLookup(word) },
                    onTabSelect = { idx ->
                        if (idx in lookupStack.indices) activeTabIndex = idx
                    },
                    onBack = {
                        if (activeTabIndex > 0) activeTabIndex--
                    },
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
        activeProfile: chimahon.anki.AnkiProfile,
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

            val dictionaryPaths = getDictionaryPaths(context, activeProfile)

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
