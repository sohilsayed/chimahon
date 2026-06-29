package chimahon.dictionary.ko

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KoreanMorphemeChainAnalyzerComparisonTest {

    @Test
    fun `compare analyzer output with current yomitan style deinflector`() {
        val cases = listOf(
            "읽어서입니다만",
            "입니다만",
            "먹는다고",
            "들어서",
            "도와서",
            "학생이라고",
        )

        for (case in cases) {
            val currentCandidates = currentDeinflectorCandidates(case)
            val analyzerParses = KoreanMorphemeChainAnalyzer.analyze(case, maxResults = 5)

            assertTrue(currentCandidates.isNotEmpty(), "Current deinflector should return at least itself for $case")
            assertTrue(analyzerParses.isNotEmpty(), "Analyzer should return at least one parse for $case")

            println("CASE: $case")
            println("CURRENT_CANDIDATES: ${currentCandidates.joinToString()}")
            analyzerParses.forEachIndexed { index, parse ->
                println("ANALYZER[$index]_LEMMAS: ${parse.lemmaCandidates.joinToString()}")
                println("ANALYZER[$index]_CHAIN: ${parse.displayChain()}")
                println("ANALYZER[$index]_TAGS: ${parse.segments.joinToString(" + ") { it.tag.name }}")
                if (parse.alternations.isNotEmpty()) {
                    println("ANALYZER[$index]_ALTERNATIONS: ${parse.alternations.joinToString { it.name }}")
                }
            }
            println()
        }
    }

    private fun currentDeinflectorCandidates(text: String): List<String> {
        return KoreanDeinflector.preProcess(text)
            .flatMap { preprocessed -> KoreanDeinflector.deinflect(preprocessed, "ko") }
            .map { it.text }
            .distinct()
            .take(20)
    }

    private fun KoreanMorphemeParse.displayChain(): String {
        return segments.joinToString(" + ") { it.name ?: it.surface }
    }
}
