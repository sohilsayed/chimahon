package chimahon.dictionary.arabic

import chimahon.LookupResult
import chimahon.TermResult

object ArabicLookupMapper {

    fun wrap(
        originalQuery: String,
        deinflectedText: String,
        term: TermResult,
        preprocessorSteps: Int = 0,
    ): LookupResult {
        return LookupResult(
            matched = originalQuery,
            deinflected = deinflectedText,
            process = emptyArray(),
            term = term,
            preprocessorSteps = preprocessorSteps,
        )
    }

    fun wrapAll(
        originalQuery: String,
        candidates: List<String>,
        terms: List<TermResult>,
        preprocessorSteps: Int = 0,
    ): List<LookupResult> {
        val termsByText = terms.groupBy { it.expression }
        return candidates.flatMap { candidate ->
            val termList = termsByText[candidate]
            if (termList != null && termList.isNotEmpty()) {
                termList.map { term ->
                    wrap(originalQuery, candidate, term, preprocessorSteps)
                }
            } else {
                emptyList()
            }
        }
    }
}
