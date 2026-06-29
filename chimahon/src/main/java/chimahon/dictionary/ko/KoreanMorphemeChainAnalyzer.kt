package chimahon.dictionary.ko

/**
 * Experimental Korean analyzer that lives beside the current Yomitan-style
 * deinflector. It does not perform dictionary validation or affect lookup.
 */
object KoreanMorphemeChainAnalyzer {

    fun analyze(
        text: String,
        maxResults: Int = 20,
        lexicon: KoreanMorphemeLexicon? = null,
    ): List<KoreanMorphemeParse> {
        val surface = extractKoreanSpan(text).ifBlank { text.trim() }
        if (surface.isBlank()) return emptyList()

        val normalized = KoreanTextProcessors.disassemble(surface)
        val results = mutableListOf<KoreanMorphemeParse>()

        for (particleState in stripParticles(normalized)) {
            results += parsePredicate(
                surfaceJamo = normalized,
                remaining = particleState.remaining,
                suffixSegments = particleState.segmentsReversed,
                lexicon = lexicon,
            )
        }

        if (results.isEmpty()) {
            results += KoreanMorphemeParse(
                surface = surface,
                normalized = normalized,
                segments = listOf(
                    KoreanMorphemeSegment(
                        surface = surface,
                        normalized = normalized,
                        tag = KoreanMorphemeTag.Unknown,
                        lemmaCandidates = listOf(surface),
                    ),
                ),
                lemmaCandidates = listOf(surface),
                score = 0,
            )
        }

        return results
            .distinctBy { parseKey(it) }
            .sortedWith(
                compareByDescending<KoreanMorphemeParse> { it.coveredLength }
                    .thenByDescending { it.score }
                    .thenBy { it.segments.size },
            )
            .take(maxResults)
    }

    private fun extractKoreanSpan(text: String): String {
        return text.trim().takeWhile { ch ->
            ch.isLetterOrDigit() ||
                KoreanTextProcessors.isHangulSyllable(ch) ||
                KoreanTextProcessors.isJamo(ch)
        }
    }

    private fun stripParticles(text: String): List<ParseState> {
        val out = mutableListOf(ParseState(text, emptyList(), 0))

        fun recurse(remaining: String, segments: List<KoreanMorphemeSegment>, depth: Int, score: Int) {
            if (depth >= MAX_TRAILING_PARTICLES) return
            for (rule in particleRules) {
                if (!remaining.endsWith(rule.surfaceJamo)) continue
                val nextRemaining = remaining.dropLast(rule.surfaceJamo.length)
                if (nextRemaining.isBlank()) continue

                val nextSegments = segments + rule.toSegment()
                out += ParseState(nextRemaining, nextSegments, score + rule.score)
                recurse(nextRemaining, nextSegments, depth + 1, score + rule.score)
            }
        }

        recurse(text, emptyList(), depth = 0, score = 0)
        return out.distinctBy { it.remaining + "\u0000" + it.segmentsReversed.joinToString("\u0001") { segmentKey(it) } }
    }

