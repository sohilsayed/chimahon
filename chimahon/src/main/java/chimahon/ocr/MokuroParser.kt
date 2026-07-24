package chimahon.ocr

import kotlinx.serialization.json.Json

private val mokuroJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun parseMokuro(content: String): MokuroVolume? {
    return runCatching {
        mokuroJson.decodeFromString<MokuroVolume>(content)
    }.getOrNull()
}
