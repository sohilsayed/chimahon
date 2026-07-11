package chimahon.dictionary.ko

import chimahon.LookupResult
import chimahon.TermResult
import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector

object KoreanAnalyzerDeinflector : Deinflector {

    override fun preProcess(text: String): List<String> = KoreanTextProcessors.allVariants(text)

    override fun deinflect(
        text: String,
        languageCode: String,
    ): List<DeinflectionResult> {
        val surface = KoreanTextProcessors.assemble(text)
        val candidates = buildList {
            add(surface)
            addAll(
                KoreanMorphemeChainAnalyzer.analyze(surface, maxResults = MAX_ANALYZER_RESULTS)
                    .flatMap { it.lemmaCandidates },
            )
        }

        return candidates
            .map { KoreanTextProcessors.assemble(it) }
            .distinct()
            .map { DeinflectionResult(it, 0) }
    }

    override fun wrapResults(
        originalQuery: String,
        candidates: List<String>,
        terms: List<TermResult>,
    ): List<LookupResult> {
        return KoreanDeinflector.wrapResults(originalQuery, candidates, terms)
    }

    private const val MAX_ANALYZER_RESULTS = 80
}
