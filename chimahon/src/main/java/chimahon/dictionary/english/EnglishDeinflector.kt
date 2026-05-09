package chimahon.dictionary.english

import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector
import chimahon.dictionary.DeinflectorHelpers.deinflectRecursive
import chimahon.dictionary.Rule
import chimahon.dictionary.prefixInflection
import chimahon.dictionary.suffixInflection
import chimahon.dictionary.wholeWordInflection

object EnglishDeinflector : Deinflector {

    override fun deinflect(
        text: String,
        languageCode: String,
        conditions: Set<String>,
    ): List<DeinflectionResult> {
        return deinflectRecursive(text, allRules, conditions)
    }

    private fun doubledConsonantInflections(consonants: String, suffix: String, conditionsIn: Set<String>, conditionsOut: Set<String>): List<Rule.Suffix> {
        return consonants.map { consonant ->
            suffixInflection("$consonant$consonant$suffix", "$consonant", conditionsIn, conditionsOut)
        }
    }

    private val phrasalVerbParticles = listOf(
        "aboard", "about", "above", "across", "ahead", "alongside", "apart", "around",
        "aside", "astray", "away", "back", "before", "behind", "below", "beneath",
        "besides", "between", "beyond", "by", "close", "down", "east", "west", "north",
        "south", "eastward", "westward", "northward", "southward", "forward", "backward",
        "backwards", "forwards", "home", "in", "inside", "instead", "near", "off", "on",
        "opposite", "out", "outside", "over", "overhead", "past", "round", "since",
        "through", "throughout", "together", "under", "underneath", "up", "within",
        "without",
    )

    private val phrasalVerbPrepositions = listOf(
        "aback", "about", "above", "across", "after", "against", "ahead", "along",
        "among", "apart", "around", "as", "aside", "at", "away", "back", "before",
        "behind", "below", "between", "beyond", "by", "down", "even", "for", "forth",
        "forward", "from", "in", "into", "of", "off", "on", "onto", "open", "out",
        "over", "past", "round", "through", "to", "together", "toward", "towards",
        "under", "up", "upon", "way", "with", "without",
    )

    private val particlesDisjunction = phrasalVerbParticles.joinToString("|")
    private val phrasalVerbWordSet = (phrasalVerbParticles + phrasalVerbPrepositions).toSet()
    private val phrasalVerbWordDisjunction = phrasalVerbWordSet.joinToString("|")

    // Creates phrasal verb rules for each suffix inflection
    private fun createPhrasalVerbInflections(sourceRules: List<Rule.Suffix>): List<Rule.Custom> {
        return sourceRules.mapNotNull { rule ->
            val inflectedSuffix = rule.inflectedSuffix
            val deinflectedSuffix = rule.deinflectedSuffix
            if (inflectedSuffix.isEmpty()) return@mapNotNull null
            val regex = Regex("^[a-zA-Z]*$inflectedSuffix (?:$phrasalVerbWordDisjunction)")
            Rule.Custom(
                conditionsIn = setOf("v"),
                conditionsOut = setOf("v_phr"),
                isInflected = regex,
                deinflectFn = { term ->
                    term.replace(Regex("(?<=)$inflectedSuffix(?= (?:$phrasalVerbWordDisjunction))")) { deinflectedSuffix }
                },
            )
        }
    }

    private val pastSuffixInflections: List<Rule.Suffix> = listOf(
        suffixInflection("ed", "", setOf("v"), setOf("v")),
        suffixInflection("ed", "e", setOf("v"), setOf("v")),
        suffixInflection("ied", "y", setOf("v"), setOf("v")),
        suffixInflection("cked", "c", setOf("v"), setOf("v")),
        *doubledConsonantInflections("bdgklmnprstz", "ed", setOf("v"), setOf("v")).toTypedArray(),
        suffixInflection("laid", "lay", setOf("v"), setOf("v")),
        suffixInflection("paid", "pay", setOf("v"), setOf("v")),
        suffixInflection("said", "say", setOf("v"), setOf("v")),
    )

    private val ingSuffixInflections: List<Rule.Suffix> = listOf(
        suffixInflection("ing", "", setOf("v"), setOf("v")),
        suffixInflection("ing", "e", setOf("v"), setOf("v")),
        suffixInflection("ying", "ie", setOf("v"), setOf("v")),
        suffixInflection("cking", "c", setOf("v"), setOf("v")),
        *doubledConsonantInflections("bdgklmnprstz", "ing", setOf("v"), setOf("v")).toTypedArray(),
    )

