package chimahon.dictionary.it

import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector

object ItalianDeinflector : Deinflector {

    override fun preProcess(text: String): List<String> = ItalianTextPreprocessors.allVariants(text)

    override fun deinflect(
        text: String,
        languageCode: String,
    ): List<DeinflectionResult> {
        return listOf(DeinflectionResult(text, 0))
    }
}
