package chimahon.dictionary.english

import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector
import chimahon.dictionary.Rule

/**
 * Basic English deinflector covering the most common morphological patterns:
 * plurals, past tense, progressive, comparatives, adverbs.
 */
object EnglishDeinflector : Deinflector {

    override val languageCode: String = "en"

    private fun suffixRule(
        inflected: String,
        deinflected: String,
        conditionsIn: Set<String> = emptySet(),
        conditionsOut: Set<String> = emptySet(),
    ) = Rule.Suffix(
        inflectedSuffix = inflected,
        deinflectedSuffix = deinflected,
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
        isInflected = Regex("${Regex.escape(inflected)}$"),
    )

    private val noun = setOf("n")
    private val verb = setOf("v")
    private val adj = setOf("adj")
    private val adv = setOf("adv")

    private val rules: List<Rule> = buildList {
        // Plural nouns
        add(suffixRule("ies", "y", conditionsOut = noun))
        add(suffixRule("ves", "f", conditionsOut = noun))          // e.g. leaves → leaf
        add(suffixRule("ves", "fe", conditionsOut = noun))         // e.g. knives → knife
        add(suffixRule("oes", "", conditionsOut = noun))           // e.g. potatoes
        add(suffixRule("ses", "s", conditionsOut = noun))          // e.g. buses
        add(suffixRule("xes", "x", conditionsOut = noun))
        add(suffixRule("zes", "z", conditionsOut = noun))
        add(suffixRule("ches", "ch", conditionsOut = noun))
        add(suffixRule("shes", "sh", conditionsOut = noun))
        add(suffixRule("s", "", conditionsOut = noun))             // generic -s plural

        // Past tense
        add(suffixRule("ied", "y", conditionsOut = verb))
        add(suffixRule("ed", "e", conditionsOut = verb))           // e.g. used → use
        add(suffixRule("ed", "", conditionsOut = verb))            // e.g. walked → walk

        // Progressive -ing
        add(suffixRule("ying", "ie", conditionsOut = verb))        // e.g. lying → lie
        add(suffixRule("ing", "e", conditionsOut = verb))          // e.g. running? No — writing → write
        add(suffixRule("ing", "", conditionsOut = verb))           // e.g. running → run

        // Third-person singular
        add(suffixRule("ies", "y", conditionsOut = verb))
        add(suffixRule("es", "", conditionsOut = verb))
        add(suffixRule("s", "", conditionsOut = verb))

        // Comparative / superlative adjectives
        add(suffixRule("ier", "y", conditionsOut = adj))
        add(suffixRule("iest", "y", conditionsOut = adj))
        add(suffixRule("er", "e", conditionsOut = adj))
        add(suffixRule("est", "e", conditionsOut = adj))
        add(suffixRule("er", "", conditionsOut = adj))
        add(suffixRule("est", "", conditionsOut = adj))

        // Adverbs from adjectives
        add(suffixRule("ily", "y", conditionsOut = adv))
        add(suffixRule("ly", "e", conditionsOut = adv))
        add(suffixRule("ly", "", conditionsOut = adv))
    }

    override fun deinflect(text: String, conditions: Set<String>): List<DeinflectionResult> {
        val results = mutableListOf(DeinflectionResult(text, conditions))
        val seen = mutableSetOf(text to conditions)

        deinflectRecursive(text, conditions, seen, results, depth = 0)

        return results
    }

    private fun deinflectRecursive(
        current: String,
        conditions: Set<String>,
        seen: MutableSet<Pair<String, Set<String>>>,
        results: MutableList<DeinflectionResult>,
        depth: Int,
    ) {
        if (depth >= 3 || current.length < 3) return

        for (rule in rules) {
            if (rule !is Rule.Suffix) continue
            if (conditions.isNotEmpty() && (rule.conditionsIn intersect conditions).isEmpty()) continue
            if (!rule.isInflected.containsMatchIn(current)) continue

            val base = current.dropLast(rule.inflectedSuffix.length) + rule.deinflectedSuffix
            if (base.length < 2) continue

            val key = base to rule.conditionsOut
            if (seen.add(key)) {
                results.add(DeinflectionResult(base, rule.conditionsOut))
                deinflectRecursive(base, rule.conditionsOut, seen, results, depth + 1)
            }
        }
    }
}
