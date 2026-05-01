package chimahon.audio

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

class WordAudioDatabase(private val context: Context) {
    private var db: SQLiteDatabase? = null
    private var dbPath: String? = null

    private val defaultSources = listOf(
        "nhk16", "daijisen", "shinmeikai8", "jpod", "jpod_alternate",
        "taas", "ozk5", "forvo", "forvo_ext", "forvo_ext2",
    )

    private fun katakanaToHiragana(text: String): String {
        return text.map { char ->
            val code = char.code
            if (code in 0x30A1..0x30F6) {
                (code - 0x60).toChar()
            } else {
                char
            }
        }.joinToString("")
    }

    companion object {
        private const val TAG = "WordAudioDatabase"
    }

    /**
     * Updates the database path and opens the connection if necessary.
     */
    fun updatePath(path: String?): Boolean {
        if (path == dbPath && db != null) return true

        close()

        if (path == null) return false

        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            Log.w(TAG, "Database file not found or not readable: $path")
            return false
        }

        return try {
            db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
            dbPath = path
            Log.i(TAG, "Opened local audio database at $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open local audio database", e)
            false
        }
    }

    /**
     * Tests if the database is queryable and not malformed.
     */
    fun testConnection(): Boolean {
        val database = db ?: return false
        return try {
            database.rawQuery("SELECT count(*) FROM entries LIMIT 1", null).use { cursor ->
                cursor.moveToFirst()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Database connection test failed, likely corrupted or malformed", e)
            false
        }
    }

    /**
     * Finds entries for a given term and reading, replicated from Hoshi Reader's logic.
     */
    fun findEntries(term: String, reading: String): List<LocalEntry> {
        val database = db ?: return emptyList()
        val results = mutableListOf<LocalEntry>()

        val normalizedReading = katakanaToHiragana(reading)

        // Sort order based on source priority
        val sourceOrder = StringBuilder("CASE source ")
        defaultSources.forEachIndexed { index, source ->
            sourceOrder.append("WHEN '$source' THEN $index ")
        }
        sourceOrder.append("ELSE 999 END")

        val query = if (normalizedReading.isEmpty()) {
            """
            SELECT file, source, speaker, display, reading, expression FROM entries
            WHERE expression = ? AND (file LIKE '%.mp3' OR file LIKE '%.ogg' OR file LIKE '%.opus' OR file LIKE '%.wav' OR file LIKE '%.m4a' OR file LIKE '%.aac' OR file LIKE '%.flac')
            ORDER BY $sourceOrder
            LIMIT 1
            """.trimIndent()
        } else {
            """
            SELECT file, source, speaker, display, reading, expression FROM entries
            WHERE (expression = ? OR reading = ?) AND (file LIKE '%.mp3' OR file LIKE '%.ogg' OR file LIKE '%.opus' OR file LIKE '%.wav' OR file LIKE '%.m4a' OR file LIKE '%.aac' OR file LIKE '%.flac')
            ORDER BY CASE WHEN reading = ? THEN 0 ELSE 1 END, $sourceOrder
            LIMIT 1
            """.trimIndent()
        }

        val args = if (normalizedReading.isEmpty()) {
            arrayOf(term)
        } else {
            arrayOf(term, normalizedReading, normalizedReading)
        }

        try {
            database.rawQuery(query, args).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(
                        LocalEntry(
                            file = cursor.getString(0) ?: "",
                            sourceId = cursor.getString(1) ?: "",
                            speaker = cursor.getString(2),
                            display = cursor.getString(3),
                            reading = cursor.getString(4) ?: "",
                            expression = cursor.getString(5) ?: "",
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying entries", e)
        }

        return results
    }

    /**
     * Retrieves the raw audio bytes for a specific file path and source.
     */
    fun getAudioData(filePath: String, sourceId: String): ByteArray? {
        val database = db ?: return null

        // AnkiconnectAndroid schema uses 'android' table with 'file' and 'source' columns
        val query = "SELECT data FROM android WHERE file = ? AND source = ?"

        return try {
            database.rawQuery(query, arrayOf(filePath, sourceId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getBlob(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving audio data", e)
            null
        }
    }

    fun close() {
        db?.close()
        db = null
        dbPath = null
    }

    data class LocalEntry(
        val file: String,
        val sourceId: String,
        val speaker: String?,
        val display: String?,
        val reading: String,
        val expression: String,
    )
}
