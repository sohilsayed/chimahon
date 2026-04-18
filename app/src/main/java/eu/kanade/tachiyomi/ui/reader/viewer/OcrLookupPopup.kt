package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import chimahon.DictionaryRepository
import chimahon.LookupResult
import chimahon.MediaInfo
import chimahon.anki.AnkiCardCreator
import chimahon.anki.AnkiResult
import chimahon.util.ImageEncoder
import eu.kanade.tachiyomi.ui.dictionary.DictionaryEntryWebView
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.TabInfo
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

/** One entry in the recursive-lookup history stack. */
private data class LookupFrame(
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
    mediaInfo: MediaInfo? = null,
    screenshot: Bitmap? = null,
    onRequestScreenshot: (() -> Bitmap?)? = null,
    onCropTriggered: ((Long, Int?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
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
    val popupScalePref by dictionaryPreferences.popupScale().collectAsState()
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

    /** Perform a dictionary lookup and push a new frame onto the stack. */
    fun pushLookup(query: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val termPaths = withContext(Dispatchers.IO) {
                    getDictionaryPaths(context, activeProfile)
                }
                val result = withContext(Dispatchers.IO) {
                    repository.lookup(query, termPaths)
                }
                var existing: Set<String> = emptySet()
                if (ankiEnabled && ankiModel.isNotBlank() && result.results.isNotEmpty()) {
                    val unique = result.results.map { it.term.expression }.distinct()
                    existing = withContext(Dispatchers.IO) {
                        AnkiCardCreator.checkExistingCards(context, unique, ankiModel)
                    }
                }
                val frame = LookupFrame(
                    query = query,
                    results = result.results,
                    styles = result.styles,
                    mediaDataUris = result.mediaDataUris,
                    existingExpressions = existing,
                )
                // Truncate any forward history past the current index, then push
                while (lookupStack.size > activeTabIndex + 1) lookupStack.removeAt(lookupStack.size - 1)
                lookupStack.add(frame)
                activeTabIndex = lookupStack.size - 1
                errorMessage = result.error
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Lookup failed"
            } finally {
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

    fun performAnkiLookup(index: Int, glossaryIndex: Int?) {
        val result = results.getOrNull(index) ?: return

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
                )
                if (ankiResult is AnkiResult.Success) {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onDismiss()
                        onCropTriggered.invoke(ankiResult.noteId, glossaryIndex)
                    }
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        when (ankiResult) {
                            is AnkiResult.CardExists -> context.toast(MR.strings.anki_card_exists)
                            is AnkiResult.OpenCard -> chimahon.anki.AnkiDroidBridge(context).guiEditNote(ankiResult.noteId)
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
                )
                when (ankiResult) {
                    is AnkiResult.Success -> context.toast(MR.strings.anki_card_added)
                    is AnkiResult.CardExists -> context.toast(MR.strings.anki_card_exists)
                    is AnkiResult.OpenCard -> chimahon.anki.AnkiDroidBridge(context).guiEditNote(ankiResult.noteId)
                    is AnkiResult.PermissionDenied -> context.toast(MR.strings.pref_anki_permission_denied)
                    is AnkiResult.Error -> context.toast(
                        context.stringResource(MR.strings.anki_card_error, ankiResult.message),
                    )
                    is AnkiResult.NotConfigured -> context.toast(MR.strings.anki_not_configured)
                }
            }
        }
    }

    val onAnkiLookup: ((Int, Int?) -> Unit)? = if (ankiEnabled) {
        { index, glossaryIndex ->
            performAnkiLookup(index, glossaryIndex)
        }
    } else {
        null
    }

    // === Positioning Logic: Right → Left → Below → Above ===
    val spaceRight = screenWidthPx - anchorX - gapPx
    val spaceLeft = anchorX - gapPx
    val spaceBelow = screenHeightPx - anchorY - gapPx
    val spaceAbove = anchorY - gapPx

    data class PopupPosition(val x: Float, val y: Float)

    val position: PopupPosition = when {
        spaceRight >= popupWidthPx + paddingPx -> {
            val x = anchorX + gapPx
            val y = (anchorY - popupHeightPx / 2)
                .coerceIn(paddingPx, screenHeightPx - popupHeightPx - paddingPx)
            PopupPosition(x, y)
        }
        spaceLeft >= popupWidthPx + paddingPx -> {
            val x = anchorX - popupWidthPx - gapPx
            val y = (anchorY - popupHeightPx / 2)
                .coerceIn(paddingPx, screenHeightPx - popupHeightPx - paddingPx)
            PopupPosition(x, y)
        }
        spaceBelow >= popupHeightPx + paddingPx -> {
            val x = (anchorX - popupWidthPx / 2)
                .coerceIn(paddingPx, screenWidthPx - popupWidthPx - paddingPx)
            val y = anchorY + gapPx
            PopupPosition(x, y)
        }
        else -> {
            val x = (anchorX - popupWidthPx / 2)
                .coerceIn(paddingPx, screenWidthPx - popupWidthPx - paddingPx)
            val y = (anchorY - popupHeightPx - gapPx).coerceAtLeast(paddingPx)
            PopupPosition(x, y)
        }
    }

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
        pushLookup(lookupString)
    }

    // Callbacks forwarded from the WebView bridge
    val onRecursiveLookup: (String) -> Unit = { word -> pushLookup(word) }
    val onTabSelect: (Int) -> Unit = { idx ->
        if (idx in lookupStack.indices) activeTabIndex = idx
    }
    val onBack: () -> Unit = {
        if (activeTabIndex > 0) activeTabIndex--
    }

    val outsideTapInteraction = remember { MutableInteractionSource() }

    Popup(
        offset = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
        onDismissRequest = { onDismiss() },
        properties = PopupProperties(
            focusable = false,
            dismissOnClickOutside = false, // Handled by scrim in ChimaReaderActivity
        ),
    ) {
        Surface(
            modifier = modifier
                .width(maxWidthDp)
                .height(maxHeightDp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    // Consume taps on the popup itself to prevent them from falling through to the reader
                },
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {}
                results.isEmpty() -> {}
                else -> {
                    DictionaryEntryWebView(
                        results = results,
                        styles = styles,
                        mediaDataUris = mediaDataUris,
                        placeholder = "",
                        headerText = lookupString.take(20) + if (lookupString.length > 20) "…" else "",
                        popupScale = popupScalePref,
                        showFrequencyHarmonic = showFreqHarmonic,
                        groupTerms = groupTerms,
                        showPitchDiagram = showPitchDiagram,
                        showPitchNumber = showPitchNumber,
                        showPitchText = showPitchText,
                        activeProfile = activeProfile,
                        existingExpressions = existingExpressions,
                        tabs = buildTabs(),
                        recursiveNavMode = recursiveNavMode,
                        webViewProvider = { webView },
                        onAnkiLookup = onAnkiLookup,
                        onRecursiveLookup = onRecursiveLookup,
                        onTabSelect = onTabSelect,
                        onBack = onBack,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
