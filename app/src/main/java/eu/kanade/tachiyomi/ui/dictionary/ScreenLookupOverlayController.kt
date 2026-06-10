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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import eu.kanade.tachiyomi.ui.reader.viewer.orderedFullText
import eu.kanade.tachiyomi.ui.reader.viewer.toOrderedOffset
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class ScreenLookupOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onDismiss: () -> Unit,
    private val onRecapture: () -> Unit,
) {
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var screenshot: Bitmap? = null

    val isShowing: Boolean
        get() = overlayView != null

    fun show(nextScreenshot: Bitmap) {
        dismiss(recycleScreenshot = true, notify = false)
        screenshot = nextScreenshot

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
private fun ScreenLookupOverlay(
    screenshot: Bitmap,
    onClose: () -> Unit,
    onRecapture: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val dictionaryPreferences = remember { Injekt.get<DictionaryPreferences>() }
    val activeProfile = remember { dictionaryPreferences.profileStore.getActiveProfile() }
    val repository = remember { Injekt.get<DictionaryRepository>() }
    val webView = remember(activeProfile.languageCode) {
        prepareDictionaryWebViewShell(context, languageCode = activeProfile.languageCode)
    }

    var blocks by remember { mutableStateOf<List<OcrTextBlock>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf<ScreenLookupSelection?>(null) }
    var lookupNonce by remember { mutableIntStateOf(0) }

    DisposableEffect(webView) {
        onDispose {
            webView.runCatching {
                stopLoading()
                destroy()
            }
        }
    }

    LaunchedEffect(screenshot, activeProfile.languageCode) {
        isLoading = true
        error = null
        blocks = emptyList()
        val language = OcrLanguage.entries.find {
            it.bcp47.equals(activeProfile.languageCode, ignoreCase = true)
        } ?: OcrLanguage.JAPANESE

        runCatching {
            withContext(Dispatchers.Default) {
                recognizePage(screenshot.toScreenLookupOcrPngBytes(), language)
                    .toScreenLookupBlocks(language.bcp47)
            }
        }.onSuccess {
            blocks = it
            if (it.isEmpty()) {
                error = context.contextStringResource(MR.strings.screen_lookup_no_text)
            }
        }.onFailure {
            error = it.message ?: context.contextStringResource(MR.strings.screen_lookup_capture_failed)
        }
        isLoading = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

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
                            return@detectTapGestures
                        }

                        val tapX = (offset.x / widthPx).coerceIn(0f, 1f)
                        val tapY = (offset.y / heightPx).coerceIn(0f, 1f)
                        val charOffset = tapped.screenLookupCharOffset(tapX, tapY)
                        val text = tapped.fullText
                        if (charOffset !in text.indices || !isLookupStartChar(text[charOffset])) {
                            selection = null
                            return@detectTapGestures
                        }

                        val lookupString = extractOcrLookupString(text, charOffset)
                        if (lookupString.isBlank()) {
                            selection = null
                            return@detectTapGestures
                        }

                        lookupNonce++
                        selection = ScreenLookupSelection(
                            block = tapped,
                            lookupString = lookupString,
                            sentence = tapped.orderedFullText,
                            sentenceOffset = tapped.toOrderedOffset(charOffset),
                            anchorX = tapped.xmin * widthPx,
                            anchorY = tapped.ymin * heightPx,
                            anchorWidth = (tapped.xmax - tapped.xmin) * widthPx,
                            anchorHeight = (tapped.ymax - tapped.ymin) * heightPx,
                        )
                    }
                },
        ) {
            val active = selection?.block
            blocks.forEach { block ->
                val left = block.xmin * size.width
                val top = block.ymin * size.height
                val rectSize = Size(
                    width = (block.xmax - block.xmin) * size.width,
                    height = (block.ymax - block.ymin) * size.height,
                )
                val isActive = block == active
                drawRect(
                    color = if (isActive) Color(0x442196F3) else Color(0x1A000000),
                    topLeft = Offset(left, top),
                    size = rectSize,
                )
                drawRect(
                    color = if (isActive) Color(0xFF64B5F6) else Color(0xB3FFFFFF),
                    topLeft = Offset(left, top),
                    size = rectSize,
                    style = Stroke(width = if (isActive) 3.dp.toPx() else 1.dp.toPx()),
                )
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

        if (!isLoading && error == null && blocks.isNotEmpty() && selection == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
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
                    type = "screen",
                    screenshot = screenshot,
                    onRequestScreenshot = { screenshot },
                    usePopup = false,
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
