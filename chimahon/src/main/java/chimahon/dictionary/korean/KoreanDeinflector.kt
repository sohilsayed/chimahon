package chimahon.dictionary.korean

import chimahon.dictionary.DeinflectionResult
import chimahon.dictionary.Deinflector
import chimahon.dictionary.Rule

/**
 * Korean deinflector using jamo-level suffix rules.
 *
 * Korean morphology works at the level of Hangul jamo (phonemes), so we:
 * 1. Disassemble the syllable block text into individual jamo
 * 2. Match/strip suffix jamo sequences
 * 3. Reassemble to look up in the dictionary
 *
 * Rules ported from Yomitan korean-transforms.js (suffix-only transforms).
 */
object KoreanDeinflector : Deinflector {

    override val languageCode: String = "ko"

    private fun suffixRule(
        inflectedSuffix: String,
        deinflectedSuffix: String,
        conditionsIn: Set<String>,
        conditionsOut: Set<String>,
    ) = Rule.Suffix(
        inflectedSuffix = inflectedSuffix,
        deinflectedSuffix = deinflectedSuffix,
        conditionsIn = conditionsIn,
        conditionsOut = conditionsOut,
        isInflected = Regex("${Regex.escape(inflectedSuffix)}$"),
    )

    private val v = setOf("v")
    private val adj = setOf("adj")
    private val ida = setOf("ida")
    private val p = setOf("p")
    private val f = setOf("f")
    private val eusi = setOf("eusi")
    private val vAdj = setOf("v", "adj")
    private val vAdjIda = setOf("v", "adj", "ida")
    private val pFEusi = setOf("p", "f", "eusi")

    private val rules: List<Rule> = buildList {
        // -거나
        add(suffixRule("ㄱㅓㄴㅏ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㄱㅓㄴㅏ", "", emptySet(), pFEusi))

        // -고
        add(suffixRule("ㄱㅗ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㄱㅗ", "", emptySet(), pFEusi))

        // -(느)ㄴ다
        add(suffixRule("ㄴㄷㅏ", "ㄷㅏ", emptySet(), v))
        add(suffixRule("ㄴㅡㄴㄷㅏ", "ㄷㅏ", emptySet(), v))

        // Past tense: -았다/었다
        add(suffixRule("ㅇㅏㅆㄷㅏ", "ㄷㅏ", emptySet(), vAdj))
        add(suffixRule("ㅇㅓㅆㄷㅏ", "ㄷㅏ", emptySet(), vAdj))
        // 하다 → 하였다/했다
        add(suffixRule("ㅎㅐㅆㄷㅏ", "ㅎㅏㄷㅏ", emptySet(), vAdj))
        add(suffixRule("ㅎㅏㅇㅕㅆㄷㅏ", "ㅎㅏㄷㅏ", emptySet(), vAdj))

        // -아서/어서
        add(suffixRule("ㅇㅏㅅㅓ", "ㄷㅏ", emptySet(), vAdj))
        add(suffixRule("ㅇㅓㅅㅓ", "ㄷㅏ", emptySet(), vAdj))

        // -(으)니까
        add(suffixRule("ㄴㅣㄲㅏ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㅇㅡㄴㅣㄲㅏ", "ㄷㅏ", emptySet(), vAdj))

        // -(으)ㄹ  Future/adnominal
        add(suffixRule("ㄹ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㅇㅡㄹ", "ㄷㅏ", emptySet(), vAdj))

        // -(으)면  If
        add(suffixRule("ㅁㅕㄴ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㅇㅡㅁㅕㄴ", "ㄷㅏ", emptySet(), vAdj))

        // -ㅂ니다 / -습니다  Formal polite declarative
        add(suffixRule("ㅂㄴㅣㄷㅏ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㅇㅡㅂㄴㅣㄷㅏ", "ㄷㅏ", emptySet(), vAdj))

        // -ㅂ니까  Formal polite interrogative
        add(suffixRule("ㅂㄴㅣㄲㅏ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㅇㅡㅂㄴㅣㄲㅏ", "ㄷㅏ", emptySet(), vAdj))

        // -게  Adverbial
        add(suffixRule("ㄱㅔ", "ㄷㅏ", emptySet(), vAdjIda))

        // -지  Negation / tag
        add(suffixRule("ㅈㅣ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㅈㅣ", "", emptySet(), pFEusi))

        // -어요 / -아요  Informal polite
        add(suffixRule("ㅇㅓㅇㅛ", "ㄷㅏ", emptySet(), vAdj))
        add(suffixRule("ㅇㅏㅇㅛ", "ㄷㅏ", emptySet(), vAdj))

        // Intermediate past → dictionary form
        add(suffixRule("ㄷㅏ", "", emptySet(), p + f + eusi + ida))

        // -더라도  Concessive
        add(suffixRule("ㄷㅓㄹㅏㄷㅗ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㄷㅓㄹㅏㄷㅗ", "", emptySet(), pFEusi))

        // -던 (Retrospective)
        add(suffixRule("ㄷㅓㄴ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㄷㅓㄴ", "", emptySet(), pFEusi))

        // -(으)니  Causal/sequential
        add(suffixRule("ㄴㅣ", "ㄷㅏ", emptySet(), vAdjIda))
        add(suffixRule("ㅇㅡㄴㅣ", "ㄷㅏ", emptySet(), vAdj))

        // -어/아  Short connector
        add(suffixRule("ㅇㅓ", "ㄷㅏ", emptySet(), vAdj))
        add(suffixRule("ㅇㅏ", "ㄷㅏ", emptySet(), vAdj))
    }

    override fun deinflect(text: String, conditions: Set<String>): List<DeinflectionResult> {
        // Disassemble the input into jamo
        val disassembled = HangulUtils.disassemble(text)
        val results = mutableListOf(DeinflectionResult(text, conditions))
        val seen = mutableSetOf(text to conditions)

        deinflectRecursive(text, disassembled, conditions, seen, results, depth = 0)

        return results
    }

    private fun deinflectRecursive(
        originalText: String,
        disassembled: String,
        conditions: Set<String>,
        seen: MutableSet<Pair<String, Set<String>>>,
        results: MutableList<DeinflectionResult>,
        depth: Int,
    ) {
        if (depth >= 5) return

        for (rule in rules) {
            if (conditions.isNotEmpty() && (rule.conditionsIn intersect conditions).isEmpty()) continue
            if (rule !is Rule.Suffix) continue

            val inflectedSuffix = rule.inflectedSuffix
            if (!disassembled.endsWith(inflectedSuffix)) continue

            val baseDisassembled = disassembled.dropLast(inflectedSuffix.length) + rule.deinflectedSuffix
            val assembled = HangulUtils.assemble(baseDisassembled)

            val key = assembled to rule.conditionsOut
            if (seen.add(key)) {
                results.add(DeinflectionResult(assembled, rule.conditionsOut))
                deinflectRecursive(assembled, baseDisassembled, rule.conditionsOut, seen, results, depth + 1)
            }
        }
    }
}
