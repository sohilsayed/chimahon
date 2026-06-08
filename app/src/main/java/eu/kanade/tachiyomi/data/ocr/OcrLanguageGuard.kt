package eu.kanade.tachiyomi.data.ocr

fun isOcrAllowedForLanguage(sourceLanguage: String, profileLanguage: String): Boolean {
    val sourceLang = sourceLanguage.normalizedOcrLanguageCode()
    if (sourceLang.isOcrMixedOrUnknownLanguage()) return true

    val profileLang = profileLanguage.normalizedOcrLanguageCode()
    if (profileLang.isOcrMixedOrUnknownLanguage()) return true

    return sourceLang == profileLang
}

private fun String.normalizedOcrLanguageCode(): String {
    return trim()
        .substringBefore('-')
        .substringBefore('_')
        .lowercase()
}

private fun String.isOcrMixedOrUnknownLanguage(): Boolean {
    return isBlank() || this in mixedOrUnknownOcrLanguages
}

private val mixedOrUnknownOcrLanguages = setOf("all", "other", "multi", "unknown")
