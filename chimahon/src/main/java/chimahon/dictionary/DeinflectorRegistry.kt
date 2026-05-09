package chimahon.dictionary

import chimahon.dictionary.arabic.ArabicDeinflector
import chimahon.dictionary.chinese.ChineseDeinflector
import chimahon.dictionary.english.EnglishDeinflector
import chimahon.dictionary.ko.KoreanDeinflector
import chimahon.dictionary.de.GermanDeinflector
import chimahon.dictionary.fr.FrenchDeinflector
import chimahon.dictionary.ru.RussianDeinflector
import chimahon.dictionary.es.SpanishDeinflector
import chimahon.dictionary.it.ItalianDeinflector

/**
 * Registry to provide the appropriate [Deinflector] for a given language code.
 */
object DeinflectorRegistry {

    private val registry = mapOf(
        "ar" to ArabicDeinflector,
        "ko" to KoreanDeinflector,
        "en" to EnglishDeinflector,
        "zh" to ChineseDeinflector,
        "de" to GermanDeinflector,
        "fr" to FrenchDeinflector,
        "ru" to RussianDeinflector,
        "es" to SpanishDeinflector,
        "it" to ItalianDeinflector
    )

    fun get(languageCode: String): Deinflector? {
        return registry[languageCode]
    }
}
