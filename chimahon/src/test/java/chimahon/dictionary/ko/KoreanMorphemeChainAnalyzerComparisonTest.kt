package chimahon.dictionary.ko

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class KoreanMorphemeChainAnalyzerComparisonTest {

    @Test
    fun `compare analyzer output with current yomitan style deinflector`() {
        val cases = listOf(
            ComparisonCase("읽어서입니다만"),
            ComparisonCase("입니다만"),
            ComparisonCase("먹는다고", oldCandidatesNewAnalyzerMustCover = setOf("먹다")),
            ComparisonCase("들어서", oldCandidatesNewAnalyzerMustCover = setOf("들다", "듣다")),
            ComparisonCase("도와서", oldCandidatesNewAnalyzerMustCover = setOf("돕다")),
            ComparisonCase("학생이라고"),
            ComparisonCase("찾아왔다", oldCandidatesNewAnalyzerMustCover = setOf("찾아오다", "찾다", "오다")),
            ComparisonCase("찾아와", oldCandidatesNewAnalyzerMustCover = setOf("찾아오다")),
            ComparisonCase("찾아왔다고", oldCandidatesNewAnalyzerMustCover = setOf("찾아오다")),
            ComparisonCase("찾아봐", oldCandidatesNewAnalyzerMustCover = setOf("찾아보다", "찾다", "보다")),
            ComparisonCase("갖췄다", oldCandidatesNewAnalyzerMustCover = setOf("갖추다")),
            ComparisonCase("나눴다", oldCandidatesNewAnalyzerMustCover = setOf("나누다")),
            ComparisonCase("나눠", oldCandidatesNewAnalyzerMustCover = setOf("나누다")),
            ComparisonCase("됐다", oldCandidatesNewAnalyzerMustCover = setOf("되다")),
            ComparisonCase("돼", oldCandidatesNewAnalyzerMustCover = setOf("되다")),
            ComparisonCase("와", oldCandidatesNewAnalyzerMustCover = setOf("오다")),
            ComparisonCase("왔다", oldCandidatesNewAnalyzerMustCover = setOf("오다")),
            ComparisonCase("봐", oldCandidatesNewAnalyzerMustCover = setOf("보다")),
            ComparisonCase("봤다", oldCandidatesNewAnalyzerMustCover = setOf("보다")),
            ComparisonCase("찾거나", oldCandidatesNewAnalyzerMustCover = setOf("찾다")),
            ComparisonCase("읽으면", oldCandidatesNewAnalyzerMustCover = setOf("읽다")),
            ComparisonCase("아니까", oldCandidatesNewAnalyzerMustCover = setOf("알다")),
        )

        for (case in cases) {
            val currentCandidates = currentDeinflectorCandidates(case.surface)
            val analyzerParses = KoreanMorphemeChainAnalyzer.analyze(case.surface, maxResults = 20)
            val analyzerCandidates = analyzerParses.flatMap { it.lemmaCandidates }.toSet()

            assertTrue(currentCandidates.isNotEmpty(), "Current deinflector should return at least itself for ${case.surface}")
            assertTrue(analyzerParses.isNotEmpty(), "Analyzer should return at least one parse for ${case.surface}")
            assertTrue(
                analyzerCandidates.containsAll(case.oldCandidatesNewAnalyzerMustCover),
                "${case.surface} should cover old candidates ${case.oldCandidatesNewAnalyzerMustCover}, got $analyzerCandidates",
            )

            println("CASE: ${case.surface}")
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

    @Test
    fun `current deinflector covers local yomitan korean transform fixtures`() {
        val fixturePath = localYomitanKoreanTransformFixture()
        assumeTrue(Files.exists(fixturePath), "Local Yomitan fixture is not present")

        val cases = parseValidYomitanTransformCases(String(Files.readAllBytes(fixturePath), Charsets.UTF_8))
        assertEquals(608, cases.size, "Yomitan fixture case count changed")

        val missing = cases.filterNot { case ->
            case.expectedTerm in currentDeinflectorCandidates(case.source, maxCandidates = 200)
        }

        assertTrue(
            missing.isEmpty(),
            "Current Korean deinflector missed Yomitan fixture cases: ${missing.take(20)}",
        )
    }

    @Test
    fun `analyzer covers local yomitan korean transform fixtures`() {
        val fixturePath = localYomitanKoreanTransformFixture()
        assumeTrue(Files.exists(fixturePath), "Local Yomitan fixture is not present")

        val cases = parseValidYomitanTransformCases(String(Files.readAllBytes(fixturePath), Charsets.UTF_8))
        assertEquals(608, cases.size, "Yomitan fixture case count changed")

        val missing = cases.mapNotNull { case ->
            val analyzerCandidates = KoreanMorphemeChainAnalyzer.analyze(case.source, maxResults = 50)
                .flatMap { it.lemmaCandidates }
                .toSet()
            if (case.expectedTerm in analyzerCandidates) {
                null
            } else {
                val currentCandidates = currentDeinflectorCandidates(case.source, maxCandidates = 20)
                "${case.category}: ${case.source} -> ${case.expectedTerm}; " +
                    "current=${currentCandidates.joinToString()}; analyzer=${analyzerCandidates.joinToString()}"
            }
        }

        assertTrue(
            missing.isEmpty(),
            "Analyzer missed Yomitan fixture cases (${missing.size}/${cases.size}):\n${missing.take(50).joinToString("\n")}",
        )
    }

    private fun localYomitanKoreanTransformFixture(): Path {
        var directory: Path? = Paths.get("").toAbsolutePath()
        while (directory != null) {
            val fixture = directory.resolve(Paths.get("ref", "yomitan", "test", "language", "korean-transforms.test.js"))
            if (Files.exists(fixture)) return fixture
            directory = directory.parent
        }
        return Paths.get("ref", "yomitan", "test", "language", "korean-transforms.test.js")
    }

    private data class ComparisonCase(
        val surface: String,
        val oldCandidatesNewAnalyzerMustCover: Set<String> = emptySet(),
    )

    private data class YomitanTransformCase(
        val category: String,
        val source: String,
        val expectedTerm: String,
    )

    private fun parseValidYomitanTransformCases(source: String): List<YomitanTransformCase> {
        val categoryRegex = Regex("""category: '([^']+)'""")
        val validRegex = Regex("""valid: (true|false)""")
        val caseRegex = Regex("""\{term: '([^']+)', source: '([^']+)',\s+rule: [^,]+, reasons: \[[^\]]*]\}""")

        var category = ""
        var valid = false
        val cases = mutableListOf<YomitanTransformCase>()
        for (line in source.lineSequence()) {
            categoryRegex.find(line)?.let { category = it.groupValues[1] }
            validRegex.find(line)?.let { valid = it.groupValues[1] == "true" }
            val match = caseRegex.find(line) ?: continue
            if (valid) {
                cases += YomitanTransformCase(
                    category = category,
                    expectedTerm = match.groupValues[1],
                    source = match.groupValues[2],
                )
            }
        }
        return cases
    }

    private fun currentDeinflectorCandidates(text: String, maxCandidates: Int = 20): List<String> {
        return KoreanDeinflector.preProcess(text)
            .flatMap { preprocessed -> KoreanDeinflector.deinflect(preprocessed, "ko") }
            .map { it.text }
            .distinct()
            .take(maxCandidates)
    }

    private fun KoreanMorphemeParse.displayChain(): String {
        return segments.joinToString(" + ") { it.name ?: it.surface }
    }
}
