package com.canopus.chimareader.ui.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.canopus.chimareader.data.FontManager
import com.canopus.chimareader.data.Theme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var importedFonts by remember { mutableStateOf(FontManager.getImportedFonts(context)) }
    val allFonts = remember(importedFonts) { FontManager.defaultFonts + importedFonts }

    var showImportDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isImporting = true
            scope.launch {
                val success = FontManager.importFont(context, it)
                if (success) {
                    importedFonts = FontManager.getImportedFonts(context)
                }
                isImporting = false
            }
        }
    }

    val readerSettings = viewModel.getReaderSettings(context)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
            Text(
                "Appearance",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp)
            )

            // Theme (moved to top)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = viewModel.theme == Theme.SYSTEM,
                        onClick = { viewModel.updateTheme(Theme.SYSTEM) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
                    ) {
                        Text("System")
                    }
                    SegmentedButton(
                        selected = viewModel.theme == Theme.LIGHT,
                        onClick = { viewModel.updateTheme(Theme.LIGHT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
                    ) {
                        Text("Light")
                    }
                    SegmentedButton(
                        selected = viewModel.theme == Theme.DARK,
                        onClick = { viewModel.updateTheme(Theme.DARK) },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
                    ) {
                        Text("Dark")
                    }
                    SegmentedButton(
                        selected = viewModel.theme == Theme.SEPIA,
                        onClick = { viewModel.updateTheme(Theme.SEPIA) },
                        shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
                    ) {
                        Text("Sepia")
                    }
                }
            }

            // Layout Mode
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !viewModel.continuousMode,
                        onClick = { viewModel.updateContinuousMode(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Paginated")
                    }
                    SegmentedButton(
                        selected = viewModel.continuousMode,
                        onClick = { viewModel.updateContinuousMode(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Continuous")
                    }
                }
            }

            // Typography
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Typography", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                // Font Family
                var fontExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = fontExpanded,
                    onExpandedChange = { fontExpanded = it }
                ) {
                    OutlinedTextField(
                        value = viewModel.selectedFont,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Font Family") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = fontExpanded,
                        onDismissRequest = { fontExpanded = false }
                    ) {
                        allFonts.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font) },
                                onClick = {
                                    viewModel.updateSelectedFont(font)
                                    fontExpanded = false
                                }
                            )
                        }
                    }
                }

                // Import Font Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { fontPickerLauncher.launch(arrayOf("application/font-ttf", "application/x-font-ttf", "font/ttf", "application/octet-stream")) },
                        modifier = Modifier.weight(1f),
                        enabled = !isImporting
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Import Font")
                        }
                    }

                    // Delete imported font button
                    if (importedFonts.contains(viewModel.selectedFont)) {
                        OutlinedButton(
                            onClick = {
                                FontManager.deleteFont(context, viewModel.selectedFont)
                                importedFonts = FontManager.getImportedFonts(context)
                                viewModel.updateSelectedFont(FontManager.defaultFonts.first())
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Delete Font")
                        }
                    }
                }

                // Font Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.bodyMedium)
                        Text("${viewModel.fontSize}px", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = viewModel.fontSize.toFloat(),
                        onValueChange = { viewModel.updateFontSize(it.roundToInt()) },
                        valueRange = 12f..48f,
                        steps = 36
                    )
                }

                // Line Height
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Line Height", style = MaterialTheme.typography.bodyMedium)
                        Text("%.1f".format(viewModel.lineHeight), style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = viewModel.lineHeight.toFloat(),
                        onValueChange = { viewModel.updateLineHeight(it.toDouble()) },
                        valueRange = 1.0f..2.5f,
                        steps = 15
                    )
                }

                // Hide Furigana
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Hide Furigana", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = viewModel.hideFurigana,
                        onCheckedChange = { viewModel.updateHideFurigana(it) }
                    )
                }

                // Keep screen on
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Keep screen on", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = viewModel.keepScreenOn,
                        onCheckedChange = { viewModel.updateKeepScreenOn(it) }
                    )
                }
            }

            // Margins
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Margins", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Horizontal Padding", style = MaterialTheme.typography.bodyMedium)
                        Text("${viewModel.horizontalPadding}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = viewModel.horizontalPadding.toFloat(),
                        onValueChange = { viewModel.updateHorizontalPadding(it.roundToInt()) },
                        valueRange = 0f..50f,
                        steps = 49
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vertical Padding", style = MaterialTheme.typography.bodyMedium)
                        Text("${viewModel.verticalPadding}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = viewModel.verticalPadding.toFloat(),
                        onValueChange = { viewModel.updateVerticalPadding(it.roundToInt()) },
                        valueRange = 0f..50f,
                        steps = 49
                    )
                }
            }

            // Layout Settings
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Layout", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

                // Writing Mode
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Writing Mode", style = MaterialTheme.typography.labelMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = viewModel.verticalWriting,
                            onClick = { viewModel.updateVerticalWriting(true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Vertical")
                        }
                        SegmentedButton(
                            selected = !viewModel.verticalWriting,
                            onClick = { viewModel.updateVerticalWriting(false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Horizontal")
                        }
                    }
                }

                // Tap Zone Size
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tap Zone Size (Navigation)", style = MaterialTheme.typography.bodyMedium)
                        Text("${viewModel.tapZonePercent}%", style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = viewModel.tapZonePercent.toFloat(),
                        onValueChange = { viewModel.updateTapZonePercent(it.roundToInt()) },
                        valueRange = 0f..40f,
                        steps = 39
                    )
                }

                // Advanced Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateLayoutAdvanced(!viewModel.layoutAdvanced) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text("Advanced", style = MaterialTheme.typography.bodyMedium)
                    Icon(
                        imageVector = if (viewModel.layoutAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (viewModel.layoutAdvanced) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Advanced settings - only visible when enabled
                if (viewModel.layoutAdvanced) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avoid Page Break
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Avoid Page Break", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = viewModel.avoidPageBreak,
                                onCheckedChange = { viewModel.updateAvoidPageBreak(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        // Justify Text
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Justify Text", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = viewModel.justifyText,
                                onCheckedChange = { viewModel.updateJustifyText(it) },
                                modifier = Modifier.scale(0.85f)
                            )
                        }

                        // Character Spacing
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Character Spacing", style = MaterialTheme.typography.bodySmall)
                                Text("%.2f".format(viewModel.characterSpacing), style = MaterialTheme.typography.bodySmall)
                            }
                            Slider(
                                value = viewModel.characterSpacing.toFloat(),
                                onValueChange = { viewModel.updateCharacterSpacing(it.toDouble()) },
                                valueRange = 0f..0.5f,
                                steps = 10
                            )
                        }
                    }
                }
            }
        }
    }
}
