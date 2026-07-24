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

    @Test
    fun `dictionary lexicon mode accepts adjective predicates`() {
        val lexicon = KoreanMorphemeLexicon { lemma ->
            when (lemma) {
                "고맙다" -> listOf(
                    KoreanMorphemeLexiconEntry(
                        lemma = lemma,
                        partsOfSpeech = setOf("adj"),
                        inflectionClasses = setOf("bieup_irregular"),
                    ),
                )
                else -> emptyList()
            }
        }

        val parse = KoreanMorphemeChainAnalyzer.analyze("고마워", lexicon = lexicon).parseForExact("고맙다")

        assertEquals(listOf("고맙다", "-아/어"), parse.displayParts())
    }

    @Test
    fun `dictionary lexicon mode filters predicate parses with noun only entries`() {
        val lexicon = KoreanMorphemeLexicon { lemma ->
            when (lemma) {
                "돕다" -> listOf(
                    KoreanMorphemeLexiconEntry(
                        lemma = lemma,
                        partsOfSpeech = setOf("n"),
                        inflectionClasses = setOf("bieup_irregular"),
                    ),
                )
                else -> emptyList()
            }
        }

        val parses = KoreanMorphemeChainAnalyzer.analyze("도와서", lexicon = lexicon)

        assertTrue(parses.none { "돕다" in it.lemmaCandidates })
    }

    @Test
    fun `recovers lexicalized compound and auxiliary chain from contracted past`() {
        val parses = KoreanMorphemeChainAnalyzer.analyze("찾아왔다", lexicon = lexiconOf("찾아오다", "찾다", "오다"))

        assertEquals(
            listOf("찾아오다", "-았/었", "-다"),
            parses.parseForExact("찾아오다").displayParts(),
        )
        assertEquals(
            listOf("찾", "-아/어", "오다", "-았/었", "-다"),
            parses.parseForAll("찾다", "오다").displayParts(),
        )
    }

    @Test
    fun `recovers lexicalized compound and auxiliary chain before finite ending`() {
        val parses = KoreanMorphemeChainAnalyzer.analyze("찾아온다", lexicon = lexiconOf("찾아오다", "찾다", "오다"))

        assertEquals(
            listOf("찾아오", "-ㄴ다"),
            parses.parseForExact("찾아오다").displayParts(),
        )
        assertEquals(
            listOf("찾", "-아/어", "오다", "-ㄴ다"),
            parses.parseForAll("찾다", "오다").displayParts(),
        )
    }

    @Test
    fun `recovers lexicalized compound and auxiliary chain from contracted informal ending`() {
        val parses = KoreanMorphemeChainAnalyzer.analyze("찾아봐", lexicon = lexiconOf("찾아보다", "찾다", "보다"))

        assertEquals(
            listOf("찾아보다", "-아/어"),
            parses.parseForExact("찾아보다").displayParts(),
        )
        assertEquals(
            listOf("찾", "-아/어", "보다", "-아/어"),
            parses.parseForAll("찾다", "보다").displayParts(),
        )
    }

    @Test
    fun `recovers contracted regular vowel past lemma`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("갖췄다", lexicon = lexiconOf("갖추다")).parseForExact("갖추다")

        assertEquals(listOf("갖추다", "-았/었", "-다"), parse.displayParts())
        assertEquals(listOf("vowel contraction"), parse.alternations.map { it.name })
    }

    @Test
    fun `recovers go auxiliary chains`() {
        val progressive = KoreanMorphemeChainAnalyzer.analyze("읽고있다", lexicon = lexiconOf("읽다", "있다"))
            .parseForAll("읽다", "있다")
        val desiderative = KoreanMorphemeChainAnalyzer.analyze("먹고싶다", lexicon = lexiconOf("먹다", "싶다"))
            .parseForAll("먹다", "싶다")

        assertEquals(listOf("읽", "-고", "있다", "-다"), progressive.displayParts())
        assertEquals(listOf("먹", "-고", "싶다", "-다"), desiderative.displayParts())
    }

    @Test
    fun `recovers ji auxiliary chain`() {
        val parse = KoreanMorphemeChainAnalyzer.analyze("읽지않다", lexicon = lexiconOf("읽다", "않다"))
            .parseForAll("읽다", "않다")

        assertEquals(listOf("읽", "-지", "않다", "-다"), parse.displayParts())
    }

    @Test
    fun `recovers mined KoParadigm connective endings`() {
        val cases = listOf(
            Triple("찾거나", "찾다", listOf("찾", "-거나")),
            Triple("찾거든", "찾다", listOf("찾", "-거든")),
            Triple("찾기에", "찾다", listOf("찾", "-기에")),
            Triple("찾다가", "찾다", listOf("찾", "-다가")),
            Triple("찾도록", "찾다", listOf("찾", "-도록")),
            Triple("찾게", "찾다", listOf("찾", "-게")),
            Triple("읽으면", "읽다", listOf("읽", "-으면")),
            Triple("가면", "가다", listOf("가", "-면")),
            Triple("읽으니까", "읽다", listOf("읽", "-으니까")),
            Triple("가니까", "가다", listOf("가", "-니까")),
            Triple("아니까", "알다", listOf("알다", "-니까")),
            Triple("읽으려고", "읽다", listOf("읽", "-으려고")),
            Triple("가려고", "가다", listOf("가", "-려고")),
            Triple("먹는데", "먹다", listOf("먹", "-는데")),
            Triple("좋은데", "좋다", listOf("좋", "-은데")),
        )

        for ((surface, lemma, parts) in cases) {
            val parses = KoreanMorphemeChainAnalyzer.analyze(surface, lexicon = lexiconOf(lemma))
            assertTrue(
                parses.any { it.lemmaCandidates == listOf(lemma) && it.displayParts() == parts },
                "$surface expected $parts in ${parses.map { it.lemmaCandidates to it.displayParts() }}",
            )
        }
    }

    @Test
    fun `recovers common contracted vowel past lemmas`() {
        val cases = mapOf(
            "갔다" to "가다",
            "섰다" to "서다",
            "냈다" to "내다",
            "됐다" to "되다",
            "켰다" to "켜다",
            "했다" to "하다",
        )

        for ((surface, lemma) in cases) {
            val parse = KoreanMorphemeChainAnalyzer.analyze(surface, lexicon = lexiconOf(lemma)).parseForExact(lemma)
            assertEquals(listOf(lemma, "-았/었", "-다"), parse.displayParts(), surface)
        }
    }

    @Test
    fun `recovers common contracted informal lemmas`() {
        val cases = mapOf(
            "가" to "가다",
            "서" to "서다",
            "내" to "내다",
            "돼" to "되다",
            "켜" to "켜다",
            "해" to "하다",
        )

        for ((surface, lemma) in cases) {
            val parse = KoreanMorphemeChainAnalyzer.analyze(surface, lexicon = lexiconOf(lemma)).parseForExact(lemma)
            assertEquals(listOf(lemma, "-아/어"), parse.displayParts(), surface)
        }
    }

    @Test
    fun `recovers irregular contracted informal and past lemmas`() {
        val cases = mapOf(
            "고마워" to "고맙다",
            "고마웠다" to "고맙다",
            "불러" to "부르다",
            "불렀다" to "부르다",
            "들어" to "듣다",
            "나아" to "낫다",
        )

        for ((surface, lemma) in cases) {
            val parse = KoreanMorphemeChainAnalyzer.analyze(surface, lexicon = lexiconOf(lemma)).parseForExact(lemma)
            assertTrue(parse.displayParts().first() == lemma, surface)
            assertTrue(parse.alternations.isNotEmpty(), surface)
        }
    }

    @Test
    fun `recovers copula contractions after nouns`() {
        val cases = mapOf(
            "사과야" to "사과",
            "선생님이야" to "선생님",
            "사과예요" to "사과",
            "사과에요" to "사과",
            "선생님이에요" to "선생님",
            "선생님이예요" to "선생님",
            "사과라도" to "사과",
        )

        for ((surface, noun) in cases) {
            val parse = KoreanMorphemeChainAnalyzer.analyze(surface, lexicon = nominalLexiconOf(noun)).parseForExact(noun)
            assertEquals(noun, parse.displayParts().first(), surface)
            assertTrue(parse.segments.any { it.tag == KoreanMorphemeTag.Copula }, surface)
        }
    }

    @Test
    fun `recovers anida contracted copula-like endings`() {
        val cases = mapOf(
            "아니야" to "아니다",
            "아니에요" to "아니다",
            "아니예요" to "아니다",
            "아녜요" to "아니다",
            "아녀요" to "아니다",
            "아니어서" to "아니다",
            "아니었다" to "아니다",
            "아니였다" to "아니다",
            "아니라도" to "아니다",
            "아니라서" to "아니다",
        )

        for ((surface, lemma) in cases) {
            val parse = KoreanMorphemeChainAnalyzer.analyze(surface, lexicon = lexiconOf(lemma)).parseForExact(lemma)
            assertTrue(parse.displayParts().isNotEmpty(), surface)
        }
    }

    @Test
    fun `recovers conjugated change in state ending`() {
        val parses = KoreanMorphemeChainAnalyzer.analyze(
            "예뻐지셨습니다",
            lexicon = lexiconOf("예쁘다", "지다"),
        )

        assertEquals(
            listOf("예쁘다", "-아/어", "지다", "-시", "-았/었", "-(스)ㅂ니다"),
            parses.parseForAll("예쁘다", "지다").displayParts(),
        )
    }

    private fun lexiconOf(vararg lemmas: String): KoreanMorphemeLexicon {
        val entries = lemmas.associateWith { lemma ->
            listOf(KoreanMorphemeLexiconEntry(lemma = lemma, partsOfSpeech = setOf("VV"), score = 10))
        }
        return KoreanMorphemeLexicon { lemma -> entries[lemma].orEmpty() }
    }

    private fun nominalLexiconOf(vararg lemmas: String): KoreanMorphemeLexicon {
        val entries = lemmas.associateWith { lemma ->
            listOf(KoreanMorphemeLexiconEntry(lemma = lemma, partsOfSpeech = setOf("n"), score = 10))
        }
        return KoreanMorphemeLexicon { lemma -> entries[lemma].orEmpty() }
    }

    private fun List<KoreanMorphemeParse>.parseFor(lemma: String): KoreanMorphemeParse {
        return first { lemma in it.lemmaCandidates }
    }

    private fun List<KoreanMorphemeParse>.parseForExact(lemma: String): KoreanMorphemeParse {
        val seen = joinToString { it.lemmaCandidates.joinToString("+") }
        return firstOrNull { it.lemmaCandidates == listOf(lemma) }
            ?: error("Missing exact lemma $lemma. Saw: $seen")
    }

    private fun List<KoreanMorphemeParse>.parseForAll(vararg lemmas: String): KoreanMorphemeParse {
        val expected = lemmas.toSet()
        return first { it.lemmaCandidates.toSet() == expected }
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
