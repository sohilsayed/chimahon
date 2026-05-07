package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.colorscheme.CustomColorScheme
import chimahon.DictionaryRepository
import chimahon.LookupResult
import chimahon.MediaInfo
import chimahon.anki.AnkiCardCreator
import chimahon.anki.AnkiResult
import chimahon.util.ImageEncoder
import eu.kanade.tachiyomi.ui.dictionary.DictionaryEntryWebView
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryColorScheme
import eu.kanade.tachiyomi.ui.dictionary.TabInfo
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import eu.kanade.tachiyomi.util.system.toast
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID
import kotlin.math.roundToInt

/** One entry in the recursive-lookup history stack. */
private data class LookupFrame(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val results: List<LookupResult>,
    val styles: List<chimahon.DictionaryStyle>,
    val mediaDataUris: Map<String, String>,
    val existingExpressions: Set<String>,
)


@Composable
fun OcrLookupPopup(
    lookupString: String,
    fullText: String,
    charOffset: Int,
    onDismiss: () -> Unit,
    webView: WebView,
    repository: DictionaryRepository,
    anchorX: Float,
    anchorY: Float,
    anchorWidth: Float = 0f,
    anchorHeight: Float = 0f,
    isVertical: Boolean,
    mediaInfo: MediaInfo? = null,
    screenshot: Bitmap? = null,
    onRequestScreenshot: (() -> Bitmap?)? = null,
    onCropTriggered: ((Long, Int?) -> Unit)? = null,
    initialLookupDeferred: kotlinx.coroutines.Deferred<chimahon.DictionaryRepository.LookupResult2>? = null,
    usePopup: Boolean = true,
    onTermMatched: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember {
        mutableStateOf(initialLookupDeferred != null && !initialLookupDeferred.isCompleted)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ── Lookup history stack ──────────────────────────────────────────────
    val lookupStack = remember { mutableStateListOf<LookupFrame>() }
    var activeTabIndex by remember { mutableIntStateOf(0) }

    val currentFrame: LookupFrame? = lookupStack.getOrNull(activeTabIndex)
    val results = currentFrame?.results ?: emptyList()
    val styles = currentFrame?.styles ?: emptyList()
    val mediaDataUris = currentFrame?.mediaDataUris ?: emptyMap()
    val existingExpressions = currentFrame?.existingExpressions ?: emptySet()

    /** Build the [TabInfo] list that is passed to the JS tab bar. */
    fun buildTabs(): List<TabInfo> = lookupStack.mapIndexed { i, frame ->
        TabInfo(
            label = frame.query.take(16),
            active = i == activeTabIndex,
        )
    }


    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val popupWidthPref by dictionaryPreferences.popupWidth().collectAsState()
    val popupHeightPref by dictionaryPreferences.popupHeight().collectAsState()
    val popupFontSizePref by dictionaryPreferences.fontSize().collectAsState()
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
    val cropMode = activeProfile.ankiCropMode

    val showFreqHarmonic by dictionaryPreferences.showFrequencyHarmonic().collectAsState()
    val groupTerms by dictionaryPreferences.groupTerms().collectAsState()
    val showPitchDiagram by dictionaryPreferences.showPitchDiagram().collectAsState()
    val showPitchNumber by dictionaryPreferences.showPitchNumber().collectAsState()
    val showPitchText by dictionaryPreferences.showPitchText().collectAsState()
    val customCss by dictionaryPreferences.customCss().collectAsState()
    val wordAudioEnabled by dictionaryPreferences.wordAudioEnabled().collectAsState()

    val systemIsDark = isSystemInDarkTheme()
    val amoled by dictionaryPreferences.themeDarkAmoled().collectAsState()
    val customColor by dictionaryPreferences.customColor().collectAsState()

    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val seedColor = if (customColor == 0) uiPreferences.colorTheme().get() else customColor
    val isDark = remember(seedColor, customColor, systemIsDark) {
        if (customColor != 0) Color(seedColor).luminance() < 0.5f else systemIsDark
    }
    val colorScheme = remember(isDark, amoled, seedColor) {
        getDictionaryColorScheme(isDark, amoled, seedColor)
    }
    val BgColor = remember(isDark, amoled, seedColor, colorScheme) {
        if (amoled && isDark) Color.Black else colorScheme.surface
    }

    /** Perform a dictionary lookup and push a new frame onto the stack. */
    fun pushLookup(query: String, isRecursive: Boolean = false, deferredResult: kotlinx.coroutines.Deferred<chimahon.DictionaryRepository.LookupResult2>? = null) {
        val cleanQuery = if (isRecursive) {
            query.replace(Regex("[\\s\\p{Punct}「」『』【】（）〔〕［］｛｝〈〉《》…、。！？!?]+"), "").trim()
        } else {
            query.trim()
        }

        if (isRecursive) {
            if (cleanQuery.isBlank()) return
            // Ignore if entirely ascii/english letters and numbers
            if (cleanQuery.all { it.code <= 127 }) return
        }

        val finalQuery = if (isRecursive) cleanQuery else query

        fun handleResult(result: chimahon.DictionaryRepository.LookupResult2, phaseStart: Long) {
            if (isRecursive && result.results.isEmpty()) {
                isLoading = false
                return
            }

            // Create frame and push immediately — popup shows NOW
            val frame = LookupFrame(
                id = UUID.randomUUID().toString(),
                query = finalQuery,
                results = result.results,
                styles = result.styles,
                mediaDataUris = result.mediaDataUris,
                existingExpressions = emptySet(),
            )

            // Truncate any forward history past the current index, then push
            while (lookupStack.size > activeTabIndex + 1) lookupStack.removeAt(lookupStack.size - 1)
            lookupStack.add(frame)
            activeTabIndex = lookupStack.size - 1
            errorMessage = result.error

            // Hide loading spinner — popup is visible
            isLoading = false

            if (!isRecursive && result.results.isNotEmpty()) {
                val firstMatched = result.results.firstOrNull()?.matched
                if (firstMatched != null) {
                    val charCount = firstMatched.codePointCount(0, firstMatched.length)
                    scope.launch(Dispatchers.Main) {
                        onTermMatched?.invoke(charCount)
                    }
                }
            }

            // Anki duplicate check runs in background, doesn't block UI
            if (ankiEnabled && result.results.isNotEmpty()) {
                val uniqueExpressions = result.results.map { it.term.expression }.distinct()
                scope.launch(Dispatchers.IO) {
                    val existing = AnkiCardCreator.checkExistingCards(
                        context = context,
                        expressions = uniqueExpressions,
                        deckName = ankiDeck,
                        dupScope = ankiDupScope,
                    )
                    withContext(Dispatchers.Main) {
                        val frameIndex = lookupStack.indexOfFirst { it.id == frame.id }
                        if (frameIndex >= 0) {
                            lookupStack[frameIndex] = lookupStack[frameIndex].copy(existingExpressions = existing)
                        }
                        Log.i(
                            "DictionaryPopup",
                            "anki_check_ms=${android.os.SystemClock.elapsedRealtime() - phaseStart} expressions=${uniqueExpressions.size}",
                        )
                    }
                }
            }

            // Load media in background
            scope.launch(Dispatchers.IO) {
                val media = repository.loadMediaAsync(finalQuery, result.results)
                if (media.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val frameIndex = lookupStack.indexOfFirst { it.id == frame.id }
                        if (frameIndex >= 0) {
                            lookupStack[frameIndex] = lookupStack[frameIndex].copy(mediaDataUris = media)
                        }
                    }
                }
            }
        }

        val deferred = deferredResult ?: scope.async(Dispatchers.IO) {
            val termPaths = getDictionaryPaths(context, activeProfile)
            repository.lookup(finalQuery, termPaths)
        }

        if (!deferred.isCompleted) {
            // Must await in a coroutine
            scope.launch {
                isLoading = true
                errorMessage = null
                val phaseStart = android.os.SystemClock.elapsedRealtime()
                try {
                    val result = deferred.await()
                    handleResult(result, phaseStart)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    errorMessage = e.message ?: "Lookup failed"
                    isLoading = false
                }
            }
        } else {
            // Synchronous lookup (no nested coroutine)
            // Session is warm, lookup is fast (~10-20ms) — no need for Dispatchers.IO hop
            errorMessage = null
            val phaseStart = android.os.SystemClock.elapsedRealtime()
            try {
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                val result = deferred.getCompleted()
                handleResult(result, phaseStart)
            } catch (e: Exception) {
                errorMessage = e.message ?: "Lookup failed"
                isLoading = false
            }
        }
    }
    val recursiveNavMode by dictionaryPreferences.recursiveLookupMode().collectAsState()

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val maxWidthDp = popupWidthPref.dp.coerceIn(280.dp, configuration.screenWidthDp.dp * 0.9f)
    val maxHeightDp = popupHeightPref.dp.coerceIn(200.dp, configuration.screenHeightDp.dp * 0.8f)

    val popupWidthPx = with(density) { maxWidthDp.toPx() }
    val popupHeightPx = with(density) { maxHeightDp.toPx() }

    val paddingPx = with(density) { 8.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }

    val screenshotFieldMapped = remember(ankiFieldMap) {
        try {
            val fieldMap = org.json.JSONObject(ankiFieldMap)
            fieldMap.keys().asSequence().any { key ->
                val value = fieldMap.getString(key)
                value.contains(chimahon.anki.Marker.SCREENSHOT)
            }
        } catch (e: Exception) {
            false
        }
    }

    fun performAnkiLookup(
        index: Int,
        glossaryIndex: Int?,
        selectedDict: String? = null,
        popupSelection: String? = null,
        forceOpen: Boolean = false,
    ) {
        val result = results.getOrNull(index) ?: return

        // Local helper to update the state, which triggers the optimized JS call via DictionaryEntryWebView
        fun updateStatus(expression: String) {
            val frame = currentFrame ?: return
            val frameIndex = lookupStack.indexOfFirst { it.id == frame.id }
            if (frameIndex >= 0) {
                val newExisting = frame.existingExpressions + expression
                lookupStack[frameIndex] = lookupStack[frameIndex].copy(existingExpressions = newExisting)
            }
        }

        val shouldUseCropMode = screenshotFieldMapped && cropMode == "crop" && onCropTriggered != null

        if (shouldUseCropMode) {
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
                    sentence = fullText,
                    offset = charOffset,
                    media = mediaInfo,
                    glossaryIndex = glossaryIndex,
                    selection = result.matched,
                    selectedDict = selectedDict,
                    popupSelection = popupSelection,
                    styles = styles,
                    forceOpen = forceOpen,
                )
                if (ankiResult is AnkiResult.Success || ankiResult is AnkiResult.CardExists || ankiResult is AnkiResult.OpenCard) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        updateStatus(result.term.expression)
                        onDismiss()
                        if (ankiResult is AnkiResult.Success) {
                            onCropTriggered.invoke(ankiResult.noteId, glossaryIndex)
                        }
                    }
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        when (ankiResult) {
                            is AnkiResult.PermissionDenied -> context.toast(MR.strings.pref_anki_permission_denied)
                            is AnkiResult.Error -> context.toast(
                                context.stringResource(MR.strings.anki_card_error, ankiResult.message),
                            )
                            is AnkiResult.NotConfigured -> context.toast(MR.strings.anki_not_configured)
                            else -> {}
                        }
                    }
                }
            }
        } else {
            scope.launch {
                val encoding = onRequestScreenshot?.invoke()?.let { ImageEncoder.encode(it) }
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
                    sentence = fullText,
                    offset = charOffset,
                    media = mediaInfo,
                    glossaryIndex = glossaryIndex,
                    screenshotBytes = encoding?.bytes,
                    selection = result.matched,
                    selectedDict = selectedDict,
                    popupSelection = popupSelection,
                    styles = styles,
                    forceOpen = forceOpen,
                )
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    when (ankiResult) {
                        is AnkiResult.Success -> {
                            updateStatus(result.term.expression)
                            context.toast(MR.strings.anki_card_added)
                        }
                        is AnkiResult.CardExists -> {
                            updateStatus(result.term.expression)
                            context.toast(MR.strings.anki_card_exists)
                        }
                        is AnkiResult.OpenCard -> {
                            updateStatus(result.term.expression)
                            chimahon.anki.AnkiDroidBridge(context).guiEditNote(ankiResult.noteId)
                        }
                        is AnkiResult.PermissionDenied -> context.toast(MR.strings.pref_anki_permission_denied)
                        is AnkiResult.Error -> context.toast(
                            context.stringResource(MR.strings.anki_card_error, ankiResult.message),
                        )
                        is AnkiResult.NotConfigured -> context.toast(MR.strings.anki_not_configured)
                    }
                }
            }
        }
    }

    val onAnkiLookup: ((Int, Int?, String?, String?, Boolean) -> Unit)? = if (ankiEnabled) {
        { index, glossaryIndex, selectedDict, popupSelection, forceOpen ->
            performAnkiLookup(index, glossaryIndex, selectedDict, popupSelection, forceOpen)
        }
    } else {
        null
    }

    // === Popup Positioning: evaluate all 4 sides, pick the best ===
    data class PopupLayoutResult(val x: Float, val y: Float, val widthPx: Float, val heightPx: Float)

    val layoutResult = remember(
        anchorX, anchorY, anchorWidth, anchorHeight,
        screenWidthPx, screenHeightPx, popupWidthPx, popupHeightPx, isVertical,
    ) {
        val w = minOf(popupWidthPx, screenWidthPx - paddingPx * 2)
        val h = minOf(popupHeightPx, screenHeightPx - paddingPx * 2)

        val ax = anchorX
        val ay = anchorY
        val aw = anchorWidth
        val ah = anchorHeight
        val acx = ax + aw / 2f
        val acy = ay + ah / 2f

        // Candidate positions (top-left of popup) for each side
        // format: [x, y, isVerticalSide]
        val candidates = arrayOf(
            floatArrayOf(ax + aw + gapPx, acy - h / 2f, 1f), // Right
            floatArrayOf(ax - w - gapPx, acy - h / 2f, 1f), // Left
            floatArrayOf(acx - w / 2f, ay + ah + gapPx, 0f), // Below
            floatArrayOf(acx - w / 2f, ay - h - gapPx, 0f), // Above
        )

        fun overlapArea(px: Float, py: Float): Float {
            if (aw <= 0f || ah <= 0f) return 0f
            val ox = minOf(px + w, ax + aw) - maxOf(px, ax)
            val oy = minOf(py + h, ay + ah) - maxOf(py, ay)
            return if (ox > 0f && oy > 0f) ox * oy else 0f
        }

        var bestScore = Float.MAX_VALUE
        var bestX = paddingPx
        var bestY = paddingPx

        for (i in candidates.indices) {
            val c = candidates[i]
            val rawX = c[0]
            val rawY = c[1]

            // Clamp to screen boundaries with padding
            val clampedX = rawX.coerceIn(paddingPx, screenWidthPx - w - paddingPx)
            val clampedY = rawY.coerceIn(paddingPx, screenHeightPx - h - paddingPx)

            val area = overlapArea(clampedX, clampedY)

            // Penalties:
            // 1. Overlap is the primary penalty (Area weighted heavily)
            val overlapPenalty = area * 15f

            // 2. Clamping penalty (how much we had to shift the popup)
            val shiftX = Math.abs(clampedX - rawX)
            val shiftY = Math.abs(clampedY - rawY)
            val clampingPenalty = (shiftX + shiftY) * 2f

            // 3. Axis & Side preference
            // Candidates: 0=Right, 1=Left, 2=Below, 3=Above
            val sideBias = if (isVertical) {
                when (i) {
                    0, 3 -> -1500f // Right or Above (Already read area for vertical)
                    1, 2 -> -1200f // Left or Below
                    else -> 0f
                }
            } else {
                when (i) {
                    1, 3 -> -1500f // Left or Above (Already read area for horizontal)
                    0, 2 -> -1200f // Right or Below
                    else -> 0f
                }
            }

            val score = overlapPenalty + clampingPenalty + sideBias

            if (score < bestScore) {
                bestScore = score
                bestX = clampedX
                bestY = clampedY
            }
        }

        PopupLayoutResult(bestX, bestY, w, h)
    }

    val actualWidthDp = with(density) { layoutResult.widthPx.toDp() }
    val actualHeightDp = with(density) { layoutResult.heightPx.toDp() }


    LaunchedEffect(lookupString, ankiEnabled, ankiModel) {
        if (lookupString.isBlank()) {
            lookupStack.clear()
            activeTabIndex = 0
            isLoading = false
            return@LaunchedEffect
        }
        // Reset the stack and load the initial term
        lookupStack.clear()
        activeTabIndex = 0
        pushLookup(lookupString, deferredResult = initialLookupDeferred)
    }

    // Callbacks forwarded from the WebView bridge
    val onRecursiveLookup: (String) -> Unit = { word -> pushLookup(word, isRecursive = true) }
    val onTabSelect: (Int) -> Unit = { idx ->
        if (idx in lookupStack.indices) activeTabIndex = idx
    }
    val onBack: () -> Unit = {
        if (activeTabIndex > 0) activeTabIndex--
    }

    val outsideTapInteraction = remember { MutableInteractionSource() }

    @Composable
    fun PopupContent() {
        val swipeThreshold = with(density) { 56.dp.toPx() }
        Surface(
            modifier = modifier
                .width(actualWidthDp)
                .height(actualHeightDp)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val ignoreHeight = with(density) { 48.dp.toPx() }
                            if (down.position.y < ignoreHeight) continue

                            var totalDragX = 0f
                            var totalDragY = 0f
                            try {
                                drag(down.id) { change ->
                                    val delta = change.positionChange()
                                    totalDragX += delta.x
                                    totalDragY += delta.y
                                    // If horizontal swipe is dominant and exceeds threshold, dismiss
                                    if (kotlin.math.abs(totalDragX) > swipeThreshold && 
                                        kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.5f
                                    ) {
                                        onDismiss()
                                        throw CancellationException("Dismissed by swipe")
                                    }
                                }
                            } catch (e: CancellationException) {
                                if (e.message != "Dismissed by swipe") throw e
                            }
                        }
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    // Consume taps on the popup itself to prevent them from falling through to the reader
                },
            shape = RoundedCornerShape(8.dp),
            color = BgColor,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                DictionaryEntryWebView(
                    results = results,
                    styles = styles,
                    mediaDataUris = mediaDataUris,
                    placeholder = if (isLoading) "" else "No results found",
                    headerText = lookupString.take(20) + if (lookupString.length > 20) "…" else "",
                    fontSize = popupFontSizePref,
                    showFrequencyHarmonic = showFreqHarmonic,
                    groupTerms = groupTerms,
                    showPitchDiagram = showPitchDiagram,
                    showPitchNumber = showPitchNumber,
                    showPitchText = showPitchText,
                    activeProfile = activeProfile,
                    existingExpressions = existingExpressions,
                    tabs = buildTabs(),
                    recursiveNavMode = recursiveNavMode,
                    customCss = customCss,
                    wordAudioEnabled = wordAudioEnabled,
                    webViewProvider = { webView },
                    onAnkiLookup = onAnkiLookup,
                    onRecursiveLookup = onRecursiveLookup,
                    onTabSelect = onTabSelect,
                    onBack = onBack,
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxSize(),
                )

                if (isLoading && results.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp).align(Alignment.Center)
                    )
                }
            }
        }
    }

    if (usePopup) {
        Popup(
            offset = IntOffset(layoutResult.x.roundToInt(), layoutResult.y.roundToInt()),
            onDismissRequest = { onDismiss() },
            properties = PopupProperties(
                focusable = false,
                dismissOnClickOutside = false, // Handled by scrim in ChimaReaderActivity
            ),
        ) {
            PopupContent()
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(
                    x = with(LocalDensity.current) { layoutResult.x.toDp() },
                    y = with(LocalDensity.current) { layoutResult.y.toDp() },
                )
        ) {
            PopupContent()
        }
    }
}
