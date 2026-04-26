package com.canopus.chimareader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.canopus.chimareader.data.BookImporter
import com.canopus.chimareader.data.BookMetadata
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.ui.reader.NovelReaderActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    onSyncRequest: (suspend () -> Boolean)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var books by remember { mutableStateOf<List<BookMetadata>>(emptyList()) }
    var isImporting by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<BookMetadata?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    fun loadBooks() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val loaded = BookStorage.loadAllBooks(context)
                books = loaded.sortedByDescending { it.lastAccess }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadBooks()
    }

    val epubPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isImporting = true
            importError = null
            val result = BookImporter.importEpub(context, uri)
            if (result.metadata != null) {
                loadBooks()
            } else {
                importError = result.error
            }
            isImporting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search books...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            )
                        )
                    } else {
                        Text(
                            "Library", 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(onClick = { 
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Cancel Search")
                        }
                    } else if (isImporting || isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                isSyncing = true
                                onSyncRequest?.invoke()
                                loadBooks() // Refresh UI after sync
                                isSyncing = false
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync with Google Drive")
                        }
                        IconButton(onClick = { epubPicker.launch("application/epub+zip") }) {
                            Icon(Icons.Default.Add, contentDescription = "Import EPUB")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        },
        snackbarHost = {
            importError?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { importError = null }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(error)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        // Delete confirmation dialog
        showDeleteDialog?.let { book ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Delete Book") },
                text = { Text("Are you sure you want to delete \"${book.title}\"? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    BookStorage.deleteBook(context, book.id)
                                }
                                loadBooks()
                                showDeleteDialog = null
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (books.isEmpty() && !isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Your library is empty",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to import an EPUB file",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            val filteredBooks = if (searchQuery.isEmpty()) {
                books
            } else {
                books.filter { 
                    it.title?.contains(searchQuery, ignoreCase = true) == true ||
                    it.folder?.contains(searchQuery, ignoreCase = true) == true
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(filteredBooks, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        onClick = {
                            if (book.isGhost) {
                                epubPicker.launch("application/epub+zip")
                            } else {
                                val bookDir = BookStorage.getBookDirectory(context, book.folder ?: book.id)
                                NovelReaderActivity.launch(context, bookDir)
                            }
                        },
                        onDelete = { showDeleteDialog = book }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookCard(
    book: BookMetadata,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (book.cover != null) {
                AsyncImage(
                    model = File(book.cover),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = "No Cover",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (book.isGhost) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .align(Alignment.BottomCenter)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MISSING FILE",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Menu button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = book.title ?: "Unknown Title",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        // Example: Subtitle / Author
        Text(
            text = "EPUB Document",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
