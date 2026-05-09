package chimahon.dictionary

fun suffixInflection(
    inflectedSuffix: String,
    deinflectedSuffix: String,
    conditionsIn: Set<String>,
    conditionsOut: Set<String>,
    finalStemSegment: String = "",
): Rule.Suffix = Rule.Suffix(
    inflectedSuffix,
    deinflectedSuffix,
    conditionsIn,
    conditionsOut,
    Regex("$finalStemSegment${Regex.escape(inflectedSuffix)}$"),
)

fun prefixInflection(
    inflectedPrefix: String,
    deinflectedPrefix: String,
    conditionsIn: Set<String>,
    conditionsOut: Set<String>,
    initialStemSegment: String = "",
): Rule.Prefix = Rule.Prefix(
    inflectedPrefix,
    deinflectedPrefix,
    conditionsIn,
    conditionsOut,
    Regex("^${Regex.escape(inflectedPrefix)}$initialStemSegment"),
)

fun wholeWordInflection(
    inflectedWord: String,
    deinflectedWord: String,
    conditionsIn: Set<String>,
    conditionsOut: Set<String>,
): Rule.Custom = Rule.Custom(
    conditionsIn = conditionsIn,
    conditionsOut = conditionsOut,
    isInflected = Regex("^${Regex.escape(inflectedWord)}$"),
    deinflectFn = { deinflectedWord },
)

fun deinflectRecursive(
    text: String,
    allRules: List<Rule>,
    conditions: Set<String>,
): List<DeinflectionResult> {
    val results = mutableListOf(DeinflectionResult(text, conditions))
    val seen = mutableSetOf(text to conditions)
    var index = 0
    while (index < results.size) {
        val (currentText, currentConditions) = results[index]
        for (rule in allRules) {
            if (rule.conditionsIn.isNotEmpty() && !currentConditions.containsAll(rule.conditionsIn)) continue
            if (!rule.isInflected.containsMatchIn(currentText)) continue
            val deinflected = when (rule) {
                is Rule.Suffix -> {
                    currentText.dropLast(rule.inflectedSuffix.length) + rule.deinflectedSuffix
                }
                is Rule.Prefix -> {
                    rule.deinflectedPrefix + currentText.drop(rule.inflectedPrefix.length)
                }
                is Rule.Sandwich -> {
                    rule.deinflectedPrefix +
                        currentText.drop(rule.inflectedPrefix.length).dropLast(rule.inflectedSuffix.length) +
                        rule.deinflectedSuffix
                }
                is Rule.Custom -> {
                    rule.deinflectFn(currentText)
                }
            }
            val key = deinflected to rule.conditionsOut
            if (seen.add(key)) {
                results.add(DeinflectionResult(deinflected, rule.conditionsOut))
            }
        }
        index++
    }
    return results
}
