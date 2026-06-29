package chimahon.dictionary.ko

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KoreanMorphemeChainAnalyzerTest {

    @Test
    fun `decomposes stacked connective copula final ending and particle`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("읽어서입니다만").first()

        assertTrue("읽다" in parse.lemmaCandidates)
        assertEquals(
            listOf(
                KoreanMorphemeTag.Stem,
                KoreanMorphemeTag.ConnectiveEnding,
                KoreanMorphemeTag.Copula,
                KoreanMorphemeTag.FinalEnding,
                KoreanMorphemeTag.Particle,
            ),
            parse.segments.map { it.tag },
        )
        assertEquals(listOf("읽", "-아/어서", "이다", "-(스)ㅂ니다", "만"), parse.displayParts())
    }

    @Test
    fun `analyzes copula chain when lookup starts at imnidaman`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("입니다만").first()

        assertEquals(listOf("이다"), parse.lemmaCandidates)
        assertEquals(
            listOf(
                KoreanMorphemeTag.Copula,
                KoreanMorphemeTag.FinalEnding,
                KoreanMorphemeTag.Particle,
            ),
            parse.segments.map { it.tag },
        )
        assertEquals(listOf("이다", "-(스)ㅂ니다", "만"), parse.displayParts())
    }

    @Test
    fun `decomposes direct quotation ending`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("먹는다고").first()

        assertTrue("먹다" in parse.lemmaCandidates)
        assertEquals(
            listOf(KoreanMorphemeTag.Stem, KoreanMorphemeTag.QuotationEnding),
            parse.segments.map { it.tag },
        )
        assertEquals(listOf("먹", "-는다고"), parse.displayParts())
    }

    @Test
    fun `offers regular and irregular d stem candidates`() {
        val parses = KoreanMorphemeChainAnalyzer.analyze("들어서")

        assertTrue(parses.any { "들다" in it.lemmaCandidates && it.alternations.isEmpty() })
        assertAlternation(parses, "듣다", "ㄷ irregular")
    }

    @Test
    fun `recovers bieup irregular candidate from awa connective`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("도와서").parseFor("돕다")

        assertTrue("돕다" in parse.lemmaCandidates)
        assertEquals(listOf("돕다", "-아/어서"), parse.displayParts())
        assertEquals(listOf("ㅂ irregular"), parse.alternations.map { it.name })
    }

    @Test
    fun `marks siot irregular vowel ending candidates`() {
        val parses = KoreanMorphemeChainAnalyzer.analyze("나아서")

        assertTrue(parses.any { "나다" in it.lemmaCandidates && it.alternations.isEmpty() })
        assertAlternation(parses, "낫다", "ㅅ irregular")
    }

    @Test
    fun `marks leu irregular connective candidates`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("달라서").parseFor("다르다")

        assertEquals(listOf("다르다", "-아/어서"), parse.displayParts())
        assertEquals(listOf("르 irregular"), parse.alternations.map { it.name })
    }

    @Test
    fun `marks eu irregular connective candidates`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("써서").parseFor("쓰다")

        assertEquals(listOf("쓰다", "-아/어서"), parse.displayParts())
        assertEquals(listOf("으 irregular"), parse.alternations.map { it.name })
    }

    @Test
    fun `marks hieut irregular connective candidates`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("파래서").parseFor("파랗다")

        assertEquals(listOf("파랗다", "-아/어서"), parse.displayParts())
        assertEquals(listOf("ㅎ irregular"), parse.alternations.map { it.name })
    }

    @Test
    fun `marks rieul drop before bieup final ending`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("압니다").parseFor("알다")

        assertEquals(listOf("알다", "-(스)ㅂ니다"), parse.displayParts())
        assertEquals(listOf("ㄹ drop"), parse.alternations.map { it.name })
    }

    @Test
    fun `dictionary lexicon mode keeps every matching entry for a lemma`() {
        val lexicon = KoreanMorphemeLexicon { lemma ->
            when (lemma) {
                "돕다" -> listOf(
                    KoreanMorphemeLexiconEntry(
                        lemma = lemma,
                        partsOfSpeech = setOf("VV"),
                        inflectionClasses = setOf("bieup_irregular"),
                        dictionaryId = "main",
                        entryId = "help",
                        score = 20,
                    ),
                    KoreanMorphemeLexiconEntry(
                        lemma = lemma,
                        partsOfSpeech = setOf("VA"),
                        inflectionClasses = setOf("bieup_irregular"),
                        dictionaryId = "grammar",
                        entryId = "supportive",
                        score = 10,
                    ),
                )
                else -> emptyList()
            }
        }

        val parse = KoreanMorphemeChainAnalyzer.analyze("도와서", lexicon = lexicon).parseFor("돕다")

        assertEquals(listOf("돕다", "-아/어서"), parse.displayParts())
        assertEquals(listOf("help", "supportive"), parse.dictionaryMatches.map { it.entryId })
    }

    @Test
    fun `dictionary lexicon mode filters guesses without dictionary entries`() {
        val lexicon = KoreanMorphemeLexicon { lemma ->
            when (lemma) {
                "들다" -> listOf(KoreanMorphemeLexiconEntry(lemma = lemma, partsOfSpeech = setOf("VV")))
                "듣다" -> listOf(
                    KoreanMorphemeLexiconEntry(
                        lemma = lemma,
                        partsOfSpeech = setOf("VV"),
                        inflectionClasses = setOf("digeut_irregular"),
                    ),
                )
                else -> emptyList()
            }
        }

        val parses = KoreanMorphemeChainAnalyzer.analyze("들어서", lexicon = lexicon)

        assertEquals(setOf("들다", "듣다"), parses.flatMap { it.lemmaCandidates }.toSet())
        assertAlternation(parses, "듣다", "ㄷ irregular")
    }

    private fun List<KoreanMorphemeParse>.parseFor(lemma: String): KoreanMorphemeParse {
        return first { lemma in it.lemmaCandidates }
    }

    private fun assertAlternation(
        parses: List<KoreanMorphemeParse>,
        lemma: String,
        alternation: String,
    ) {
        assertTrue(
            parses.any { lemma in it.lemmaCandidates && alternation in it.alternations.map { item -> item.name } },
            "Expected $lemma with $alternation in ${parses.map { it.lemmaCandidates to it.alternations.map { item -> item.name } }}",
        )
    }

    private fun KoreanMorphemeParse.displayParts(): List<String> {
        return segments.map { it.name ?: it.surface }
    }
}
