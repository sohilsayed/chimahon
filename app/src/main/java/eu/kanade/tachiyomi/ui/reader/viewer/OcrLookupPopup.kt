package eu.kanade.tachiyomi.ui.reader.viewer

import android.webkit.WebView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import chimahon.DictionaryRepository
import chimahon.LookupResult
import chimahon.anki.AnkiCardCreator
import chimahon.anki.AnkiDroidBridge
import chimahon.anki.AnkiResult
import chimahon.anki.DelegatingWebViewBridge
import eu.kanade.tachiyomi.ui.dictionary.DictionaryEntryWebView
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.math.roundToInt

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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<LookupResult>>(emptyList()) }
    var styles by remember { mutableStateOf<List<chimahon.DictionaryStyle>>(emptyList()) }
    var mediaDataUris by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var existingExpressions by remember { mutableStateOf<Set<String>>(emptySet()) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val popupWidthPref by dictionaryPreferences.popupWidth().collectAsState()
    val popupHeightPref by dictionaryPreferences.popupHeight().collectAsState()
    val popupScalePref by dictionaryPreferences.popupScale().collectAsState()
    val ankiEnabled by dictionaryPreferences.ankiEnabled().collectAsState()
    val ankiDeck by dictionaryPreferences.ankiDeck().collectAsState()
    val ankiModel by dictionaryPreferences.ankiModel().collectAsState()
    val ankiFieldMap by dictionaryPreferences.ankiFieldMap().collectAsState()
    val ankiDupCheck by dictionaryPreferences.ankiDuplicateCheck().collectAsState()
    val ankiDupScope by dictionaryPreferences.ankiDuplicateScope().collectAsState()
    val ankiDupAction by dictionaryPreferences.ankiDuplicateAction().collectAsState()
    val ankiTags by dictionaryPreferences.ankiDefaultTags().collectAsState()

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val maxWidthDp = popupWidthPref.dp.coerceIn(280.dp, configuration.screenWidthDp.dp * 0.9f)
    val maxHeightDp = popupHeightPref.dp.coerceIn(200.dp, configuration.screenHeightDp.dp * 0.8f)

    val popupWidthPx = with(density) { maxWidthDp.toPx() }
    val popupHeightPx = with(density) { maxHeightDp.toPx() }

    val paddingPx = with(density) { 8.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }

    val delegatingBridge = remember(ankiEnabled) {
        if (ankiEnabled) DelegatingWebViewBridge { results } else null
    }

    if (ankiEnabled && delegatingBridge != null) {
        delegatingBridge.onAddToAnki = { result ->
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
                )
                when (ankiResult) {
                    is AnkiResult.Success -> context.toast(MR.strings.anki_card_added)
                    is AnkiResult.CardExists -> context.toast(MR.strings.anki_card_exists)
                    is AnkiResult.Error -> context.toast(
                        context.stringResource(MR.strings.anki_card_error, ankiResult.message),
                    )
                    is AnkiResult.NotConfigured -> context.toast(MR.strings.anki_not_configured)
                }
            }
        }
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

    LaunchedEffect(lookupString) {
        if (lookupString.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null

        scope.launch {
            try {
                val termPaths = getDictionaryPaths(context)
                val result = withContext(Dispatchers.IO) {
                    repository.lookup(lookupString, termPaths)
                }
                results = result.results
                styles = result.styles
                mediaDataUris = result.mediaDataUris
                errorMessage = result.error

                // Check which expressions are already in Anki
                if (ankiEnabled && ankiModel.isNotBlank() && results.isNotEmpty()) {
                    val bridge = AnkiDroidBridge(context)
                    val foundExpressions = mutableSetOf<String>()
                    val uniqueExpressions = results.map { it.term.expression }.distinct()
                    for (expr in uniqueExpressions) {
                        try {
                            val notes = bridge.findNotes(expr, ankiModel)
                            if (notes.isNotEmpty()) {
                                foundExpressions.add(expr)
                            }
                        } catch (_: Exception) {
                            // Ignore errors
                        }
                    }
                    existingExpressions = foundExpressions
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Lookup failed"
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Surface(
            modifier = modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .width(maxWidthDp)
                .heightIn(max = maxHeightDp)
                .pointerInput(Unit) {
                    detectTapGestures { }
                },
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            when {
                isLoading -> {}
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
                        existingExpressions = existingExpressions,
                        webViewProvider = { webView },
                        ankiBridge = delegatingBridge,
                        modifier = Modifier
                            .width(maxWidthDp)
                            .heightIn(min = 60.dp, max = maxHeightDp),
                    )
                }
            }
        }
    }
}
