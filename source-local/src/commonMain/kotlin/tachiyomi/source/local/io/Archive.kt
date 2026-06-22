package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension

object Archive {

    private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "7z", "cb7", "tar", "cbt")

    fun isSupported(file: UniFile): Boolean {
        return file.extension?.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }
}

object ArchiveAnime {

    private val SUPPORTED_VIDEO_TYPES = setOf("avi", "flv", "mkv", "mov", "mp4", "webm", "wmv")

    fun isSupported(file: UniFile): Boolean {
        return file.extension?.lowercase() in SUPPORTED_VIDEO_TYPES
    }
}
