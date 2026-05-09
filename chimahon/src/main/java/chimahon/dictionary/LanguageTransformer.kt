package chimahon.dictionary

import java.util.concurrent.ConcurrentHashMap

data class TransformedText(
    val text: String,
    val conditions: Int,
    val trace: List<TraceFrame>,
)

data class TraceFrame(
    val text: String,
    val transform: String,
    val ruleIndex: Int,
)

data class Transform(
    val id: String,
    val name: String,
    val description: String?,
    val rules: List<CompiledRule>,
    val heuristic: Regex,
)

internal data class CompiledRule(
    val type: String,
    val isInflected: Regex,
    val deinflect: (String) -> String,
    val conditionsIn: Int,
    val conditionsOut: Int,
)

class LanguageTransformer {

    private var transforms: List<Transform> = emptyList()
    private var conditionTypeToConditionFlagsMap: Map<String, Int> = emptyMap()
    private var partOfSpeechToConditionFlagsMap: Map<String, Int> = emptyMap()
    private var nextFlagIndex: Int = 0

    fun clear() {
        nextFlagIndex = 0
        transforms = emptyList()
        conditionTypeToConditionFlagsMap = emptyMap()
        partOfSpeechToConditionFlagsMap = emptyMap()
    }

    fun addDescriptor(descriptor: TransformDescriptor) {
        val conditionEntries = descriptor.conditions.entries.toList()
        val (conditionFlagsMap, nextFlagIndex) = getConditionFlagsMap(conditionEntries, nextFlagIndex)

        val transforms2 = mutableListOf<Transform>()

        for ((transformId, transform) in descriptor.transforms) {
            val rules2 = transform.rules.mapIndexed { ruleIndex, rule ->
                val conditionFlagsIn = getConditionFlagsStrict(conditionFlagsMap, rule.conditionsIn)
                    ?: throw Error("Invalid conditionsIn for transform $transformId.rules[$ruleIndex]")
                val conditionFlagsOut = getConditionFlagsStrict(conditionFlagsMap, rule.conditionsOut)
                    ?: throw Error("Invalid conditionsOut for transform $transformId.rules[$ruleIndex]")
                CompiledRule(
                    type = rule.type,
                    isInflected = rule.isInflected,
                    deinflect = rule.deinflectFn,
                    conditionsIn = conditionFlagsIn,
                    conditionsOut = conditionFlagsOut,
                )
            }

            val heuristicSources = transform.rules.map { it.isInflected.pattern }
            val heuristic = if (heuristicSources.isNotEmpty()) {
                Regex(heuristicSources.joinToString("|"))
            } else {
                Regex("$^") // matches nothing
            }

            transforms2.add(
                Transform(
                    id = transformId,
                    name = transform.name,
                    description = transform.description,
                    rules = rules2,
                    heuristic = heuristic,
                ),
            )
        }

        this.nextFlagIndex = nextFlagIndex
        this.transforms = transforms2

        for ((type, condition) in conditionEntries) {
            val flags = conditionFlagsMap[type]
            if (flags == null) continue
            conditionTypeToConditionFlagsMap = conditionTypeToConditionFlagsMap + (type to flags)
            if (condition.isDictionaryForm) {
                partOfSpeechToConditionFlagsMap = partOfSpeechToConditionFlagsMap + (type to flags)
            }
        }
    }

    fun getConditionFlagsFromPartsOfSpeech(partsOfSpeech: List<String>): Int {
        return getConditionFlags(partOfSpeechToConditionFlagsMap, partsOfSpeech)
    }

    fun getConditionFlagsFromConditionTypes(conditionTypes: List<String>): Int {
        return getConditionFlags(conditionTypeToConditionFlagsMap, conditionTypes)
    }

    fun getConditionFlagsFromConditionType(conditionType: String): Int {
        return getConditionFlags(conditionTypeToConditionFlagsMap, listOf(conditionType))
    }

    fun transform(sourceText: String): List<TransformedText> {
        val results = mutableListOf(createTransformedText(sourceText, 0, emptyList()))
        var i = 0
        while (i < results.size) {
            val current = results[i]
            val text = current.text
            val conditions = current.conditions
            val trace = current.trace

            for (transform in transforms) {
                if (!transform.heuristic.test(text)) continue

                val transformId = transform.id
                val rules = transform.rules
                for (ruleIndex in rules.indices) {
                    val rule = rules[ruleIndex]

                    if (!conditionsMatch(conditions, rule.conditionsIn)) continue
                    if (!rule.isInflected.test(text)) continue

                    val isCycle = trace.any { it.transform == transformId && it.ruleIndex == ruleIndex && it.text == text }
                    if (isCycle) {
                        continue
                    }

                    val deinflected = rule.deinflect(text)
                    val newTrace = listOf(TraceFrame(text, transformId, ruleIndex)) + trace

                    results.add(createTransformedText(deinflected, rule.conditionsOut, newTrace))

                    if (results.size >= MAX_RESULTS) {
                        return results
                    }
                }
            }
            i++
        }
        return results
    }