    private fun parsePredicate(
        surfaceJamo: String,
        remaining: String,
        suffixSegments: List<KoreanMorphemeSegment>,
        lexicon: KoreanMorphemeLexicon?,
    ): List<KoreanMorphemeParse> {
        val results = mutableListOf<KoreanMorphemeParse>()

        fun emitLexeme(
            stemJamo: String,
            rule: MorphRule?,
            segmentsReversed: List<KoreanMorphemeSegment>,
            scoreBonus: Int,
            forceNoun: Boolean = false,
        ) {
            if (stemJamo.isBlank() && rule?.tag != KoreanMorphemeTag.Copula) return

            val lemmaAnalyses = when {
                forceNoun -> buildNounAnalyses(stemJamo)
                rule != null -> buildLemmaAnalyses(stemJamo, rule)
                else -> buildBareAnalyses(stemJamo)
            }

            if (lemmaAnalyses.isEmpty()) return

            val stemSurface = KoreanTextProcessors.assemble(stemJamo)
            for (analysis in lemmaAnalyses) {
                val dictionaryMatches = lexicon.dictionaryMatchesFor(analysis)
                if (dictionaryMatches != null && dictionaryMatches.isEmpty()) continue

                val lexeme = KoreanMorphemeSegment(
                    surface = stemSurface,
                    normalized = stemJamo,
                    tag = if (forceNoun) KoreanMorphemeTag.NounLike else KoreanMorphemeTag.Stem,
                    name = analysis.displayName,
                    lemmaCandidates = listOf(analysis.lemma),
                    alternations = analysis.alternations,
                    dictionaryMatches = dictionaryMatches.orEmpty(),
                )
                val segments = (listOf(lexeme) + segmentsReversed.asReversed()).filterNot {
                    it.surface.isBlank() && it.tag == KoreanMorphemeTag.Stem
                }
                results += KoreanMorphemeParse(
                    surface = KoreanTextProcessors.assemble(surfaceJamo),
                    normalized = surfaceJamo,
                    segments = segments,
                    lemmaCandidates = listOf(analysis.lemma),
                    score = segments.sumOf { it.score } + scoreBonus + dictionaryMatches.orEmpty().maxOfOrNull { it.score }.orZero(),
                )
            }
        }

        fun emitCopulaOnly(segmentsReversed: List<KoreanMorphemeSegment>, scoreBonus: Int) {
            val segments = segmentsReversed.asReversed()
            results += KoreanMorphemeParse(
                surface = KoreanTextProcessors.assemble(surfaceJamo),
                normalized = surfaceJamo,
                segments = segments,
                lemmaCandidates = listOf("이다"),
                score = segments.sumOf { it.score } + scoreBonus,
            )
        }

        fun parseBeforeFinite(
            beforeFinite: String,
            finiteSegments: List<KoreanMorphemeSegment>,
            finiteRule: MorphRule,
        ) {
            emitLexeme(beforeFinite, finiteRule, finiteSegments, scoreBonus = finiteRule.score)

            for (prefinal in prefinalRules) {
                if (!beforeFinite.endsWith(prefinal.surfaceJamo)) continue
                val beforePrefinal = beforeFinite.dropLast(prefinal.surfaceJamo.length)
                emitLexeme(beforePrefinal, finiteRule, finiteSegments + prefinal.toSegment(), scoreBonus = finiteRule.score + prefinal.score)
            }

            for (copula in copulaRules) {
                if (!beforeFinite.endsWith(copula.surfaceJamo)) continue
                val beforeCopula = beforeFinite.dropLast(copula.surfaceJamo.length)
                val copulaSegments = finiteSegments + copula.toSegment()

                if (beforeCopula.isBlank()) {
                    emitCopulaOnly(copulaSegments, scoreBonus = finiteRule.score + copula.score)
                } else {
                    emitLexeme(beforeCopula, null, copulaSegments, scoreBonus = finiteRule.score + copula.score, forceNoun = true)
                }

                for (connective in connectiveRules) {
                    if (!beforeCopula.endsWith(connective.surfaceJamo)) continue
                    val beforeConnective = beforeCopula.dropLast(connective.surfaceJamo.length)
                    emitLexeme(
                        stemJamo = beforeConnective,
                        rule = connective,
                        segmentsReversed = copulaSegments + connective.toSegment(),
                        scoreBonus = finiteRule.score + copula.score + connective.score,
                    )
                }
            }
        }

        for (rule in finiteRules) {
            if (!remaining.endsWith(rule.surfaceJamo)) continue
            val beforeFinite = remaining.dropLast(rule.surfaceJamo.length)
            parseBeforeFinite(beforeFinite, suffixSegments + rule.toSegment(), rule)
        }

        for (rule in connectiveRules + adnominalRules) {
            if (!remaining.endsWith(rule.surfaceJamo)) continue
            val beforeRule = remaining.dropLast(rule.surfaceJamo.length)
            emitLexeme(beforeRule, rule, suffixSegments + rule.toSegment(), scoreBonus = rule.score)
        }

        for (copula in copulaRules) {
            if (!remaining.endsWith(copula.surfaceJamo)) continue
            val beforeCopula = remaining.dropLast(copula.surfaceJamo.length)
            val copulaSegments = suffixSegments + copula.toSegment()
            if (beforeCopula.isBlank()) {
                emitCopulaOnly(copulaSegments, scoreBonus = copula.score)
            } else {
                emitLexeme(beforeCopula, null, copulaSegments, scoreBonus = copula.score, forceNoun = true)
            }
        }

        if (suffixSegments.isNotEmpty()) {
            emitLexeme(remaining, null, suffixSegments, scoreBonus = 0, forceNoun = true)
        }

        return results
    }

