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
import eu.kanade.tachiyomi.ui.dictionary.DictionaryEntryWebView
import eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences
import eu.kanade.tachiyomi.ui.dictionary.getDictionaryPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

import kotlin.math.roundToInt

@Composable
fun OcrLookupPopup(
    lookupString: String,
    onDismiss: () -> Unit,
    webView: WebView,
    repository: DictionaryRepository,
    anchorX: Float,
    anchorY: Float,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<chimahon.LookupResult>>(emptyList()) }
    var styles by remember { mutableStateOf<List<chimahon.DictionaryStyle>>(emptyList()) }
    var mediaDataUris by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val popupWidthPref by dictionaryPreferences.popupWidth().collectAsState()
    val popupHeightPref by dictionaryPreferences.popupHeight().collectAsState()
    val popupScalePref by dictionaryPreferences.popupScale().collectAsState()

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val maxWidthDp = popupWidthPref.dp.coerceIn(280.dp, configuration.screenWidthDp.dp * 0.9f)
    val maxHeightDp = popupHeightPref.dp.coerceIn(200.dp, configuration.screenHeightDp.dp * 0.8f)

    val popupWidthPx = with(density) { maxWidthDp.toPx() }
    val popupHeightPx = with(density) { maxHeightDp.toPx() }

    val paddingPx = with(density) { 8.dp.toPx() }
    val gapPx = with(density) { 8.dp.toPx() }

    // === Positioning Logic: Right → Left → Below → Above ===
    val spaceRight = screenWidthPx - anchorX - gapPx
    val spaceLeft = anchorX - gapPx
    val spaceBelow = screenHeightPx - anchorY - gapPx
    val spaceAbove = anchorY - gapPx

    data class PopupPosition(val x: Float, val y: Float)

    val position: PopupPosition = when {
        // Try RIGHT first
        spaceRight >= popupWidthPx + paddingPx -> {
            val x = anchorX + gapPx
            val y = (anchorY - popupHeightPx / 2)
                .coerceIn(paddingPx, screenHeightPx - popupHeightPx - paddingPx)
            PopupPosition(x, y)
        }
        // Try LEFT
        spaceLeft >= popupWidthPx + paddingPx -> {
            val x = anchorX - popupWidthPx - gapPx
            val y = (anchorY - popupHeightPx / 2)
                .coerceIn(paddingPx, screenHeightPx - popupHeightPx - paddingPx)
            PopupPosition(x, y)
        }
        // Try BELOW
        spaceBelow >= popupHeightPx + paddingPx -> {
            val x = (anchorX - popupWidthPx / 2)
                .coerceIn(paddingPx, screenWidthPx - popupWidthPx - paddingPx)
            val y = anchorY + gapPx
            PopupPosition(x, y)
        }
        // Fallback ABOVE
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
            } catch (e: Exception) {
                errorMessage = e.message ?: "Lookup failed"
            } finally {
                isLoading = false
            }
        }
    }

    // Full-screen backdrop - tap anywhere outside to dismiss
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Surface(
            modifier = modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .width(maxWidthDp)
                .heightIn(max = maxHeightDp)
                .pointerInput(Unit) {
                    // Consume taps on popup so they don't dismiss
                    detectTapGestures { }
                },
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            when {
                isLoading -> {
                    // Loading state - just show empty surface
                }
                errorMessage != null -> {
                    // Error will show in WebView header
                }
                results.isEmpty() -> {
                    // No results will show in WebView
                }
                else -> {
                    DictionaryEntryWebView(
                        results = results,
                        styles = styles,
                        mediaDataUris = mediaDataUris,
                        placeholder = "",
                        headerText = lookupString.take(20) + if (lookupString.length > 20) "…" else "",
                        popupScale = popupScalePref,
                        webViewProvider = { webView },
                        modifier = Modifier
                            .width(maxWidthDp)
                            .heightIn(min = 60.dp, max = maxHeightDp),
                    )
                }
            }
        }
    }
}