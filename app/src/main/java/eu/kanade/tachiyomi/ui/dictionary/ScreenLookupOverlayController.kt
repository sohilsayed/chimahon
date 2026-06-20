package eu.kanade.tachiyomi.ui.dictionary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import chimahon.DictionaryRepository
import chimahon.ocr.OcrLanguage
import eu.kanade.tachiyomi.data.ocr.recognizePage
import eu.kanade.tachiyomi.ui.reader.viewer.OcrLookupPopup
import eu.kanade.tachiyomi.ui.reader.viewer.OcrTextBlock
import eu.kanade.tachiyomi.ui.reader.viewer.extractOcrLookupString
import eu.kanade.tachiyomi.ui.reader.viewer.fullText
import eu.kanade.tachiyomi.ui.reader.viewer.isLookupStartChar
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import chimahon.MediaInfo

private const val TAP_HINT_DURATION_MS = 1_200L

internal class ScreenLookupOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onDismiss: () -> Unit,
    private val onRecapture: () -> Unit,
) {
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var screenshot: Bitmap? = null
    private var cachedWebView: WebView? = null
    private var cachedProfile: chimahon.anki.AnkiProfile? = null

    val isShowing: Boolean
        get() = overlayView != null

    fun show(nextScreenshot: Bitmap) {
        dismiss(recycleScreenshot = true, notify = false)
        screenshot = nextScreenshot

        val profile = cachedProfile
            ?: Injekt.get<DictionaryPreferences>().profileStore.getActiveProfile()
                .also { cachedProfile = it }
        val webView = cachedWebView
            ?: prepareDictionaryWebViewShell(context, languageCode = profile.languageCode)
                .also { cachedWebView = it }

        val owner = OverlayLifecycleOwner().also {
            it.performCreate()
            it.performStart()
            it.performResume()
        }
        lifecycleOwner = owner

        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setComposeContent {
                ScreenLookupOverlay(
                    screenshot = nextScreenshot,
                    webView = webView,
                    activeProfile = profile,
                    onClose = { dismiss() },
                    onRecapture = {
                        dismiss(recycleScreenshot = true, notify = false)
                        onRecapture()
                    },
                )
            }
        }

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    fun dismiss(recycleScreenshot: Boolean = true, notify: Boolean = true) {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        lifecycleOwner?.performDestroy()
        lifecycleOwner = null
        if (recycleScreenshot) {
            screenshot?.takeUnless { it.isRecycled }?.recycle()
        }
        screenshot = null
        if (notify) onDismiss()
    }

    fun release() {
        dismiss(recycleScreenshot = true, notify = false)
        cachedWebView?.runCatching { destroy() }
        cachedWebView = null
        cachedProfile = null
    }
}

private class OverlayLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = store

    fun performCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun performStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun performResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun performDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

private data class ScreenLookupSelection(
    val block: OcrTextBlock,
    val lookupString: String,
    val sentence: String,
    val sentenceOffset: Int,
    val anchorX: Float,
    val anchorY: Float,
    val anchorWidth: Float,
    val anchorHeight: Float,
)