    private fun buildNounAnalyses(stemJamo: String): List<LemmaAnalysis> {
        return listOf(KoreanTextProcessors.assemble(stemJamo))
            .filter { it.isNotBlank() }
            .map { LemmaAnalysis(lemma = it) }
    }

    private fun buildBareAnalyses(stemJamo: String): List<LemmaAnalysis> {
        val surface = KoreanTextProcessors.assemble(stemJamo)
        val verb = KoreanTextProcessors.assemble(stemJamo + "ㄷㅏ")
        return listOf(surface, verb)
            .filter { it.isNotBlank() }
            .distinct()
            .map { LemmaAnalysis(lemma = it) }
    }

    private fun buildLemmaAnalyses(stemJamo: String, rule: MorphRule): List<LemmaAnalysis> {
        val analyses = mutableListOf<LemmaAnalysis>()
        for (baseSuffix in rule.baseSuffixes) {
            val lemma = KoreanTextProcessors.assemble(stemJamo + baseSuffix.jamo)
            val alternations = baseSuffix.alternation?.let { alternation ->
                listOf(
                    alternation.toResult(
                        lexical = lemma,
                        surface = KoreanTextProcessors.assemble(stemJamo + rule.surfaceJamo),
                    ),
                )
            }.orEmpty()
            analyses += LemmaAnalysis(
                lemma = lemma,
                displayName = alternations.firstOrNull()?.lexical,
                alternations = alternations,
            )
        }

        if (rule.shouldGuessVowelAlternations()) {
            analyses += vowelIrregularAnalyses(stemJamo, rule)
        }

        return analyses
            .filter { it.lemma.isNotBlank() }
            .distinctBy { it.lemma + "\u0000" + it.alternations.joinToString("|") { alternation -> alternation.id } }
    }

    private fun KoreanMorphemeLexicon?.dictionaryMatchesFor(
        analysis: LemmaAnalysis,
    ): List<KoreanMorphemeLexiconEntry>? {
        if (this == null) return null

        return lookup(analysis.lemma)
            .filter { entry -> entry.supports(analysis.alternations) }
    }

