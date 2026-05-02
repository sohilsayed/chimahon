package eu.kanade.tachiyomi.ui.browse.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.canopus.chimareader.data.BookImporter
import com.canopus.chimareader.data.NovelCategoryStorage
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.local.LocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object ImportHandler {

    suspend fun importManga(context: Context, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val mangaRepository: MangaRepository = Injekt.get()
        val storageManager: StorageManager = Injekt.get()
        val libraryPreferences: LibraryPreferences = Injekt.get()
        
        val localSourceDir = storageManager.getLocalSourceDirectory() ?: return@withContext
        
        // Group by base title for smart volume detection
        val grouped = uris.groupBy { uri ->
            val fileName = getFileName(context, uri) ?: "Unknown"
            MangaImportUtil.getBaseTitle(fileName)
        }
        
        grouped.forEach { (baseTitle, files) ->
            val safeFolderName = MangaImportUtil.getSafeFolderName(baseTitle)
            val mangaDir = localSourceDir.createDirectory(safeFolderName) ?: return@forEach
            
            files.forEach { uri ->
                val fileName = getFileName(context, uri) ?: return@forEach
                val file = mangaDir.createFile(fileName) ?: return@forEach
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.openOutputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            // Add to library
            val existingManga = mangaRepository.getMangaByUrlAndSourceId(safeFolderName, LocalSource.ID)
            val manga = if (existingManga == null) {
                val newManga = Manga.create().copy(
                    url = safeFolderName,
                    title = baseTitle,
                    source = LocalSource.ID,
                    favorite = true,
                    dateAdded = System.currentTimeMillis(),
                )
                mangaRepository.insertNetworkManga(listOf(newManga)).first()
            } else {
                if (!existingManga.favorite) {
                    mangaRepository.update(MangaUpdate(id = existingManga.id, favorite = true))
                }
                existingManga
            }
            
            // Add to default category if set
            val defaultCategoryId = libraryPreferences.defaultCategory().get()
            if (defaultCategoryId != -1) {
                mangaRepository.setMangaCategories(manga.id, listOf(defaultCategoryId.toLong()))
            }
        }
    }

    suspend fun importNovels(context: Context, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val novelCategoryStorage: NovelCategoryStorage = Injekt.get()
        
        uris.forEach { uri ->
            val result = BookImporter.importEpub(context, uri)
            val bookMetadata = result.metadata ?: return@forEach
            
            // Add to default novel category
            val categories = novelCategoryStorage.loadAllCategories()
            val defaultCategory = categories.find { it.name == "Default" || it.id == "default" } 
                ?: novelCategoryStorage.createCategory("Default")
            
            val updatedMetadata = bookMetadata.copy(
                categoryIds = (bookMetadata.categoryIds + defaultCategory.id).distinct()
            )
            val bookDir = java.io.File(com.canopus.chimareader.data.BookStorage.getBooksDirectory(context), updatedMetadata.id)
            com.canopus.chimareader.data.BookStorage.saveMetadata(updatedMetadata, bookDir)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    }
}