    private val thirdPersonSgPresentSuffixInflections: List<Rule.Suffix> = listOf(
        suffixInflection("s", "", setOf("v"), setOf("v")),
        suffixInflection("es", "", setOf("v"), setOf("v")),
        suffixInflection("ies", "y", setOf("v"), setOf("v")),
    )

    private val allRules: List<Rule> = buildList {
        // Plural
        add(suffixInflection("s", "", setOf("np"), setOf("ns")))
        add(suffixInflection("es", "", setOf("np"), setOf("ns")))
        add(suffixInflection("ies", "y", setOf("np"), setOf("ns")))
        add(suffixInflection("ves", "fe", setOf("np"), setOf("ns")))
        add(suffixInflection("ves", "f", setOf("np"), setOf("ns")))

        // Possessive
        add(suffixInflection("'s", "", setOf("n"), setOf("n")))
        add(suffixInflection("s'", "s", setOf("n"), setOf("n")))

        // Past tense
        addAll(pastSuffixInflections)
        addAll(createPhrasalVerbInflections(pastSuffixInflections))

        // Present participle
        addAll(ingSuffixInflections)
        addAll(createPhrasalVerbInflections(ingSuffixInflections))

        // Third person singular present
        addAll(thirdPersonSgPresentSuffixInflections)
        addAll(createPhrasalVerbInflections(thirdPersonSgPresentSuffixInflections))

        // Phrasal verb with interposed object: "word object particle" -> "word particle"
        add(Rule.Custom(
            conditionsIn = emptySet(),
            conditionsOut = setOf("v_phr"),
            isInflected = Regex("^\\w* (?:(?!\\b($phrasalVerbWordDisjunction)\\b).)+ (?:$particlesDisjunction)"),
            deinflectFn = { term ->
                term.replace(Regex("(?<=\\w) (?:(?!\\b($phrasalVerbWordDisjunction)\\b).)+ (?=(?:$particlesDisjunction))"), " ")
            },
        ))

        // Archaic: 'd -> ed
        add(suffixInflection("'d", "ed", setOf("v"), setOf("v")))

        // Adverb: -ly
        add(suffixInflection("ly", "", setOf("adv"), setOf("adj")))
        add(suffixInflection("ily", "y", setOf("adv"), setOf("adj")))
        add(suffixInflection("ly", "le", setOf("adv"), setOf("adj")))

        // Comparative
        add(suffixInflection("er", "", setOf("adj"), setOf("adj")))
        add(suffixInflection("er", "e", setOf("adj"), setOf("adj")))
        add(suffixInflection("ier", "y", setOf("adj"), setOf("adj")))
        addAll(doubledConsonantInflections("bdgmnt", "er", setOf("adj"), setOf("adj")))

        // Superlative
        add(suffixInflection("est", "", setOf("adj"), setOf("adj")))
        add(suffixInflection("est", "e", setOf("adj"), setOf("adj")))
        add(suffixInflection("iest", "y", setOf("adj"), setOf("adj")))
        addAll(doubledConsonantInflections("bdgmnt", "est", setOf("adj"), setOf("adj")))

        // Dropped g: in' -> ing
        add(suffixInflection("in'", "ing", setOf("v"), setOf("v")))

        // -y adjective from noun/verb
        add(suffixInflection("y", "", setOf("adj"), setOf("n", "v")))
        add(suffixInflection("y", "e", setOf("adj"), setOf("n", "v")))
        addAll(doubledConsonantInflections("glmnprst", "y", emptySet(), setOf("n", "v")))

        // un- prefix
        add(prefixInflection("un", "", setOf("adj", "adv", "v"), setOf("adj", "adv", "v")))

        // going-to future
        add(prefixInflection("going to ", "", setOf("v"), setOf("v")))

        // will future
        add(prefixInflection("will ", "", setOf("v"), setOf("v")))

        // Imperative negative
        add(prefixInflection("don't ", "", setOf("v"), setOf("v")))
        add(prefixInflection("do not ", "", setOf("v"), setOf("v")))

        // -able
        add(suffixInflection("able", "", setOf("v"), setOf("adj")))
        add(suffixInflection("able", "e", setOf("v"), setOf("adj")))
        add(suffixInflection("iable", "y", setOf("v"), setOf("adj")))
        addAll(doubledConsonantInflections("bdgklmnprstz", "able", setOf("v"), setOf("adj")))
    }
}
