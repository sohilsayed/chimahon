package com.canopus.chimareader.ui.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SasayakiSheet(
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current
    
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        
        // In a real app we'd copy this safely into BookStorage
        val tempFile = File(context.cacheDir, "sasayaki_imported.m4a")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        viewModel.sasayakiPlayer?.importAudio(tempFile)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Sasayaki",
                style = MaterialTheme.typography.headlineSmall
            )

            if (viewModel.sasayakiPlayer?.hasAudio == true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Audio Synchronization", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { viewModel.sasayakiPlayer?.togglePlayback() }) {
                        val icon = if (viewModel.sasayakiPlayer?.isPlaying == true) {
                            // Using a placeholder icon since pause icon requires extended material icons
                            Icons.Default.PlayArrow 
                        } else {
                            Icons.Default.PlayArrow
                        }
                        Icon(icon, contentDescription = "Toggle Playback")
                    }
                }
                
                Button(
                    onClick = { audioPicker.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Replace Audio File")
                }
            } else {
                Text(
                    "No audio file matched or imported.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Button(
                    onClick = { audioPicker.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import Audio File")
                }
            }
        }
    }
}
