package chimahon.dictionary

/**
 * Result of a single deinflection step.
 * [text] is the candidate dictionary form; [conditionsOut] are the grammar tags
 * that must be satisfied by the next rule in the chain.
 */
data class DeinflectionResult(val text: String, val conditionsOut: Set<String>)

/**
 * A morphological transformation rule.  Three shapes are supported:
 * - [Prefix]   – strip/replace a leading segment
 * - [Suffix]   – strip/replace a trailing segment
 * - [Sandwich] – strip/replace both ends simultaneously
 */
sealed class Rule {
    abstract val conditionsIn: Set<String>
    abstract val conditionsOut: Set<String>
    abstract val isInflected: Regex

    data class Prefix(
        val inflectedPrefix: String,
        val deinflectedPrefix: String,
        override val conditionsIn: Set<String>,
        override val conditionsOut: Set<String>,
        override val isInflected: Regex,
    ) : Rule()

    data class Suffix(
        val inflectedSuffix: String,
        val deinflectedSuffix: String,
        override val conditionsIn: Set<String>,
        override val conditionsOut: Set<String>,
        override val isInflected: Regex,
    ) : Rule()

    data class Sandwich(
        val inflectedPrefix: String,
        val deinflectedPrefix: String,
        val inflectedSuffix: String,
        val deinflectedSuffix: String,
        override val conditionsIn: Set<String>,
        override val conditionsOut: Set<String>,
        override val isInflected: Regex,
    ) : Rule()

    /**
     * Custom rule that uses a lambda for deinflection.
     * Used for complex patterns (e.g. German past participles, phrasal verbs).
     */
    data class Custom(
        override val conditionsIn: Set<String>,
        override val conditionsOut: Set<String>,
        override val isInflected: Regex,
        val deinflectFn: (String) -> String,
    ) : Rule()
}

interface Deinflector {
    /** Pre-process the raw query (e.g., normalization, cleaning). */
    fun preProcess(text: String): List<String> = listOf(text)

    /** Generate candidate dictionary forms from the (possibly preprocessed) text. */
    fun deinflect(text: String, languageCode: String, conditions: Set<String> = emptySet()): List<DeinflectionResult>

    /** Map native JNI results back to the original query context. */
    fun wrapResults(
        originalQuery: String,
        candidates: List<String>,
        terms: List<chimahon.TermResult>
    ): List<chimahon.LookupResult> {
        val termsByText = terms.groupBy { it.expression }
        return candidates.flatMap { candidate ->
            val termList = termsByText[candidate]
            if (termList != null && termList.isNotEmpty()) {
                termList.map { term ->
                    chimahon.LookupResult(
                        matched = originalQuery,
                        deinflected = candidate,
                        process = emptyArray(),
                        term = term,
                        preprocessorSteps = 0,
                    )
                }
            } else {
                emptyList()
            }
        }
    }
}
