package eu.kanade.tachiyomi.ui.browse.source

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

sealed class PendingImportData {
    data class Files(val uris: List<Uri>) : PendingImportData()
    data class Folder(val uri: Uri) : PendingImportData()
}

@Composable
fun DestinationFolderDialog(
    pendingImportData: PendingImportData,
    onDismissRequest: () -> Unit,
    onImport: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    var existingFolders by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val storageManager: StorageManager = Injekt.get()
            val localSourceDir = storageManager.getLocalSourceDirectory()
            val folders = localSourceDir?.listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { it.name }
                ?: emptyList()
            existingFolders = folders
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Destination Folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                if (existingFolders.isNotEmpty()) {
                    Text("Or select existing:", modifier = Modifier.padding(bottom = 4.dp))
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(existingFolders) { folder ->
                            Text(
                                text = folder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { folderName = folder }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