    private fun KoreanMorphemeLexiconEntry.supports(
        alternations: List<KoreanMorphemeAlternation>,
    ): Boolean {
        if (alternations.isEmpty() || inflectionClasses.isEmpty()) return true

        return alternations.all { alternation ->
            alternation.id in inflectionClasses || alternation.name in inflectionClasses
        }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun vowelIrregularAnalyses(stemJamo: String, rule: MorphRule): List<LemmaAnalysis> {
        val out = mutableListOf<LemmaAnalysis>()

        if (stemJamo.endsWith("ㄹ")) {
            out += irregularAnalysis(
                lemma = KoreanTextProcessors.assemble(stemJamo.dropLast(1) + "ㄷㄷㅏ"),
                stemJamo = stemJamo,
                rule = rule,
                alternation = DIGEUT_IRREGULAR_VOWEL,
            )
        }

        if (!stemJamo.endsWith("ㅅ")) {
            out += irregularAnalysis(
                lemma = KoreanTextProcessors.assemble(stemJamo + "ㅅㄷㅏ"),
                stemJamo = stemJamo,
                rule = rule,
                alternation = SIOT_IRREGULAR_VOWEL,
            )
        }

        return out.toList()
    }

    private fun irregularAnalysis(
        lemma: String,
        stemJamo: String,
        rule: MorphRule,
        alternation: StemAlternationTemplate,
    ): LemmaAnalysis {
        val result = alternation.toResult(
            lexical = lemma,
            surface = KoreanTextProcessors.assemble(stemJamo + rule.surfaceJamo),
        )
        return LemmaAnalysis(
            lemma = lemma,
            displayName = lemma,
            alternations = listOf(result),
        )
    }

    private fun MorphRule.shouldGuessVowelAlternations(): Boolean {
        if (!vowelInitial) return false
        val specializedAlternations = setOf(
            BIEUP_IRREGULAR_VOWEL,
            LEU_IRREGULAR_VOWEL,
            EU_IRREGULAR_VOWEL,
            HIEUT_IRREGULAR_VOWEL,
        )
        return baseSuffixes.none { it.alternation in specializedAlternations }
    }

    private data class LemmaAnalysis(
        val lemma: String,
        val displayName: String? = null,
        val alternations: List<KoreanMorphemeAlternation> = emptyList(),
    )

    private data class StemBaseTemplate(
        val jamo: String,
        val alternation: StemAlternationTemplate? = null,
    )

    private fun base(
        suffixJamo: String,
        alternation: StemAlternationTemplate? = null,
    ): StemBaseTemplate {
        return StemBaseTemplate(suffixJamo, alternation)
    }

    private fun parseKey(parse: KoreanMorphemeParse): String {
        return parse.lemmaCandidates.joinToString("|") + "\u0000" +
            parse.segments.joinToString("\u0001") { segmentKey(it) }
    }

    private fun segmentKey(segment: KoreanMorphemeSegment): String {
        return "${segment.tag}:${segment.surface}:${segment.grammarId.orEmpty()}:${segment.lemmaCandidates.joinToString("|")}:${segment.alternations.joinToString("|") { it.id }}"
    }

    private fun MorphRule.toSegment(): KoreanMorphemeSegment {
        return KoreanMorphemeSegment(
            surface = displaySurface ?: KoreanTextProcessors.assemble(surfaceJamo),
            normalized = surfaceJamo,
            tag = tag,
            grammarId = grammarId,
            name = name,
            score = score,
        )
    }

    private const val MAX_TRAILING_PARTICLES = 3

    private val BIEUP_IRREGULAR_VOWEL = StemAlternationTemplate(
        id = "bieup_irregular",
        name = "ㅂ irregular",
        description = "A ㅂ-final stem is realized as 오/우 before a vowel-initial ending.",
    )
    private val DIGEUT_IRREGULAR_VOWEL = StemAlternationTemplate(
        id = "digeut_irregular",
        name = "ㄷ irregular",
        description = "A ㄷ-final stem is realized as ㄹ before a vowel-initial ending.",
    )
    private val SIOT_IRREGULAR_VOWEL = StemAlternationTemplate(
        id = "siot_irregular",
        name = "ㅅ irregular",
        description = "A ㅅ-final stem drops ㅅ before a vowel-initial ending.",
    )
    private val LEU_IRREGULAR_VOWEL = StemAlternationTemplate(
        id = "leu_irregular",
        name = "르 irregular",
        description = "A 르-final stem drops ㅡ and adds ㄹ before 아/어.",
    )
    private val EU_IRREGULAR_VOWEL = StemAlternationTemplate(
        id = "eu_irregular",
        name = "으 irregular",
        description = "A ㅡ-final stem drops ㅡ before 아/어.",
    )
    private val HIEUT_IRREGULAR_VOWEL = StemAlternationTemplate(
        id = "hieut_irregular",
        name = "ㅎ irregular",
        description = "A ㅎ-final adjective stem drops ㅎ and contracts with 아/어.",
    )
    private val RIEUL_DROP = StemAlternationTemplate(
        id = "rieul_drop",
        name = "ㄹ drop",
        description = "A ㄹ-final stem drops ㄹ before ㄴ, ㅂ, or ㅅ.",
    )

    private val particleRules = listOf(
        rule("ㅁㅏㄴ", KoreanMorphemeTag.Particle, "noun_만", "만", score = 35),
        rule("ㅇㅡㄴ", KoreanMorphemeTag.Particle, "noun_은_는", "은/는", score = 20),
        rule("ㄴㅡㄴ", KoreanMorphemeTag.Particle, "noun_은_는", "은/는", score = 20),
        rule("ㅇㅣ", KoreanMorphemeTag.Particle, "noun_이_가", "이/가", score = 15),
        rule("ㄱㅏ", KoreanMorphemeTag.Particle, "noun_이_가", "이/가", score = 15),
        rule("ㅇㅡㄹ", KoreanMorphemeTag.Particle, "noun_을_를", "을/를", score = 15),
        rule("ㄹㅡㄹ", KoreanMorphemeTag.Particle, "noun_을_를", "을/를", score = 15),
        rule("ㄷㅗ", KoreanMorphemeTag.Particle, "noun_도", "도", score = 15),
        rule("ㅇㅔㅅㅓ", KoreanMorphemeTag.Particle, "noun_에서", "에서", score = 15),
        rule("ㅇㅔ", KoreanMorphemeTag.Particle, "noun_에", "에", score = 10),
    ).sortedByDescending { it.surfaceJamo.length }

    private val finiteRules = listOf(
        rule("ㄴㅡㄴㄷㅏㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_는다고_ㄴ다고_다고_라고", "-는다고", "ㄷㅏ", true, 65),
        ruleWithBases("ㄴㄷㅏㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_는다고_ㄴ다고_다고_라고", "-ㄴ다고", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), true, 65),
        rule("ㄷㅏㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_다고", "-다고", "ㄷㅏ", false, 55),
        rule("ㄴㅑㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_냐고", "-냐고", "ㄷㅏ", true, 55),
        rule("ㅈㅏㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_자고", "-자고", "ㄷㅏ", true, 55),
        rule("ㅇㅡㄹㅏㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_으라고_라고", "-으라고", "ㄷㅏ", true, 55),
        rule("ㄹㅏㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_라고", "-라고", "ㄷㅏ", true, 50),
        rule("ㅅㅡㅂㄴㅣㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_습니다_ㅂ니다", "-(스)ㅂ니다", "ㄷㅏ", false, 55),
        ruleWithBases("ㅂㄴㅣㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_습니다_ㅂ니다", "-(스)ㅂ니다", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 55),
        rule("ㄴㅡㄴㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_다_ㄴ다_는다", "-는다", "ㄷㅏ", false, 40),
        ruleWithBases("ㄴㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_다_ㄴ다_는다", "-ㄴ다", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 40),
        rule("ㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_다_ㄴ다_는다", "-다", "ㄷㅏ", false, 20),
    ).sortedByDescending { it.surfaceJamo.length }