    fun isCompiled(): Boolean = transforms.isNotEmpty()

    private fun createTransformedText(text: String, conditions: Int, trace: List<TraceFrame>): TransformedText {
        return TransformedText(text, conditions, trace)
    }

    private fun conditionsMatch(currentConditions: Int, nextConditions: Int): Boolean {
        return currentConditions == 0 || (currentConditions and nextConditions) != 0
    }

    private data class ConditionFlagsMapResult(
        val conditionFlagsMap: Map<String, Int>,
        val nextFlagIndex: Int,
    )

    private fun getConditionFlagsMap(conditions: List<Map.Entry<String, Condition>>, nextFlagIndex: Int): ConditionFlagsMapResult {
        val conditionFlagsMap = mutableMapOf<String, Int>()
        var currentNextFlagIndex = nextFlagIndex
        var targets = conditions

        while (targets.isNotEmpty()) {
            val nextTargets = mutableListOf<Map.Entry<String, Condition>>()
            for (target in targets) {
                val (type, condition) = target
                val subConditions = condition.subConditions
                var flags = 0
                if (subConditions == null) {
                    if (currentNextFlagIndex >= 32) {
                        throw Error("Maximum number of conditions was exceeded")
                    }
                    flags = 1 shl currentNextFlagIndex
                    currentNextFlagIndex++
                } else {
                    val multiFlags = getConditionFlagsStrict(conditionFlagsMap, subConditions)
                    if (multiFlags == null) {
                        nextTargets.add(target)
                        continue
                    } else {
                        flags = multiFlags
                    }
                }
                conditionFlagsMap[type] = flags
            }
            if (nextTargets.size == targets.size) {
                throw Error("Maximum number of conditions was exceeded")
            }
            targets = nextTargets
        }
        return ConditionFlagsMapResult(conditionFlagsMap, currentNextFlagIndex)
    }

    private fun getConditionFlagsStrict(conditionFlagsMap: Map<String, Int>, conditionTypes: List<String>): Int? {
        var flags = 0
        for (conditionType in conditionTypes) {
            val flags2 = conditionFlagsMap[conditionType] ?: return null
            flags = flags or flags2
        }
        return flags
    }

    private fun getConditionFlags(conditionFlagsMap: Map<String, Int>, conditionTypes: List<String>): Int {
        var flags = 0
        for (conditionType in conditionTypes) {
            val flags2 = conditionFlagsMap[conditionType] ?: 0
            flags = flags or flags2
        }
        return flags
    }

    companion object {
        private const val MAX_RESULTS = 200
    }
}

data class TransformDescriptor(
    val language: String,
    val conditions: Map<String, Condition>,
    val transforms: Map<String, TransformDefinition>,
)

data class Condition(
    val name: String,
    val isDictionaryForm: Boolean,
    val subConditions: List<String>? = null,
)

data class TransformDefinition(
    val name: String,
    val description: String? = null,
    val rules: List<RuleDefinition>,
)

data class RuleDefinition(
    val type: String,
    val isInflected: Regex,
    val deinflectFn: (String) -> String,
    val conditionsIn: List<String>,
    val conditionsOut: List<String>,
)

private val transformerCache = ConcurrentHashMap<String, LanguageTransformer>()

fun suffixInflection(
    inflectedSuffix: String,
    deinflectedSuffix: String,
    conditionsIn: List<String>,
    conditionsOut: List<String>,
): RuleDefinition {
    val inflectedLen = inflectedSuffix.length
    return RuleDefinition(
        type = "suffix",
        isInflected = Regex("${Regex.escape(inflectedSuffix)}$"),
        deinflectFn = { text -> text.dropLast(inflectedLen) + deinflectedSuffix },
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
    )
}

fun prefixInflection(
    inflectedPrefix: String,
    deinflectedPrefix: String,
    conditionsIn: List<String>,
    conditionsOut: List<String>,
): RuleDefinition {
    val inflectedLen = inflectedPrefix.length
    return RuleDefinition(
        type = "prefix",
        isInflected = Regex("^${Regex.escape(inflectedPrefix)}"),
        deinflectFn = { text -> deinflectedPrefix + text.drop(inflectedLen) },
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
    )
}

fun wholeWordInflection(
    inflectedWord: String,
    deinflectedWord: String,
    conditionsIn: List<String>,
    conditionsOut: List<String>,
): RuleDefinition {
    return RuleDefinition(
        type = "wholeWord",
        isInflected = Regex("^${Regex.escape(inflectedWord)}$"),
        deinflectFn = { deinflectedWord },
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
    )
}

fun deinflectRecursive(
    text: String,
    descriptor: TransformDescriptor,
): List<DeinflectionResult> {
    val transformer = transformerCache.getOrPut(descriptor.language) {
        LanguageTransformer().also {
            it.addDescriptor(descriptor)
        }
    }
    val transformed = transformer.transform(text)
    return transformed.map { DeinflectionResult(it.text, it.conditions) }
}