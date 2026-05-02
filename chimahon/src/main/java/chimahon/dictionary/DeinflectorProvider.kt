package chimahon.dictionary

import chimahon.dictionary.arabic.ArabicDeinflector
import chimahon.dictionary.chinese.ChineseDeinflector
import chimahon.dictionary.english.EnglishDeinflector
import chimahon.dictionary.korean.KoreanDeinflector

/**
 * Factory that returns the appropriate [Deinflector] for a given BCP 47 language code.
 * Returns `null` for languages without a custom deinflector (e.g. Japanese uses
 * HoshiDicts's built-in handling).
 */
object DeinflectorProvider {
    fun get(languageCode: String): Deinflector? {
        val base = languageCode.lowercase().substringBefore("-")
        return when (base) {
            "ar" -> ArabicDeinflector
            "ko" -> KoreanDeinflector
            "zh" -> ChineseDeinflector
            "en" -> EnglishDeinflector
            else -> null   // ja, etc. handled natively by HoshiDicts
        }
    }
}
