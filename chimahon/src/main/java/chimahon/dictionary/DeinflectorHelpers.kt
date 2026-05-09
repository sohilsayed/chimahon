package chimahon.dictionary

import java.util.concurrent.ConcurrentHashMap

private val transformerCache = ConcurrentHashMap<String, LanguageTransformer>()
private val ruleDeinflectorCache = ConcurrentHashMap<RuleListKey, RuleDeinflector>()
private const val MAX_DEINFLECTION_RESULTS = 200

fun suffixInflection(
    inflectedSuffix: String,
    deinflectedSuffix: String,
    conditionsIn: Set<String>,
    conditionsOut: Set<String>,
): Rule.Suffix {
    return Rule.Suffix(
        inflectedSuffix = inflectedSuffix,
        deinflectedSuffix = deinflectedSuffix,
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
        isInflected = Regex("${Regex.escape(inflectedSuffix)}$"),
    )
}

fun wholeWordInflection(
    inflectedWord: String,
    deinflectedWord: String,
    conditionsIn: Set<String>,
    conditionsOut: Set<String>,
): Rule.WholeWord {
    return Rule.WholeWord(
        inflectedWord = inflectedWord,
        deinflectedWord = deinflectedWord,
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
        isInflected = Regex("^${Regex.escape(inflectedWord)}$"),
    )
}

fun prefixInflection(
    inflectedPrefix: String,
    deinflectedPrefix: String,
    conditionsIn: Set<String>,
    conditionsOut: Set<String>,
): Rule.Prefix {
    return Rule.Prefix(
        inflectedPrefix = inflectedPrefix,
        deinflectedPrefix = deinflectedPrefix,
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
        isInflected = Regex("^${Regex.escape(inflectedPrefix)}"),
    )
}

fun sandwichInflection(
    inflectedPrefix: String,
    deinflectedPrefix: String,
    inflectedSuffix: String,
    deinflectedSuffix: String,
    conditionsIn: Set<String>,
    conditionsOut: Set<String>,
): Rule.Sandwich {
    return Rule.Sandwich(
        inflectedPrefix = inflectedPrefix,
        deinflectedPrefix = deinflectedPrefix,
        inflectedSuffix = inflectedSuffix,
        deinflectedSuffix = deinflectedSuffix,
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
        isInflected = Regex("^${Regex.escape(inflectedPrefix)}.*${Regex.escape(inflectedSuffix)}$"),
    )
}

fun customInflection(
    conditionsIn: Set<String>,
    conditionsOut: Set<String>,
    isInflected: Regex,
    deinflectFn: (String) -> String,
): Rule.Custom {
    return Rule.Custom(
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
        isInflected = isInflected,
        deinflectFn = deinflectFn,
    )
}

fun deinflectRecursive(
    text: String,
    rules: List<Rule>,
    _languageCode: String,
): List<DeinflectionResult> {
    return ruleDeinflectorCache.getOrPut(RuleListKey(rules)) {
        RuleDeinflector(rules)
    }.deinflect(text)
}

internal class RuleDeinflector(
    rules: List<Rule>,
    private val maxResults: Int = MAX_DEINFLECTION_RESULTS,
) {
    private val indexedRules = IndexedRules(rules)

    fun deinflect(text: String): List<DeinflectionResult> {
        val results = mutableListOf(text to emptySet<String>())
        val seen = hashSetOf(resultKey(text, emptySet()))
        var i = 0

        while (i < results.size && results.size < maxResults) {
            val (currentText, currentConditions) = results[i]
            for (rule in indexedRules.forText(currentText, currentConditions)) {
                val deinflected = applyRule(currentText, rule) ?: continue
                val key = resultKey(deinflected, rule.conditionsOut)
                if (!seen.add(key)) continue

                results += deinflected to rule.conditionsOut
                if (results.size >= maxResults) break
            }
            i++
        }

        return results.map { (candidate, _) -> DeinflectionResult(candidate, 0) }
    }
}

fun deinflectRecursive(
    text: String,
    descriptor: TransformDescriptor,
): List<DeinflectionResult> {
    val transformer = transformerCache.getOrPut(descriptor.language) {
        LanguageTransformer().also { it.addDescriptor(descriptor) }
    }
    val transformed = transformer.transform(text)
    return transformed.map { DeinflectionResult(it.text, it.conditions) }
}

private fun applyRule(text: String, rule: Rule): String? {
    return when (rule) {
        is Rule.Prefix -> {
            if (!text.startsWith(rule.inflectedPrefix)) return null
            rule.deinflectedPrefix + text.drop(rule.inflectedPrefix.length)
        }
        is Rule.Suffix -> {
            if (!text.endsWith(rule.inflectedSuffix)) return null
            text.dropLast(rule.inflectedSuffix.length) + rule.deinflectedSuffix
        }
        is Rule.WholeWord -> {
            if (text != rule.inflectedWord) return null
            rule.deinflectedWord
        }
        is Rule.Sandwich -> {
            if (!text.startsWith(rule.inflectedPrefix) || !text.endsWith(rule.inflectedSuffix)) return null
            if (text.length < rule.inflectedPrefix.length + rule.inflectedSuffix.length) return null
            rule.deinflectedPrefix +
                text.substring(rule.inflectedPrefix.length, text.length - rule.inflectedSuffix.length) +
                rule.deinflectedSuffix
        }
        is Rule.Custom -> {
            if (!rule.isInflected.containsMatchIn(text)) return null
            rule.deinflectFn(text)
        }
    }
}

