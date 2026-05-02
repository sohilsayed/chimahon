package chimahon.dictionary.chinese

import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector

/**
 * Chinese deinflector — identity pass-through.
 *
 * Chinese is an isolating language with no standard inflectional morphology,
 * so no suffix stripping is needed. The lookup term is passed through as-is.
 *
 * Future extensions could handle Simplified ↔ Traditional conversion or
 * particle removal (了, 吗, 呢, etc.) if needed.
 */
object ChineseDeinflector : Deinflector {
    override val languageCode: String = "zh"

    override fun deinflect(text: String, conditions: Set<String>): List<DeinflectionResult> {
        return listOf(DeinflectionResult(text, conditions))
    }
}
