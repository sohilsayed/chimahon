package chimahon.dictionary

data class DeinflectionResult(
    val text: String,
    val conditionsOut: Set<String>,
)

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
}

interface Deinflector {
    val languageCode: String
    fun deinflect(text: String, conditions: Set<String> = emptySet()): List<DeinflectionResult>
}
