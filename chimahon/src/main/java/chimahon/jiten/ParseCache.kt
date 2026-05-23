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
        val file = cacheFile(chapterIndex, "colors")
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
        val file = cacheFile(chapterIndex, "colors")
        try {
            val text = json.encodeToString(colors)
            file.writeText(text)
        } catch (e: Exception) {
            // Ignore write failures
        }
    }

    fun getCards(chapterIndex: Int): Map<Pair<Int, Int>, JitenWordCard>? {
        val file = cacheFile(chapterIndex, "cards")
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            val list = json.decodeFromString<List<CachedCardEntry>>(text)
            list.associate { (it.wordId to it.readingIndex) to it.card }
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    fun setCards(chapterIndex: Int, cards: Map<Pair<Int, Int>, JitenWordCard>) {
        cacheDir.mkdirs()
        val file = cacheFile(chapterIndex, "cards")
        try {
            val list = cards.map { (key, card) ->
                CachedCardEntry(key.first, key.second, card)
            }
            val text = json.encodeToString(list)
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

    private fun cacheFile(chapterIndex: Int, suffix: String): File {
        val hash = sha256(bookDir.absolutePath + "_ch$chapterIndex")
        return File(cacheDir, "${hash}_$suffix.json")
    }

    @kotlinx.serialization.Serializable
    private data class CachedCardEntry(
        val wordId: Int,
        val readingIndex: Int,
        val card: JitenWordCard,
    )

    companion object {
        private const val DATA_DIR = "jiten"

        private fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
