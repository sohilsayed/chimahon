package chimahon.dictionary.ru

import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector
import chimahon.dictionary.deinflectRecursive
import chimahon.dictionary.Rule
import chimahon.dictionary.prefixInflection
import chimahon.dictionary.suffixInflection

object RussianDeinflector : Deinflector {

    override fun preProcess(text: String): List<String> = RussianTextPreprocessors.allVariants(text)

    override fun deinflect(
        text: String,
        languageCode: String,
    ): List<DeinflectionResult> {
        return deinflectRecursive(text, allRules, languageCode)
    }

    // Russian relies primarily on text preprocessors (yo/e, diacritics)
    // rather than morphological deinflection rules.
    // The preprocessors handle the ё->е conversion which is the main
    // source of lookup variability in Russian.
    private val allRules: List<Rule> = emptyList()
}