private fun resultKey(text: String, conditions: Set<String>): String {
    return text + "\u0000" + conditions.sorted().joinToString("\u0001")
}

private class RuleListKey(private val rules: List<Rule>) {
    override fun equals(other: Any?): Boolean {
        return other is RuleListKey && rules === other.rules
    }

    override fun hashCode(): Int = System.identityHashCode(rules)
}

private class IndexedRules(rules: List<Rule>) {
    private val suffixRules = RuleTrie<Rule.Suffix>(reversed = true)
    private val prefixRules = RuleTrie<Rule>(reversed = false)
    private val wholeWordRules = ExactRuleIndex<Rule.WholeWord>()
    private val customRules: ConditionRuleIndex<Rule.Custom>

    init {
        val custom = mutableListOf<OrderedRule<Rule.Custom>>()
        for ((index, rule) in rules.withIndex()) {
            when (rule) {
                is Rule.Suffix -> suffixRules.add(rule.inflectedSuffix, OrderedRule(index, rule))
                is Rule.Prefix -> prefixRules.add(rule.inflectedPrefix, OrderedRule(index, rule))
                is Rule.Sandwich -> prefixRules.add(rule.inflectedPrefix, OrderedRule(index, rule))
                is Rule.WholeWord -> wholeWordRules.add(rule.inflectedWord, OrderedRule(index, rule))
                is Rule.Custom -> custom += OrderedRule(index, rule)
            }
        }
        customRules = ConditionRuleIndex(custom)
    }

    fun forText(text: String, conditions: Set<String>): Sequence<Rule> {
        return (suffixRules.match(text, conditions) +
            prefixRules.match(text, conditions) +
            wholeWordRules.match(text, conditions) +
            customRules.forConditions(conditions))
            .sortedBy { it.index }
            .map { it.rule }
    }
}

private data class OrderedRule<out T : Rule>(val index: Int, val rule: T)

private class RuleTrie<T : Rule>(private val reversed: Boolean) {
    private val root = Node<T>()

    fun add(text: String, rule: OrderedRule<T>) {
        var node = root
        val indices = if (reversed) text.lastIndex downTo 0 else text.indices
        for (i in indices) {
            node = node.children.getOrPut(text[i]) { Node() }
        }
        node.rules += rule
    }

    fun match(text: String, conditions: Set<String>): Sequence<OrderedRule<T>> = sequence {
        var node = root
        yieldAll(node.ruleIndex.forConditions(conditions))
        val indices = if (reversed) text.lastIndex downTo 0 else text.indices
        for (i in indices) {
            node = node.children[text[i]] ?: break
            yieldAll(node.ruleIndex.forConditions(conditions))
        }
    }

    private class Node<T : Rule> {
        val children = mutableMapOf<Char, Node<T>>()
        private val mutableRules = mutableListOf<OrderedRule<T>>()
        val rules: MutableList<OrderedRule<T>> = mutableRules
        val ruleIndex: ConditionRuleIndex<T> by lazy { ConditionRuleIndex(mutableRules) }
    }
}

private class ExactRuleIndex<T : Rule> {
    private val mutableRulesByText = mutableMapOf<String, MutableList<OrderedRule<T>>>()
    private val indexesByText: Map<String, ConditionRuleIndex<T>> by lazy {
        mutableRulesByText.mapValues { (_, rules) -> ConditionRuleIndex(rules) }
    }

    fun add(text: String, rule: OrderedRule<T>) {
        mutableRulesByText.getOrPut(text) { mutableListOf() } += rule
    }

    fun match(text: String, conditions: Set<String>): Sequence<OrderedRule<T>> {
        return indexesByText[text]?.forConditions(conditions) ?: emptySequence()
    }
}

private class ConditionRuleIndex<T : Rule>(private val rules: List<OrderedRule<T>>) {
    private val rulesByCondition: Map<String, List<OrderedRule<T>>> = buildMap {
        val mutable = mutableMapOf<String, MutableList<OrderedRule<T>>>()
        for (orderedRule in rules) {
            val rule = orderedRule.rule
            for (condition in rule.conditionsIn) {
                mutable.getOrPut(condition) { mutableListOf() } += orderedRule
            }
        }
        putAll(mutable)
    }

    fun forConditions(conditions: Set<String>): Sequence<OrderedRule<T>> {
        if (conditions.isEmpty()) return rules.asSequence()
        return conditions
            .asSequence()
            .flatMap { rulesByCondition[it].orEmpty().asSequence() }
            .distinctBy { it.index }
    }
}