    private val copulaRules = listOf(
        rule("ㅇㅣㄷㅏ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 50, displaySurface = "이다"),
        rule("ㅇㅣ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 50, displaySurface = "이다"),
    ).sortedByDescending { it.surfaceJamo.length }

    private val prefinalRules = listOf(
        rule("ㅇㅓㅆ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", score = 30),
        rule("ㅇㅏㅆ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", score = 30),
        rule("ㅎㅐㅆ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-했", score = 30),
        rule("ㄱㅔㅆ", KoreanMorphemeTag.PreFinalEnding, "verb_겠", "-겠", score = 25),
        rule("ㄷㅓ", KoreanMorphemeTag.PreFinalEnding, "verb_더", "-더", score = 20),
        rule("ㅇㅡㅅㅣ", KoreanMorphemeTag.PreFinalEnding, "verb_시_으시", "-(으)시", score = 20),
        rule("ㅅㅣ", KoreanMorphemeTag.PreFinalEnding, "verb_시_으시", "-시", score = 20),
    ).sortedByDescending { it.surfaceJamo.length }

    private val connectiveRules = listOf(
        rule("ㅎㅏㅇㅕㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", "ㅎㅏㄷㅏ", true, 50),
        rule("ㅎㅐㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", "ㅎㅏㄷㅏ", true, 50),
        ruleWithBases("ㅇㅘㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅇㅝㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㄹㄹㅏㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄹㅡㄷㅏ", LEU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㄹㄹㅓㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄹㅡㄷㅏ", LEU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅇㅏㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅇㅓㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅏㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄷㅏ"), base("ㅏㄷㅏ"), base("ㅡㄷㅏ", EU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅓㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄷㅏ"), base("ㅓㄷㅏ"), base("ㅡㄷㅏ", EU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅐㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅣㄷㅏ"), base("ㅏㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL), base("ㅓㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅔㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅓㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL)), true, 45),
        rule("ㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", "ㄷㅏ", true, 40),
        rule("ㅈㅣㅁㅏㄴ", KoreanMorphemeTag.ConnectiveEnding, "verb_지만", "-지만", "ㄷㅏ", false, 35),
        rule("ㄱㅗ", KoreanMorphemeTag.ConnectiveEnding, "verb_고", "-고", "ㄷㅏ", false, 30),
    ).sortedByDescending { it.surfaceJamo.length }

