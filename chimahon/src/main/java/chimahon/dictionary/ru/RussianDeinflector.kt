package chimahon.dictionary.ru

import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector

object RussianDeinflector : Deinflector {

    override fun preProcess(text: String): List<String> = RussianTextPreprocessors.allVariants(text)

    override fun deinflect(
        text: String,
        languageCode: String,
    ): List<DeinflectionResult> {
        return listOf(DeinflectionResult(text, 0))
    }

    // Russian relies primarily on text preprocessors (yo/e, diacritics)
    // rather than morphological deinflection rules.
    // The preprocessors handle the ё->е conversion which is the main
    // source of lookup variability in Russian.
}
