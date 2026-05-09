package chimahon.dictionary.it

import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector
import chimahon.dictionary.deinflectRecursive
import chimahon.dictionary.Rule

object ItalianDeinflector : Deinflector {

    override fun preProcess(text: String): List<String> = ItalianTextPreprocessors.allVariants(text)

    override fun deinflect(
        text: String,
        languageCode: String,
    ): List<DeinflectionResult> {
        return deinflectRecursive(text, emptyList(), languageCode)
    }
}