    private val adnominalRules = listOf(
        ruleWithBases("ㄴㅡㄴ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-는", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 25),
        rule("ㅇㅡㄴ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-은", "ㄷㅏ", false, 25),
        ruleWithBases("ㄴ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-ㄴ", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 25),
        rule("ㅇㅡㄹ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-을", "ㄷㅏ", false, 25),
        rule("ㄹ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-ㄹ", listOf("ㄷㅏ", "ㄹㄷㅏ"), false, 25),
    ).sortedByDescending { it.surfaceJamo.length }

    private fun rule(
        surfaceJamo: String,
        tag: KoreanMorphemeTag,
        grammarId: String,
        name: String,
        baseSuffixJamo: String = "",
        vowelInitial: Boolean = false,
        score: Int = 10,
        displaySurface: String? = null,
    ): MorphRule {
        return MorphRule(surfaceJamo, tag, grammarId, name, listOf(base(baseSuffixJamo)), vowelInitial, score, displaySurface)
    }

    private fun rule(
        surfaceJamo: String,
        tag: KoreanMorphemeTag,
        grammarId: String,
        name: String,
        baseSuffixJamo: List<String>,
        vowelInitial: Boolean,
        score: Int,
        displaySurface: String? = null,
    ): MorphRule {
        return MorphRule(surfaceJamo, tag, grammarId, name, baseSuffixJamo.map { base(it) }, vowelInitial, score, displaySurface)
    }

    private fun ruleWithBases(
        surfaceJamo: String,
        tag: KoreanMorphemeTag,
        grammarId: String,
        name: String,
        baseSuffixes: List<StemBaseTemplate>,
        vowelInitial: Boolean,
        score: Int,
        displaySurface: String? = null,
    ): MorphRule {
        return MorphRule(surfaceJamo, tag, grammarId, name, baseSuffixes, vowelInitial, score, displaySurface)
    }

    private data class MorphRule(
        val surfaceJamo: String,
        val tag: KoreanMorphemeTag,
        val grammarId: String,
        val name: String,
        val baseSuffixes: List<StemBaseTemplate>,
        val vowelInitial: Boolean,
        val score: Int,
        val displaySurface: String?,
    )

    private data class StemAlternationTemplate(
        val id: String,
        val name: String,
        val description: String,
    ) {
        fun toResult(lexical: String, surface: String): KoreanMorphemeAlternation {
            return KoreanMorphemeAlternation(
                id = id,
                name = name,
                description = description,
                lexical = lexical,
                surface = surface,
            )
        }
    }

    private data class ParseState(
        val remaining: String,
        val segmentsReversed: List<KoreanMorphemeSegment>,
        val score: Int,
    )
}

data class KoreanMorphemeParse(
    val surface: String,
    val normalized: String,
    val segments: List<KoreanMorphemeSegment>,
    val lemmaCandidates: List<String>,
    val score: Int,
) {
    val coveredLength: Int = surface.length
    val alternations: List<KoreanMorphemeAlternation> = segments.flatMap { it.alternations }
    val dictionaryMatches: List<KoreanMorphemeLexiconEntry> = segments.flatMap { it.dictionaryMatches }
}

fun interface KoreanMorphemeLexicon {
    fun lookup(lemma: String): List<KoreanMorphemeLexiconEntry>
}

data class KoreanMorphemeLexiconEntry(
    val lemma: String,
    val partsOfSpeech: Set<String> = emptySet(),
    val inflectionClasses: Set<String> = emptySet(),
    val dictionaryId: String? = null,
    val entryId: String? = null,
    val score: Int = 0,
)

data class KoreanMorphemeAlternation(
    val id: String,
    val name: String,
    val description: String,
    val lexical: String,
    val surface: String,
)

data class KoreanMorphemeSegment(
    val surface: String,
    val normalized: String,
    val tag: KoreanMorphemeTag,
    val grammarId: String? = null,
    val name: String? = null,
    val lemmaCandidates: List<String> = emptyList(),
    val alternations: List<KoreanMorphemeAlternation> = emptyList(),
    val dictionaryMatches: List<KoreanMorphemeLexiconEntry> = emptyList(),
    val score: Int = 0,
)

enum class KoreanMorphemeTag {
    Stem,
    NounLike,
    Copula,
    PreFinalEnding,
    ConnectiveEnding,
    FinalEnding,
    QuotationEnding,
    AdnominalEnding,
    Particle,
    Unknown,
}
