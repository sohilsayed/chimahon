package chimahon.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Read-only access to word_audio.db backed by a SAF Uri.
 * SAF fd → mmap → sqlite3_deserialize(READONLY).
 * Zero copy, zero path-based opening, works on FUSE devices.
 */
class WordAudioDatabase(private val context: Context) {

    private var handle: Long = 0L
    private var currentUri: String? = null

    private val defaultSources = listOf(
        "nhk16", "daijisen", "shinmeikai8", "jpod", "jpod_alternate",
        "taas", "ozk5", "forvo", "forvo_ext", "forvo_ext2",
    )

    private fun katakanaToHiragana(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            sb.append(if (code in 0x30A1..0x30F6) (code - 0x60).toChar() else c)
        }
        return sb.toString()
    }

    /** Legacy: opens a database by file path. */
    fun updatePath(path: String?): Boolean {
        if (path.isNullOrBlank()) { close(); return false }
        return updateUri(Uri.fromFile(File(path)))
    }

    /** Opens any SAF Uri (or file:// Uri). */
    fun updateUri(uri: Uri): Boolean {
        val key = uri.toString()
        if (key == currentUri && handle != 0L) return true
        close()

        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            Log.e(TAG, "SAF open failed for $uri", e)
            return false
        } ?: return false

        // Close the legacy path pfd before detaching
        val size = pfd.statSize
        if (size <= 0) {
            Log.e(TAG, "empty or invalid file: $uri ($size)")
            pfd.close()
            return false
        }

        val fd = pfd.detachFd()
        val h = nativeOpen(fd, size)
        if (h == 0L) {
            Log.e(TAG, "nativeOpen failed (see logcat: word_audio)")
            return false
        }
        handle = h
        currentUri = key
        Log.i(TAG, "Opened audio database: $uri")
        return true
    }

    fun isOpenFor(uriStr: String): Boolean = currentUri == uriStr && handle != 0L

    fun testConnection(): Boolean {
        if (handle == 0L) return false
        return nativeTestConnection(handle)
    }

    fun findEntries(term: String, reading: String): List<LocalEntry> {
        if (handle == 0L) return emptyList()
        val normalizedReading = katakanaToHiragana(reading)
        val rows = nativeFindEntries(
            handle, term, normalizedReading,
            defaultSources.joinToString(","),
        )
        return rows?.toList() ?: emptyList()
    }

    fun getAudioData(filePath: String, sourceId: String): ByteArray? {
        if (handle == 0L) return null
        return nativeGetAudioData(handle, filePath, sourceId)
    }

    fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
        currentUri = null
    }

    data class LocalEntry(
        val file: String,
        val sourceId: String,
        val speaker: String?,
        val display: String?,
        val reading: String,
        val expression: String,
    )

    companion object {
        private const val TAG = "WordAudioDatabase"
        init { System.loadLibrary("word_audio_jni") }
    }

    private external fun nativeOpen(fd: Int, size: Long): Long
    private external fun nativeClose(handle: Long)
    private external fun nativeTestConnection(handle: Long): Boolean
    private external fun nativeFindEntries(
        handle: Long, term: String, reading: String, sourceOrder: String,
    ): Array<LocalEntry>?
    private external fun nativeGetAudioData(
        handle: Long, filePath: String, sourceId: String,
    ): ByteArray?
}
