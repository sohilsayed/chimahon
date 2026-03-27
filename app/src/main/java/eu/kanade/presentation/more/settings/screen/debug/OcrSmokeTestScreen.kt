package eu.kanade.presentation.more.settings.screen.debug

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import chimahon.ocr.LensClient
import chimahon.ocr.OcrDebugResult
import chimahon.ocr.OcrLanguage
import chimahon.ocr.OcrResult
import chimahon.ocr.RawChunk
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.coroutines.launch
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections.emptyList

class OcrSmokeTestScreen : Screen() {

    companion object {
        const val TITLE = "OCR smoke test"
        private const val ASSET_DIR = "ocr-test"
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val lensClient = remember { Injekt.get<LensClient>() }

        val assetNames by produceState(initialValue = emptyList<String>(), context) {
            value = context.assets.list(ASSET_DIR)
                ?.filter { it.substringAfterLast('.', "").lowercase() in supportedExtensions }
                ?.sorted()
                .orEmpty()
        }

        var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(assetNames) {
            if (selectedIndex > assetNames.lastIndex) {
                selectedIndex = assetNames.lastIndex.coerceAtLeast(0)
            }
        }

        val selectedAsset = assetNames.getOrNull(selectedIndex)
        val selectedBytes by produceState<ByteArray?>(initialValue = null, selectedAsset) {
            value = selectedAsset?.let { context.assets.open("$ASSET_DIR/$it").use { stream -> stream.readBytes() } }
        }
        val previewBitmap = remember(selectedBytes) {
            selectedBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }

        var debugResult by remember { mutableStateOf<OcrDebugResult?>(null) }
        var displayRaw by rememberSaveable { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        val overlayBoxes = remember(debugResult, displayRaw) {
            val result = debugResult ?: return@remember emptyList()
            if (displayRaw) {
                result.rawChunks.toNormalizedRawLines()
            } else {
                result.mergedResults
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = TITLE,
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Drop test images into app/src/main/assets/$ASSET_DIR/ and open this screen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (assetNames.isEmpty()) {
                    item {
                        Text(
                            text = "No assets found.",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Asset ${selectedIndex + 1}/${assetNames.size}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = selectedAsset.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { selectedIndex = (selectedIndex - 1).coerceAtLeast(0) },
                                    enabled = selectedIndex > 0,
                                ) {
                                    Text("Previous")
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedIndex = (selectedIndex + 1).coerceAtMost(assetNames.lastIndex)
                                    },
                                    enabled = selectedIndex < assetNames.lastIndex,
                                ) {
                                    Text("Next")
                                }
                                Button(
                                    onClick = {
                                        val bytes = selectedBytes ?: return@Button
                                        scope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            debugResult = null
                                            try {
                                                debugResult = lensClient.getDebugOcrData(
                                                    bytes = bytes,
                                                    language = OcrLanguage.JAPANESE,
                                                )
                                            } catch (error: Throwable) {
                                                errorMessage = error.message ?: error::class.java.simpleName
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
                                    enabled = !isLoading && selectedBytes != null,
                                ) {
                                    Text(if (isLoading) "Running..." else "Run OCR")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !displayRaw,
                                    onClick = { displayRaw = false },
                                    label = { Text("Merged") },
                                    enabled = debugResult != null,
                                )
                                FilterChip(
                                    selected = displayRaw,
                                    onClick = { displayRaw = true },
                                    label = { Text("Raw") },
                                    enabled = debugResult != null,
                                )
                            }
                        }
                    }

                    item {
                        when {
                            isLoading -> {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeCap = StrokeCap.Round,
                                    )
                                    Text("Running OCR and merge pipeline...")
                                }
                            }
                            errorMessage != null -> {
                                Text(
                                    text = "Error: $errorMessage",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            else -> Unit
                        }
                    }

                    if (previewBitmap != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val mergedCount = debugResult?.mergedResults?.size ?: 0
                                val rawCount = debugResult?.rawChunks?.sumOf { it.lines.size } ?: 0
                                Text(
                                    text = "Boxes: ${if (displayRaw) rawCount else mergedCount}",
                                    style = MaterialTheme.typography.labelLarge,
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline)
                                        .background(Color.Black),
                                ) {
                                    Image(
                                        bitmap = previewBitmap,
                                        contentDescription = selectedAsset,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val strokeWidth = 2.dp.toPx()
                                        overlayBoxes.forEach { box ->
                                            drawRect(
                                                color = if (displayRaw) Color(0xFF4CAF50) else Color(0xFFFF5252),
                                                topLeft = Offset(
                                                    x = (box.tightBoundingBox.x.toFloat() * size.width),
                                                    y = (box.tightBoundingBox.y.toFloat() * size.height),
                                                ),
                                                size = Size(
                                                    width = (box.tightBoundingBox.width.toFloat() * size.width),
                                                    height = (box.tightBoundingBox.height.toFloat() * size.height),
                                                ),
                                                style = Stroke(width = strokeWidth),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (debugResult != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Merged OCR text",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                SelectionContainer {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .widthIn(max = 900.dp)
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        debugResult?.mergedResults?.forEachIndexed { index, result ->
                                            Text(
                                                text = "${index + 1}. ${result.text}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun List<RawChunk>.toNormalizedRawLines(): List<OcrResult> {
        return flatMap { chunk ->
            chunk.lines.map { result ->
                val box = result.tightBoundingBox
                result.copy(
                    tightBoundingBox = box.copy(
                        x = box.x / chunk.fullWidth.toDouble(),
                        y = (box.y + chunk.globalY.toDouble()) / chunk.fullHeight.toDouble(),
                        width = box.width / chunk.fullWidth.toDouble(),
                        height = box.height / chunk.fullHeight.toDouble(),
                    ),
                )
            }
        }
    }

    private val supportedExtensions = setOf("png", "jpg", "jpeg", "webp", "avif")
}