@Composable
internal fun ScreenLookupOverlay(
    screenshot: Bitmap,
    webView: WebView,
    activeProfile: chimahon.anki.AnkiProfile,
    onClose: () -> Unit,
    onRecapture: () -> Unit,
    type: String = "screen",
    mediaInfo: MediaInfo? = null,
    titleId: String? = null,
    onRequestSentenceAudio: (suspend () -> ByteArray?)? = null,
) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val repository = remember { Injekt.get<DictionaryRepository>() }
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val boxScaleX = dictionaryPreferences.ocrBoxScaleX().get()
    val boxScaleY = dictionaryPreferences.ocrBoxScaleY().get()

    var matchedCharCount by remember { mutableIntStateOf(0) }
    var matchOffset by remember { mutableIntStateOf(0) }
    var blocks by remember { mutableStateOf<List<OcrTextBlock>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<ScreenLookupSelection?>(null) }
    var showTapHint by remember { mutableStateOf(false) }
    var lookupNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(screenshot, activeProfile.languageCode) {
        isLoading = true
        error = null
        blocks = emptyList()
        showTapHint = false
        val language = OcrLanguage.entries.find {
            it.bcp47.equals(activeProfile.languageCode, ignoreCase = true)
        } ?: OcrLanguage.JAPANESE

        runCatching {
            withContext(Dispatchers.Default) {
                recognizePage(screenshot, language)
                    .toScreenLookupBlocks(language.bcp47)
            }
        }.onSuccess {
            blocks = it
            if (it.isEmpty()) {
                error = context.contextStringResource(MR.strings.screen_lookup_no_text)
            } else {
                showTapHint = true
            }
        }.onFailure {
            error = it.message ?: context.contextStringResource(MR.strings.screen_lookup_capture_failed)
        }
        isLoading = false

        if (showTapHint) {
            delay(TAP_HINT_DURATION_MS)
            showTapHint = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val widthPx = with(localDensity) { maxWidth.toPx() }
        val heightPx = with(localDensity) { maxHeight.toPx() }

        Image(
            bitmap = screenshot.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(blocks, widthPx, heightPx) {
                    detectTapGestures { offset ->
                        val tapped = blocks.firstOrNull { block ->
                            offset.x >= block.xmin * widthPx &&
                                offset.x <= block.xmax * widthPx &&
                                offset.y >= block.ymin * heightPx &&
                                offset.y <= block.ymax * heightPx
                        }

                        if (tapped == null) {
                            selection = null
                            showTapHint = false
                            return@detectTapGestures
                        }

                        val tapX = (offset.x / widthPx).coerceIn(0f, 1f)
                        val tapY = (offset.y / heightPx).coerceIn(0f, 1f)
                        val charOffset = tapped.screenLookupCharOffset(tapX, tapY)
                        val text = tapped.fullText
                        if (charOffset !in text.indices || !isLookupStartChar(text[charOffset])) {
                            selection = null
                            showTapHint = false
                            return@detectTapGestures
                        }

                        val lookupString = extractOcrLookupString(text, charOffset)
                        if (lookupString.isBlank()) {
                            selection = null
                            showTapHint = false
                            return@detectTapGestures
                        }

                        lookupNonce++
                        showTapHint = false
                        matchedCharCount = 0
                        matchOffset = 0
                        selection = ScreenLookupSelection(
                            block = tapped,
                            lookupString = lookupString,
                            sentence = tapped.fullText,
                            sentenceOffset = charOffset,
                            anchorX = tapped.xmin * widthPx,
                            anchorY = tapped.ymin * heightPx,
                            anchorWidth = (tapped.xmax - tapped.xmin) * widthPx,
                            anchorHeight = (tapped.ymax - tapped.ymin) * heightPx,
                        )
                    }
                },
        ) {
            val active = selection?.block
            val activeMatchCount = matchedCharCount
            val activeMatchOffset = matchOffset
            val activeSelection = selection

            val borderColor = Color(0, 170, 255, 180)
            val highlightColor = Color(130, 150, 200, 100)

            blocks.forEach { block ->
                val isActive = block == active
                val geometries = block.lineGeometries

                if (geometries != null && geometries.size == block.lines.size) {
                    geometries.forEachIndexed { _, geo ->
                        val centerX = (geo.xmin + geo.xmax) / 2f
                        val centerY = (geo.ymin + geo.ymax) / 2f
                        val tightW = (geo.xmax - geo.xmin).coerceAtLeast(0.001f)
                        val tightH = (geo.ymax - geo.ymin).coerceAtLeast(0.001f)

                        val scaledW = tightW * boxScaleX
                        val scaledH = tightH * boxScaleY
                        val left = (centerX - scaledW / 2f) * size.width
                        val top = (centerY - scaledH / 2f) * size.height
                        val w = scaledW * size.width
                        val h = scaledH * size.height

                        drawRect(
                            color = if (isActive) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                        )
                        drawRect(
                            color = borderColor,
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = if (isActive) 2.dp.toPx() else 1.dp.toPx()),
                        )

                        if (isActive && activeMatchCount > 0 && activeSelection != null) {
                            val orderedIds = block.lines.indices.toList()
                            var accumulated = 0
                            for (oi in orderedIds.indices) {
                                val rawIdx = orderedIds[oi]
                                val lineLen = block.lines[rawIdx].length
                                val lineEnd = accumulated + lineLen
                                val absStart = activeSelection.sentenceOffset + activeMatchOffset
                                val absEnd = absStart + activeMatchCount
                                if (absStart < lineEnd && absEnd > accumulated) {
                                    val overlapL = maxOf(absStart, accumulated)
                                    val overlapR = minOf(absEnd, lineEnd)
                                    val startFrac = (overlapL - accumulated).toFloat() / lineLen.coerceAtLeast(1)
                                    val endFrac = (overlapR - accumulated).toFloat() / lineLen.coerceAtLeast(1)

                                    val lGeo = geometries[rawIdx]
                                    val lCx = (lGeo.xmin + lGeo.xmax) / 2f
                                    val lCy = (lGeo.ymin + lGeo.ymax) / 2f
                                    val lTw = (lGeo.xmax - lGeo.xmin).coerceAtLeast(0.001f)
                                    val lTh = (lGeo.ymax - lGeo.ymin).coerceAtLeast(0.001f)
                                    val lSw = lTw * boxScaleX
                                    val lSh = lTh * boxScaleY
                                    val lLeft = (lCx - lSw / 2f) * size.width
                                    val lTop = (lCy - lSh / 2f) * size.height
                                    val lW = lSw * size.width
                                    val lH = lSh * size.height

                                    val origW = lW / boxScaleX
                                    val origH = lH / boxScaleY
                                    val padX = (lW - origW) / 2f
                                    val padY = (lH - origH) / 2f

                                    if (block.vertical) {
                                        drawRect(
                                            color = highlightColor,
                                            topLeft = Offset(lLeft, lTop + padY + origH * startFrac),
                                            size = Size(lW, origH * (endFrac - startFrac)),
                                        )
                                    } else {
                                        drawRect(
                                            color = highlightColor,
                                            topLeft = Offset(lLeft + padX + origW * startFrac, lTop),
                                            size = Size(origW * (endFrac - startFrac), lH),
                                        )
                                    }
                                    break
                                }
                                accumulated = lineEnd
                            }
                        }
                    }
                } else {
                    val blockLeft = block.xmin * size.width
                    val blockTop = block.ymin * size.height
                    val blockSize = Size(
                        width = (block.xmax - block.xmin) * size.width,
                        height = (block.ymax - block.ymin) * size.height,
                    )
                    drawRect(
                        color = if (isActive) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
                        topLeft = Offset(blockLeft, blockTop),
                        size = blockSize,
                    )
                    drawRect(
                        color = borderColor,
                        topLeft = Offset(blockLeft, blockTop),
                        size = blockSize,
                        style = Stroke(width = if (isActive) 2.dp.toPx() else 1.dp.toPx()),
                    )
                }
            }
        }

        if (isLoading || error != null) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(MR.strings.screen_lookup_finding_text))
                    } else {
                        Text(error.orEmpty())
                    }
                }
            }
        }

        if (showTapHint && !isLoading && error == null && blocks.isNotEmpty() && selection == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 84.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                tonalElevation = 2.dp,
            ) {
                Text(
                    text = stringResource(MR.strings.screen_lookup_tap_text),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        ScreenLookupControls(
            onClose = onClose,
            onRecapture = onRecapture,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 12.dp),
        )

        val selected = selection
        if (selected != null) {
            key(selected.lookupString, lookupNonce) {
                OcrLookupPopup(
                    visible = true,
                    lookupString = selected.lookupString,
                    fullText = selected.sentence,
                    charOffset = selected.sentenceOffset,
                    onDismiss = { selection = null },
                    webView = webView,
                    repository = repository,
                    anchorX = selected.anchorX,
                    anchorY = selected.anchorY,
                    anchorWidth = selected.anchorWidth,
                    anchorHeight = selected.anchorHeight,
                    isVertical = selected.block.vertical,
                    activeProfile = activeProfile,
                    type = type,
                    mediaInfo = mediaInfo,
                    screenshot = screenshot,
                    onRequestScreenshot = { screenshot },
                    onRequestSentenceAudio = onRequestSentenceAudio,
                    usePopup = false,
                    titleId = titleId,
                    onTermMatched = { count, off ->
                        matchedCharCount = count
                        matchOffset = off
                    },
                )
            }
        }
    }
}

@Composable
private fun ScreenLookupControls(
    onClose: () -> Unit,
    onRecapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.42f), CircleShape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onRecapture) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(MR.strings.screen_lookup_capture_button),
                tint = Color.White,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(MR.strings.action_close),
                tint = Color.White,
            )
        }
    }
}
