package chimahon.jiten

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class ParseCache(
    private val bookDir: File,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir = File(bookDir, DATA_DIR)

    fun getColors(chapterIndex: Int): List<ColorEntry>? {
        val file = cacheFile(chapterIndex)
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            json.decodeFromString<List<ColorEntry>>(text)
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    fun setColors(chapterIndex: Int, colors: List<ColorEntry>) {
        cacheDir.mkdirs()
        val file = cacheFile(chapterIndex)
        try {
            val text = json.encodeToString(colors)
            file.writeText(text)
        } catch (e: Exception) {
            // Ignore write failures
        }
    }

    fun clear() {
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }

    private fun cacheFile(chapterIndex: Int): File {
        val hash = sha256(bookDir.absolutePath + "_ch$chapterIndex")
        return File(cacheDir, "$hash.json")
    }

    companion object {
        private const val DATA_DIR = "jiten"

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
