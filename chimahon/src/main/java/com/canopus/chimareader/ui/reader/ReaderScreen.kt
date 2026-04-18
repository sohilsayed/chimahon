package com.canopus.chimareader.ui.reader

import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.canopus.chimareader.data.BookMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ReaderLoadState {
    data object Loading : ReaderLoadState
    data class Error(val message: String) : ReaderLoadState
    data class Ready(val viewModel: ReaderViewModel) : ReaderLoadState
}

enum class ActiveSheet {
    Appearance, Chapters, Statistics, Sasayaki
}

@Composable
fun ReaderScreen(
    book: BookMetadata,
    onBack: () -> Unit,
    onLookupRequested: ((word: String, sentence: String, x: Float, y: Float) -> Unit)? = null,
    isPopupActive: Boolean = false,
) {
    val context = LocalContext.current

    var focusMode by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    val scope = rememberCoroutineScope()
    val settings = remember { com.canopus.chimareader.data.NovelReaderSettings(context) }

    // Collect swipe and tap settings
    val chapterSwipeDistance by settings.chapterSwipeDistance.collectAsState(initial = 96)

    val loadState by produceState<ReaderLoadState>(initialValue = ReaderLoadState.Loading, key1 = book.id) {
        value = try {
            withContext(Dispatchers.IO) {
                val loader = ReaderLoaderViewModel(context, book)
                val document = loader.document ?: error("Could not open book")
                val rootUrl = loader.rootUrl ?: error("Missing root URL")
                ReaderLoadState.Ready(
                    ReaderViewModel(
                        document = document,
                        rootUrl = rootUrl,
                        settings = settings,
                        scope = scope
                    )
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ReaderLoadState.Error(error.message ?: "Could not open book")
        }
    }

    DisposableEffect(loadState) {
        onDispose {
            val readyState = loadState as? ReaderLoadState.Ready ?: return@onDispose
            readyState.viewModel.saveBookmark(readyState.viewModel.currentProgress)
        }
    }

    val bgColor = if (loadState is ReaderLoadState.Ready) {
        Color((loadState as ReaderLoadState.Ready).viewModel.getReaderSettings(context).backgroundColor)
    } else {
        MaterialTheme.colorScheme.background
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .systemBarsPadding()
    ) {
        Log.d("ReaderScreen", "BoxWithConstraints: maxHeight=$maxHeight, maxWidth=$maxWidth")
        
        // Capture initial height once, then use requiredHeight to override parent constraints
        var webViewHeightDp by remember { mutableStateOf<Dp?>(null) }
        val density = LocalDensity.current
        
        val heightModifier = if (webViewHeightDp != null) {
            Modifier.requiredHeight(webViewHeightDp!!)
        } else {
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    if (webViewHeightDp == null && size.height > 0) {
                        webViewHeightDp = with(density) { size.height.toDp() }
                        Log.d("ReaderScreen", "Captured fixed height: $webViewHeightDp")
                    }
                }
        }
        when (val state = loadState) {
            ReaderLoadState.Loading -> ReaderMessage("Opening...", loading = true)
            is ReaderLoadState.Error -> ReaderMessage(state.message)
            is ReaderLoadState.Ready -> {
                val viewModel = state.viewModel
                
                // Initialize SasayakiPlayer if not already
                if (viewModel.sasayakiPlayer == null) {
                    val rootDir = viewModel.rootUrl
                    viewModel.sasayakiPlayer = SasayakiPlayer(
                        context = context,
                        rootDir = rootDir,
                        bridge = viewModel.bridge,
                        loadChapter = { chapterIndex -> 
                            viewModel.jumpToChapter(chapterIndex)
                        },
                        getCurrentIndex = { viewModel.index }
                    )
                }
                
                // HUD visibility state - toggled by edge taps
                var showHud by remember { mutableStateOf(true) }

                val density = LocalDensity.current
                val tapZonePx = with(density) { 64.dp.toPx() }.toInt()

                // Single WebView handles all chapters
                ReaderWebView(
                    modifier = Modifier.fillMaxWidth().then(heightModifier),
                    bridge = viewModel.bridge,
                    continuousMode = viewModel.continuousMode,
                    isImageOnly = viewModel.isCurrentChapterImageOnly,
                    readerSettings = viewModel.getReaderSettings(context),
                    focusMode = focusMode,
                    onNextChapter = {
                        viewModel.sasayakiPlayer?.prepareTransition()
                        viewModel.nextChapter()
                    },
                    onPreviousChapter = {
                        viewModel.sasayakiPlayer?.prepareTransition()
                        viewModel.previousChapter()
                    },
                    onProgressChanged = { viewModel.saveBookmark(it) },
                    onLoadFailed = { },
                    onTap = { if (focusMode) focusMode = false },
                    onTapTop = { showHud = !showHud },
                    onTapBottom = { showHud = !showHud },
                    swipeThreshold = chapterSwipeDistance,
                    tapZonePx = tapZonePx,
                    isPopupActive = isPopupActive,
                    onTextSelected = { word, sentence, x, y -> onLookupRequested?.invoke(word, sentence, x, y) },
                )

                // Top HUD - always visible when showHud is true
                if (showHud) {
                    ReaderTopBar(
                        title = viewModel.document.title().orEmpty(),
                        onBack = onBack,
                        onToggleHud = { showHud = false },
                        backgroundColor = viewModel.getReaderSettings(context).backgroundColor,
                        contentColor = viewModel.getReaderSettings(context).textColor,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                // Bottom HUD - always visible when showHud is true
                if (showHud) {
                    val readerSettings = viewModel.getReaderSettings(context)
                    ReaderBottomBar(
                        focusMode = focusMode,
                        progressText = "${(viewModel.currentProgress * 100).toInt()}%",
                        backgroundColor = readerSettings.backgroundColor,
                        contentColor = readerSettings.textColor,
                        onToggleHud = { showHud = false },
                        onToggleFocusMode = { focusMode = true },
                        onOpenChapters = { activeSheet = ActiveSheet.Chapters },
                        onOpenAppearance = { activeSheet = ActiveSheet.Appearance },
                        onOpenStatistics = { activeSheet = ActiveSheet.Statistics },
                        onOpenSasayaki = { activeSheet = ActiveSheet.Sasayaki },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
    }
    
    // Sheets outside the Box - true overlay that doesn't affect WebView size
    when (val state = loadState) {
        is ReaderLoadState.Ready -> {
            val viewModel = state.viewModel
            activeSheet?.let { sheet ->
                when (sheet) {
                    ActiveSheet.Appearance -> AppearanceSheet(viewModel) { activeSheet = null }
                    ActiveSheet.Chapters -> ChapterListSheet(viewModel) { activeSheet = null }
                    ActiveSheet.Statistics -> StatisticsSheet(viewModel) { activeSheet = null }
                    ActiveSheet.Sasayaki -> SasayakiSheet(viewModel) { activeSheet = null }
                }
            }
        }
        else -> {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
    onToggleHud: () -> Unit,
    backgroundColor: Int,
    contentColor: Int,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onToggleHud() },
        title = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = Color(contentColor)
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, 
                    contentDescription = "Back",
                    tint = Color(contentColor)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(backgroundColor).copy(alpha = 0.9f)
        )
    )
}

@Composable
private fun ReaderBottomBar(
    focusMode: Boolean,
    progressText: String,
    backgroundColor: Int,
    contentColor: Int,
    onToggleHud: () -> Unit,
    onToggleFocusMode: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenSasayaki: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(backgroundColor).copy(alpha = 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggleHud() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = progressText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(contentColor).copy(alpha = 0.7f)
        )
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onOpenChapters) {
                Icon(
                    Icons.Default.List, 
                    contentDescription = "Chapters",
                    tint = Color(contentColor)
                )
            }
            IconButton(onClick = onOpenAppearance) {
                Icon(
                    Icons.Default.Settings, 
                    contentDescription = "Appearance",
                    tint = Color(contentColor)
                )
            }
        }
    }
}

@Composable
private fun ReaderMessage(
    text: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) CircularProgressIndicator()
        Spacer(modifier = Modifier.padding(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
