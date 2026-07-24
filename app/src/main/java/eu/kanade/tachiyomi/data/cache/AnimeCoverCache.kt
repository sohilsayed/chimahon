package eu.kanade.tachiyomi.data.cache

import android.content.Context
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.domain.entries.anime.model.Anime
import java.io.File
import java.io.IOException
import java.io.InputStream

class AnimeCoverCache(private val context: Context) {

    companion object {
        private const val COVERS_DIR = "animecovers"
        private const val CUSTOM_COVERS_DIR = "animecovers/custom"
    }

    private val cacheDir = getCacheDir(COVERS_DIR)

    private val customCoverCacheDir = getCacheDir(CUSTOM_COVERS_DIR)

    fun getCoverFile(animeThumbnailUrl: String?): File? {
        return animeThumbnailUrl?.let {
            File(cacheDir, DiskUtil.hashKeyForDisk(it))
        }
    }

    fun getCustomCoverFile(animeId: Long?): File {
        return File(customCoverCacheDir, DiskUtil.hashKeyForDisk(animeId.toString()))
    }

    @Throws(IOException::class)
    fun setCustomCoverToCache(anime: Anime, inputStream: InputStream) {
        getCustomCoverFile(anime.id).outputStream().use {
            inputStream.copyTo(it)
        }
    }

    fun deleteFromCache(anime: Anime, deleteCustomCover: Boolean = false): Int {
        var deleted = 0

        getCoverFile(anime.thumbnailUrl)?.let {
            if (it.exists() && it.delete()) ++deleted
        }

        if (deleteCustomCover) {
            if (deleteCustomCover(anime.id)) ++deleted
        }

        return deleted
    }

    fun deleteCustomCover(animeId: Long?): Boolean {
        return getCustomCoverFile(animeId).let {
            it.exists() && it.delete()
        }
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
