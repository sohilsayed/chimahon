package chimahon.dictionary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DictionaryIndex(
    val title: String = "",
    val revision: String? = null,
    val format: Int = 3,
    val isUpdatable: Boolean = false,
    val indexUrl: String? = null,
    val downloadUrl: String? = null,
)

data class DictionaryUpdateInfo(
    val dictName: String,
    val currentRevision: String?,
    val isUpdatable: Boolean,
    val indexUrl: String?,
    val downloadUrl: String?,
    val latestRevision: String? = null,
    val latestDownloadUrl: String? = null,
    val hasUpdate: Boolean = false,
    val error: String? = null,
)

private val jsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun readDictionaryIndex(dictDir: File): DictionaryIndex? {
    val indexFile = File(dictDir, "index.json")
    if (!indexFile.exists()) return null
    return try {
        jsonParser.decodeFromString<DictionaryIndex>(indexFile.readText())
    } catch (_: Exception) {
        null
    }
}
