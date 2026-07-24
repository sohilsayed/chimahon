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

        fun emitLemmaAnalysis(
            stemJamo: String,
            analysis: LemmaAnalysis,
            segmentsReversed: List<KoreanMorphemeSegment>,
            scoreBonus: Int,
            tag: KoreanMorphemeTag = KoreanMorphemeTag.Stem,
            lexicalKind: LexicalKind = LexicalKind.Predicate,
        ) {
            if (!analysis.canUseStemSurface(stemJamo)) return

            val dictionaryMatches = lexicon.dictionaryMatchesFor(analysis, lexicalKind)
            if (dictionaryMatches != null && dictionaryMatches.isEmpty()) return

            val lexeme = KoreanMorphemeSegment(
                surface = KoreanTextProcessors.assemble(stemJamo),
                normalized = stemJamo,
                tag = tag,
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

            for (analysis in lemmaAnalyses) {
                emitLemmaAnalysis(
                    stemJamo = stemJamo,
                    analysis = analysis,
                    segmentsReversed = segmentsReversed,
                    scoreBonus = scoreBonus,
                    tag = if (forceNoun) KoreanMorphemeTag.NounLike else KoreanMorphemeTag.Stem,
                    lexicalKind = when {
                        forceNoun -> LexicalKind.Nominal
                        rule != null -> LexicalKind.Predicate
                        else -> LexicalKind.Any
                    },
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

        fun emitAuxiliaryChains(
            restoredStemJamo: String,
            segmentsReversed: List<KoreanMorphemeSegment>,
            scoreBonus: Int,
        ) {
            for (auxiliary in auxiliaryVerbRules) {
                if (!restoredStemJamo.endsWith(auxiliary.stemJamo)) continue
                val mainWithConnective = restoredStemJamo.dropLast(auxiliary.stemJamo.length)
                if (mainWithConnective.isBlank()) continue

                val auxiliaryAnalysis = LemmaAnalysis(
                    lemma = auxiliary.lemma,
                    displayName = auxiliary.lemma,
                )
                val auxiliaryMatches = lexicon.dictionaryMatchesFor(auxiliaryAnalysis, LexicalKind.Predicate)
                if (auxiliaryMatches != null && auxiliaryMatches.isEmpty()) continue

                for (connective in auxiliaryConnectiveRules) {
                    if (connective.grammarId !in auxiliary.connectiveGrammarIds) continue
                    if (!mainWithConnective.endsWith(connective.surfaceJamo)) continue
                    val mainStemJamo = mainWithConnective.dropLast(connective.surfaceJamo.length)
                    if (mainStemJamo.isBlank()) continue

                    for (mainAnalysis in buildLemmaAnalyses(mainStemJamo, connective)) {
                        if (!mainAnalysis.canUseStemSurface(mainStemJamo)) continue

                        val mainMatches = lexicon.dictionaryMatchesFor(mainAnalysis, LexicalKind.Predicate)
                        if (mainMatches != null && mainMatches.isEmpty()) continue

                        val mainSegment = KoreanMorphemeSegment(
                            surface = KoreanTextProcessors.assemble(mainStemJamo),
                            normalized = mainStemJamo,
                            tag = KoreanMorphemeTag.Stem,
                            name = mainAnalysis.displayName,
                            lemmaCandidates = listOf(mainAnalysis.lemma),
                            alternations = mainAnalysis.alternations,
                            dictionaryMatches = mainMatches.orEmpty(),
                        )
                        val auxiliarySegment = KoreanMorphemeSegment(
                            surface = KoreanTextProcessors.assemble(auxiliary.stemJamo),
                            normalized = auxiliary.stemJamo,
                            tag = KoreanMorphemeTag.Auxiliary,
                            grammarId = auxiliary.grammarId,
                            name = auxiliary.lemma,
                            lemmaCandidates = listOf(auxiliary.lemma),
                            dictionaryMatches = auxiliaryMatches.orEmpty(),
                            score = auxiliary.score,
                        )
                        val segments = listOf(mainSegment, connective.toSegment(), auxiliarySegment) + segmentsReversed.asReversed()
                        results += KoreanMorphemeParse(
                            surface = KoreanTextProcessors.assemble(surfaceJamo),
                            normalized = surfaceJamo,
                            segments = segments,
                            lemmaCandidates = listOf(mainAnalysis.lemma, auxiliary.lemma),
                            score = segments.sumOf { it.score } +
                                scoreBonus +
                                mainMatches.orEmpty().maxOfOrNull { it.score }.orZero() +
                                auxiliaryMatches.orEmpty().maxOfOrNull { it.score }.orZero(),
                        )
                    }
                }
            }
        }

        fun emitContractedLemmaAnalyses(
            restoredStemJamo: String,
            surfaceStemJamo: String,
            contraction: ContractedEndingRule,
            segmentsReversed: List<KoreanMorphemeSegment>,
            scoreBonus: Int,
        ) {
            for (analysis in contractedLemmaAnalyses(restoredStemJamo, surfaceStemJamo, contraction)) {
                emitLemmaAnalysis(
                    stemJamo = restoredStemJamo,
                    analysis = analysis,
                    segmentsReversed = segmentsReversed,
                    scoreBonus = scoreBonus,
                )
            }
        }

        fun parseBeforeFinite(
            beforeFinite: String,
            finiteSegments: List<KoreanMorphemeSegment>,
            finiteRule: MorphRule,
        ) {
            emitLexeme(beforeFinite, finiteRule, finiteSegments, scoreBonus = finiteRule.score)
            emitAuxiliaryChains(
                restoredStemJamo = beforeFinite,
                segmentsReversed = finiteSegments,
                scoreBonus = finiteRule.score,
            )

            fun processPrefinals(
                stem: String,
                accumulatedSegments: List<KoreanMorphemeSegment>,
                accumulatedScore: Int,
                depth: Int = 0,
            ) {
                if (depth > 3) return
                emitLexeme(stem, finiteRule, accumulatedSegments, accumulatedScore)
                emitAuxiliaryChains(
                    restoredStemJamo = stem,
                    segmentsReversed = accumulatedSegments,
                    scoreBonus = accumulatedScore,
                )
                for (prefinal in prefinalRules) {
                    if (!stem.endsWith(prefinal.surfaceJamo)) continue
                    val beforePrefinal = stem.dropLast(prefinal.surfaceJamo.length)
                    processPrefinals(
                        stem = beforePrefinal,
                        accumulatedSegments = accumulatedSegments + prefinal.toSegment(),
                        accumulatedScore = accumulatedScore + prefinal.score,
                        depth = depth + 1,
                    )
                }
            }
            processPrefinals(beforeFinite, finiteSegments, finiteRule.score)

            for (contraction in contractedPastRules) {
                if (!beforeFinite.endsWith(contraction.surfaceJamo)) continue
                val restoredStem = beforeFinite.dropLast(contraction.surfaceJamo.length) + contraction.restoredStemSuffixJamo
                val contractedSegments = finiteSegments + contraction.toSegment()
                val scoreBonus = finiteRule.score + contraction.score
                emitContractedLemmaAnalyses(restoredStem, beforeFinite, contraction, contractedSegments, scoreBonus)
                emitAuxiliaryChains(
                    restoredStemJamo = restoredStem,
                    segmentsReversed = contractedSegments,
                    scoreBonus = scoreBonus,
                )
                fun processContractPrefinals(
                    stem: String,
                    accSegments: List<KoreanMorphemeSegment>,
                    accScore: Int,
                    depth: Int = 0,
                ) {
                    if (depth > 3) return
                    emitLexeme(stem, finiteRule, accSegments, accScore)
                    emitAuxiliaryChains(
                        restoredStemJamo = stem,
                        segmentsReversed = accSegments,
                        scoreBonus = accScore,
                    )
                    for (prefinal in prefinalRules) {
                        if (!stem.endsWith(prefinal.surfaceJamo)) continue
                        val beforePrefinal = stem.dropLast(prefinal.surfaceJamo.length)
                        processContractPrefinals(
                            stem = beforePrefinal,
                            accSegments = accSegments + prefinal.toSegment(),
                            accScore = accScore + prefinal.score,
                            depth = depth + 1,
                        )
                    }
                }
                processContractPrefinals(restoredStem, contractedSegments, scoreBonus)
            }

            for (contraction in contractedInformalRules) {
                if (!beforeFinite.endsWith(contraction.surfaceJamo)) continue
                val restoredStem = beforeFinite.dropLast(contraction.surfaceJamo.length) + contraction.restoredStemSuffixJamo
                val contractedSegments = finiteSegments + contraction.toSegment()
                val scoreBonus = finiteRule.score + contraction.score
                emitContractedLemmaAnalyses(restoredStem, beforeFinite, contraction, contractedSegments, scoreBonus)
                emitAuxiliaryChains(
                    restoredStemJamo = restoredStem,
                    segmentsReversed = contractedSegments,
                    scoreBonus = scoreBonus,
                )
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

        for (contraction in contractedPastRules) {
            if (!remaining.endsWith(contraction.surfaceJamo)) continue
            val restoredStem = remaining.dropLast(contraction.surfaceJamo.length) + contraction.restoredStemSuffixJamo
            val contractedSegments = suffixSegments + contraction.toSegment()
            emitContractedLemmaAnalyses(restoredStem, remaining, contraction, contractedSegments, contraction.score)
            emitAuxiliaryChains(
                restoredStemJamo = restoredStem,
                segmentsReversed = contractedSegments,
                scoreBonus = contraction.score,
            )
        }

        for (contraction in contractedInformalRules) {
            if (!remaining.endsWith(contraction.surfaceJamo)) continue
            val restoredStem = remaining.dropLast(contraction.surfaceJamo.length) + contraction.restoredStemSuffixJamo
            val contractedSegments = suffixSegments + contraction.toSegment()
            emitContractedLemmaAnalyses(restoredStem, remaining, contraction, contractedSegments, contraction.score)
            emitAuxiliaryChains(
                restoredStemJamo = restoredStem,
                segmentsReversed = contractedSegments,
                scoreBonus = contraction.score,
            )
        }

        for (rule in auxiliaryConnectiveRules) {
            if (!remaining.endsWith(rule.surfaceJamo)) continue
            val beforeRule = remaining.dropLast(rule.surfaceJamo.length)
            emitLexeme(beforeRule, rule, suffixSegments + rule.toSegment(), scoreBonus = rule.score)
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

    private fun isCompleteStemJamo(stemJamo: String): Boolean {
        val assembled = KoreanTextProcessors.assemble(stemJamo)
        return assembled.none { KoreanTextProcessors.isJamo(it) }
    }

    private fun LemmaAnalysis.canUseStemSurface(stemJamo: String): Boolean {
        if (stemJamo.isBlank() || isCompleteStemJamo(stemJamo)) return true
        return displayName != null || alternations.isNotEmpty()
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
            val lemma = KoreanTextProcessors.assemble(baseSuffix.applyTo(stemJamo))
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
        lexicalKind: LexicalKind,
    ): List<KoreanMorphemeLexiconEntry>? {
        if (this == null) return null

        return lookup(analysis.lemma)
            .filter { entry -> entry.supports(analysis.alternations, lexicalKind) }
    }

    private fun KoreanMorphemeLexiconEntry.supports(
        alternations: List<KoreanMorphemeAlternation>,
        lexicalKind: LexicalKind,
    ): Boolean {
        if (!supportsLexicalKind(lexicalKind)) return false
        if (alternations.isEmpty() || inflectionClasses.isEmpty()) return true

        return alternations.all { alternation ->
            alternation.id in inflectionClasses || alternation.name in inflectionClasses
        }
    }

    private fun KoreanMorphemeLexiconEntry.supportsLexicalKind(lexicalKind: LexicalKind): Boolean {
        if (lexicalKind == LexicalKind.Any || partsOfSpeech.isEmpty()) return true

        val normalizedPartsOfSpeech = partsOfSpeech.map { it.lowercase().replace("-", "_").replace(" ", "_") }.toSet()
        return when (lexicalKind) {
            LexicalKind.Any -> true
            LexicalKind.Predicate -> normalizedPartsOfSpeech.any { it in predicatePartsOfSpeech }
            LexicalKind.Nominal -> normalizedPartsOfSpeech.any { it in nominalPartsOfSpeech }
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

    private fun contractedLemmaAnalysis(
        restoredStemJamo: String,
        surfaceStemJamo: String,
        contraction: ContractedEndingRule,
    ): LemmaAnalysis {
        val lemma = KoreanTextProcessors.assemble(restoredStemJamo + "ㄷㅏ")
        val alternation = (contraction.alternation ?: VOWEL_CONTRACTION).toResult(
            lexical = lemma,
            surface = KoreanTextProcessors.assemble(surfaceStemJamo),
        )
        return LemmaAnalysis(
            lemma = lemma,
            displayName = lemma,
            alternations = listOf(alternation),
        )
    }

    private fun contractedLemmaAnalyses(
        restoredStemJamo: String,
        surfaceStemJamo: String,
        contraction: ContractedEndingRule,
    ): List<LemmaAnalysis> {
        val analyses = mutableListOf(contractedLemmaAnalysis(restoredStemJamo, surfaceStemJamo, contraction))

        if (contraction.alternation == null) {
            if (restoredStemJamo.endsWith("ㄹ")) {
                analyses += irregularContractedAnalysis(
                    lemmaJamo = restoredStemJamo.dropLast(1) + "ㄷㄷㅏ",
                    surfaceStemJamo = surfaceStemJamo,
                    alternation = DIGEUT_IRREGULAR_VOWEL,
                )
            }
            if (!restoredStemJamo.endsWith("ㅅ")) {
                analyses += irregularContractedAnalysis(
                    lemmaJamo = restoredStemJamo + "ㅅㄷㅏ",
                    surfaceStemJamo = surfaceStemJamo,
                    alternation = SIOT_IRREGULAR_VOWEL,
                )
            }
            if (restoredStemJamo.endsWith("ㅜ") || restoredStemJamo.endsWith("ㅗ")) {
                analyses += irregularContractedAnalysis(
                    lemmaJamo = restoredStemJamo.dropLast(1) + "ㅂㄷㅏ",
                    surfaceStemJamo = surfaceStemJamo,
                    alternation = BIEUP_IRREGULAR_VOWEL,
                )
            }
        }

        return analyses.distinctBy { it.lemma + "\u0000" + it.alternations.joinToString("|") { alternation -> alternation.id } }
    }

    private fun irregularContractedAnalysis(
        lemmaJamo: String,
        surfaceStemJamo: String,
        alternation: StemAlternationTemplate,
    ): LemmaAnalysis {
        val lemma = KoreanTextProcessors.assemble(lemmaJamo)
        val result = alternation.toResult(
            lexical = lemma,
            surface = KoreanTextProcessors.assemble(surfaceStemJamo),
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
    ) {
        fun applyTo(stemJamo: String): String {
            return when (alternation) {
                DIGEUT_IRREGULAR_VOWEL -> {
                    if (stemJamo.endsWith("ㄹ") && jamo == "ㄷㄷㅏ") stemJamo.dropLast(1) + jamo else stemJamo + jamo
                }
                BIEUP_IRREGULAR_VOWEL -> {
                    if ((stemJamo.endsWith("ㅇㅜ") || stemJamo.endsWith("ㅇㅗ")) && jamo == "ㅂㄷㅏ") {
                        stemJamo.dropLast(2) + jamo
                    } else {
                        stemJamo + jamo
                    }
                }
                HIEUT_IRREGULAR_VOWEL -> {
                    val stem = if (stemJamo.endsWith("ㅇ")) stemJamo.dropLast(1) else stemJamo
                    val suffix = if (jamo.isNotEmpty() && stem.endsWith(jamo.first().toString())) {
                        jamo.drop(1)
                    } else {
                        jamo
                    }
                    stem + suffix
                }
                else -> stemJamo + jamo
            }
        }
    }

    private data class ContractedEndingRule(
        val surfaceJamo: String,
        val restoredStemSuffixJamo: String,
        val tag: KoreanMorphemeTag,
        val grammarId: String,
        val name: String,
        val score: Int,
        val alternation: StemAlternationTemplate? = null,
    ) {
        fun toSegment(): KoreanMorphemeSegment {
            return KoreanMorphemeSegment(
                surface = KoreanTextProcessors.assemble(surfaceJamo),
                normalized = surfaceJamo,
                tag = tag,
                grammarId = grammarId,
                name = name,
                score = score,
            )
        }
    }

    private data class AuxiliaryVerbRule(
        val stemJamo: String,
        val lemma: String,
        val grammarId: String,
        val connectiveGrammarIds: Set<String>,
        val score: Int = 35,
    )

    private enum class LexicalKind {
        Any,
        Predicate,
        Nominal,
    }

    private fun base(
        suffixJamo: String,
        alternation: StemAlternationTemplate? = null,
    ): StemBaseTemplate {
        return StemBaseTemplate(suffixJamo, alternation)
    }

    private fun contractedEnding(
        surfaceJamo: String,
        restoredStemSuffixJamo: String,
        tag: KoreanMorphemeTag,
        grammarId: String,
        name: String,
        score: Int,
        alternation: StemAlternationTemplate? = null,
    ): ContractedEndingRule {
        return ContractedEndingRule(surfaceJamo, restoredStemSuffixJamo, tag, grammarId, name, score, alternation)
    }

    private fun auxiliaryVerb(
        stem: String,
        lemma: String,
        grammarId: String,
        connectiveGrammarIds: Set<String> = setOf("verb_아_어_해"),
    ): AuxiliaryVerbRule {
        return AuxiliaryVerbRule(
            stemJamo = KoreanTextProcessors.disassemble(stem),
            lemma = lemma,
            grammarId = grammarId,
            connectiveGrammarIds = connectiveGrammarIds,
        )
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
    private val LIEUL_BIEUP_IRREGULAR = StemAlternationTemplate(
        id = "lieul_bieup_irregular",
        name = "ㄼ irregular",
        description = "A ㄼ-final stem surfaces like a ㅂ irregular before vowel and epenthetic endings.",
    )
    private val RIEUL_DROP = StemAlternationTemplate(
        id = "rieul_drop",
        name = "ㄹ drop",
        description = "A ㄹ-final stem drops ㄹ before ㄴ, ㅂ, or ㅅ.",
    )
    private val VOWEL_CONTRACTION = StemAlternationTemplate(
        id = "vowel_contraction",
        name = "vowel contraction",
        description = "A stem-final vowel contracts with 아/어 or 았/었.",
    )

    private val hieutDroppedBases = listOf(
        base("ㅏㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL),
        base("ㅓㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL),
        base("ㅕㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL),
        base("ㅑㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL),
    )

    private val contractedPastRules = listOf(
        contractedEnding("ㅎㅐㅆ", "ㅎㅏ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 48),
        contractedEnding("ㅇㅘㅆ", "ㅂ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 46, BIEUP_IRREGULAR_VOWEL),
        contractedEnding("ㅇㅝㅆ", "ㅂ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 46, BIEUP_IRREGULAR_VOWEL),
        contractedEnding("ㄹㄹㅏㅆ", "ㄹㅡ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 45, LEU_IRREGULAR_VOWEL),
        contractedEnding("ㄹㄹㅓㅆ", "ㄹㅡ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 45, LEU_IRREGULAR_VOWEL),
        contractedEnding("ㅘㅆ", "ㅗ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅝㅆ", "ㅜ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅙㅆ", "ㅚ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅇㅕㅆ", "", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅕㅆ", "ㅕ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅕㅆ", "ㅣ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅇㅏㅆ", "", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅇㅓㅆ", "", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅐㅆ", "ㅐ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅔㅆ", "ㅔ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 42),
        contractedEnding("ㅐㅆ", "ㅏㅎ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 40, HIEUT_IRREGULAR_VOWEL),
        contractedEnding("ㅐㅆ", "ㅓㅎ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 40, HIEUT_IRREGULAR_VOWEL),
        contractedEnding("ㅏㅆ", "ㅏ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 38),
        contractedEnding("ㅓㅆ", "ㅓ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 38),
        contractedEnding("ㅏㅆ", "ㅡ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 36, EU_IRREGULAR_VOWEL),
        contractedEnding("ㅓㅆ", "ㅡ", KoreanMorphemeTag.PreFinalEnding, "verb_았_었_했", "-았/었", 36, EU_IRREGULAR_VOWEL),
    ).sortedByDescending { it.surfaceJamo.length }

    private val contractedInformalRules = listOf(
        contractedEnding("ㅎㅐ", "ㅎㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 44),
        contractedEnding("ㅇㅘ", "ㅂ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 42, BIEUP_IRREGULAR_VOWEL),
        contractedEnding("ㅇㅝ", "ㅂ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 42, BIEUP_IRREGULAR_VOWEL),
        contractedEnding("ㄹㄹㅏ", "ㄹㅡ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 41, LEU_IRREGULAR_VOWEL),
        contractedEnding("ㄹㄹㅓ", "ㄹㅡ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 41, LEU_IRREGULAR_VOWEL),
        contractedEnding("ㅘ", "ㅗ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 38),
        contractedEnding("ㅝ", "ㅜ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 38),
        contractedEnding("ㅙ", "ㅚ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 38),
        contractedEnding("ㅇㅏ", "", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 38),
        contractedEnding("ㅇㅓ", "", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 38),
        contractedEnding("ㅕ", "ㅕ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 38),
        contractedEnding("ㅕ", "ㅣ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 38),
        contractedEnding("ㅐ", "ㅐ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 38),
        contractedEnding("ㅔ", "ㅔ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 38),
        contractedEnding("ㅏ", "ㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 37),
        contractedEnding("ㅓ", "ㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 37),
        contractedEnding("ㅏ", "ㅡ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 36, EU_IRREGULAR_VOWEL),
        contractedEnding("ㅓ", "ㅡ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 36, EU_IRREGULAR_VOWEL),
        contractedEnding("ㅐ", "ㅏㅎ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 36, HIEUT_IRREGULAR_VOWEL),
        contractedEnding("ㅐ", "ㅓㅎ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 36, HIEUT_IRREGULAR_VOWEL),
        contractedEnding("ㅖ", "ㅕㅎ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 36, HIEUT_IRREGULAR_VOWEL),
        contractedEnding("ㅒ", "ㅑㅎ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 36, HIEUT_IRREGULAR_VOWEL),
        contractedEnding("ㄴㅕㅇㅛ", "ㄴㅣ", KoreanMorphemeTag.FinalEnding, "verb_아어요", "-아/어요", 36),
        contractedEnding("ㄴㅖㅇㅛ", "ㄴㅣ", KoreanMorphemeTag.FinalEnding, "verb_아어요", "-아/어요", 36),
        contractedEnding("ㅇㅘㅅㅓ", "ㅗ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", 42),
        contractedEnding("ㅘㅅㅓ", "ㅗ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", 42),
        contractedEnding("ㄹㅓㅅㅓ", "", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", 40),
        contractedEnding("ㅐㅅㅓ", "ㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", 42),
        contractedEnding("ㅓㅅㅓ", "ㅜ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", 40),
        contractedEnding("ㄲㅗㅂ", "ㄱㅗㅅㅣㅍ", KoreanMorphemeTag.ConnectiveEnding, "aux_꼽다", "-꼽-", 40),
        // 러 irregular: extra ㄹ insertion for 르 stems (노르다 → 노르러서, 이르다 → 이르러서)
        contractedEnding("ㄹㅓ", "", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 36),
        contractedEnding("ㄹㅏ", "", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", 36),
    ).sortedByDescending { it.surfaceJamo.length }

    private val auxiliaryVerbRules = listOf(
        auxiliaryVerb("오", "오다", "aux_오다"),
        auxiliaryVerb("가", "가다", "aux_가다"),
        auxiliaryVerb("보", "보다", "aux_보다"),
        auxiliaryVerb("주", "주다", "aux_주다"),
        auxiliaryVerb("두", "두다", "aux_두다"),
        auxiliaryVerb("놓", "놓다", "aux_놓다"),
        auxiliaryVerb("내", "내다", "aux_내다"),
        auxiliaryVerb("대", "대다", "aux_대다"),
        auxiliaryVerb("버리", "버리다", "aux_버리다"),
        auxiliaryVerb("있", "있다", "aux_있다", setOf("verb_고")),
        auxiliaryVerb("싶", "싶다", "aux_싶다", setOf("verb_고")),
        auxiliaryVerb("말", "말다", "aux_말다", setOf("verb_고")),
        auxiliaryVerb("나", "나다", "aux_나다", setOf("verb_고")),
        auxiliaryVerb("않", "않다", "aux_않다", setOf("verb_지")),
        auxiliaryVerb("지", "지다", "aux_지다"),
        auxiliaryVerb("나가", "나가다", "aux_나가다"),
        auxiliaryVerb("나오", "나오다", "aux_나오다"),
    ).sortedByDescending { it.stemJamo.length }

    private val predicatePartsOfSpeech = setOf(
        "v",
        "verb",
        "vv",
        "va",
        "adj",
        "adjective",
    )

    private val nominalPartsOfSpeech = setOf(
        "n",
        "noun",
        "nn",
        "nng",
        "nnp",
        "np",
        "nr",
        "pronoun",
        "proper_noun",
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
        // Older interrogative quotative for verbs (느냐고 vs 냐고 for adjectives)
        ruleWithBases("ㄴㅡㄴㅑㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_느냐고", "-느냐고", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 50),
        rule("ㅈㅏㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_자고", "-자고", "ㄷㅏ", true, 55),
        rule("ㅇㅡㄹㅏㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_으라고_라고", "-으라고", "ㄷㅏ", true, 55),
        rule("ㄹㅏㄱㅗ", KoreanMorphemeTag.QuotationEnding, "verb_라고", "-라고", "ㄷㅏ", true, 50),

        // Quotative contractions (-대, -래, -재, -냬): shortened forms of -다고/라고/자고/냐고 하다
        rule("ㄴㅡㄴㄷㅐ", KoreanMorphemeTag.QuotationEnding, "verb_는대_ㄴ대_대", "-는대", "ㄷㅏ", true, 52),
        ruleWithBases("ㄴㄷㅐ", KoreanMorphemeTag.QuotationEnding, "verb_는대_ㄴ대_대", "-ㄴ대", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), true, 52),
        rule("ㄷㅐ", KoreanMorphemeTag.QuotationEnding, "verb_대", "-대", "ㄷㅏ", false, 48),
        ruleWithBases("ㄹㅐ", KoreanMorphemeTag.QuotationEnding, "verb_래_으래", "-래", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), true, 52),
        rule("ㅇㅡㄹㅐ", KoreanMorphemeTag.QuotationEnding, "verb_으래", "-으래", "ㄷㅏ", true, 52),
        rule("ㅈㅐ", KoreanMorphemeTag.QuotationEnding, "verb_재", "-재", "ㄷㅏ", true, 48),
        rule("ㄴㅒ", KoreanMorphemeTag.QuotationEnding, "verb_냬", "-냬", "ㄷㅏ", true, 48),
        // Colloquial intentional -(으)ㄹ래 (< -(으)려고 하다 + -아/어)
        ruleWithBases("ㅇㅡㄹㄹㅐ", KoreanMorphemeTag.FinalEnding, "verb_을래_ㄹ래", "-(으)ㄹ래", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 40),
        ruleWithBases("ㄹㄹㅐ", KoreanMorphemeTag.FinalEnding, "verb_을래_ㄹ래", "-ㄹ래", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 40),
        rule("ㅅㅡㅂㄴㅣㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_습니다_ㅂ니다", "-(스)ㅂ니다", "ㄷㅏ", false, 55),
        ruleWithBases("ㅂㄴㅣㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_습니다_ㅂ니다", "-(스)ㅂ니다", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 55),
        rule("ㅅㅡㅂㄴㅣㄲㅏ", KoreanMorphemeTag.FinalEnding, "verb_습니까_ㅂ니까", "-(스)ㅂ니까", "ㄷㅏ", false, 55),
        ruleWithBases("ㅂㄴㅣㄲㅏ", KoreanMorphemeTag.FinalEnding, "verb_습니까_ㅂ니까", "-(스)ㅂ니까", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 55),
        ruleWithBases("ㅇㅡㅅㅣㅂㅅㅣㅇㅗ", KoreanMorphemeTag.FinalEnding, "verb_으십시오_십시오", "-(으)십시오", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 50),
        ruleWithBases("ㅇㅜㅅㅣㅂㅅㅣㅇㅗ", KoreanMorphemeTag.FinalEnding, "verb_으십시오_십시오", "-(으)십시오", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 50),
        ruleWithBases("ㅅㅣㅂㅅㅣㅇㅗ", KoreanMorphemeTag.FinalEnding, "verb_으십시오_십시오", "-십시오", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 50),
        ruleWithBases("ㅇㅡㅅㅔㅇㅛ", KoreanMorphemeTag.FinalEnding, "verb_으세요_세요", "-(으)세요", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅇㅜㅅㅔㅇㅛ", KoreanMorphemeTag.FinalEnding, "verb_으세요_세요", "-(으)세요", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅅㅔㅇㅛ", KoreanMorphemeTag.FinalEnding, "verb_으세요_세요", "-세요", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), true, 50),
        ruleWithBases("ㅇㅡㅂㅅㅣㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_읍시다_ㅂ시다", "-(으)ㅂ시다", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 45),
        ruleWithBases("ㅂㅅㅣㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_읍시다_ㅂ시다", "-ㅂ시다", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 45),
        ruleWithBases("ㅇㅡㄹㄲㅏ", KoreanMorphemeTag.FinalEnding, "verb_을까_ㄹ까", "-(으)ㄹ까", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 40),
        ruleWithBases("ㄹㄲㅏ", KoreanMorphemeTag.FinalEnding, "verb_을까_ㄹ까", "-ㄹ까", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 40),
        rule("ㄴㅡㄴㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_다_ㄴ다_는다", "-는다", "ㄷㅏ", false, 40),
        ruleWithBases("ㄴㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_다_ㄴ다_는다", "-ㄴ다", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 40),
        rule(KoreanTextProcessors.disassemble("예요"), KoreanMorphemeTag.FinalEnding, "verb_아어요", "-아/어요", KoreanTextProcessors.disassemble("다"), true, 36),
        rule(KoreanTextProcessors.disassemble("에요"), KoreanMorphemeTag.FinalEnding, "verb_아어요", "-아/어요", KoreanTextProcessors.disassemble("다"), true, 36),
        ruleWithBases("ㅇㅏㅇㅛ", KoreanMorphemeTag.FinalEnding, "verb_아어요", "-아/어요", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅇㅓㅇㅛ", KoreanMorphemeTag.FinalEnding, "verb_아어요", "-아/어요", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 45),
        rule("ㅇㅛ", KoreanMorphemeTag.FinalEnding, "verb_아어요", "-아/어요", "ㄷㅏ", true, 35),
        rule(KoreanTextProcessors.disassemble("야"), KoreanMorphemeTag.FinalEnding, "verb_아어", "-아/어", KoreanTextProcessors.disassemble("다"), true, 36),
        ruleWithBases("ㅇㅏ", KoreanMorphemeTag.FinalEnding, "verb_아어", "-아/어", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅇㅓ", KoreanMorphemeTag.FinalEnding, "verb_아어", "-아/어", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 45),
        rule("ㄹㅏ", KoreanMorphemeTag.FinalEnding, "verb_아어라", "-아/어라", "ㄷㅏ", true, 35),
        rule("ㅈㅏ", KoreanMorphemeTag.FinalEnding, "verb_자", "-자", "ㄷㅏ", false, 35),
        ruleWithBases("ㅇㅡㄴㅑ", KoreanMorphemeTag.FinalEnding, "verb_으냐_냐", "-(으)냐", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 35),
        ruleWithBases("ㄴㅑ", KoreanMorphemeTag.FinalEnding, "verb_으냐_냐", "-냐", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 35),
        ruleWithBases("ㄴㅔ", KoreanMorphemeTag.FinalEnding, "verb_네", "-네", listOf(base("ㄷㅏ")) + hieutDroppedBases, false, 30),
        rule("ㄴㅡㄴㄱㅜㄴㅏ", KoreanMorphemeTag.FinalEnding, "verb_는구나", "-는구나", "ㄷㅏ", false, 30),
        rule("ㄱㅜㄴㅏ", KoreanMorphemeTag.FinalEnding, "verb_구나", "-구나", "ㄷㅏ", false, 30),
        ruleWithBases("ㅇㅡㅁ", KoreanMorphemeTag.FinalEnding, "verb_음_ㅁ", "-(으)ㅁ", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 30),
        ruleWithBases("ㅇㅜㅁ", KoreanMorphemeTag.FinalEnding, "verb_음_ㅁ", "-(으)ㅁ", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 30),
        ruleWithBases("ㅁ", KoreanMorphemeTag.FinalEnding, "verb_음_ㅁ", "-ㅁ", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 30),
        ruleWithBases("ㄹㅇㅜㅅㅣㅂㄴㅣㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_습니다_ㅂ니다", "-(스)ㅂ니다", listOf(base("ㄼㄷㅏ", LIEUL_BIEUP_IRREGULAR)), false, 30),
        ruleWithBases("ㅇㅡㄴㅣ", KoreanMorphemeTag.FinalEnding, "verb_으니_니", "-(으)니", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)) + hieutDroppedBases, false, 30),
        ruleWithBases("ㅇㅜㄴㅣ", KoreanMorphemeTag.FinalEnding, "verb_으니_니", "-(으)니", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 30),
        ruleWithBases("ㄴㅣ", KoreanMorphemeTag.FinalEnding, "verb_으니_니", "-니", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)) + hieutDroppedBases, false, 30),
        ruleWithBases("ㅇㅗ", KoreanMorphemeTag.FinalEnding, "verb_오", "-오", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), true, 25),
        rule("ㄷㅏ", KoreanMorphemeTag.FinalEnding, "verb_다_ㄴ다_는다", "-다", "ㄷㅏ", false, 20),
    ).sortedByDescending { it.surfaceJamo.length }

    private val copulaRules = listOf(
        rule(KoreanTextProcessors.disassemble("이예요"), KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule(KoreanTextProcessors.disassemble("이라서"), KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule(KoreanTextProcessors.disassemble("이라도"), KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule(KoreanTextProcessors.disassemble("에요"), KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule(KoreanTextProcessors.disassemble("라서"), KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule(KoreanTextProcessors.disassemble("라도"), KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule(KoreanTextProcessors.disassemble("야"), KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㄷㅏ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 50, displaySurface = "이다"),
        rule("ㅇㅣㅇㅓㅇㅑㄱㅔㅆ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㅇㅓㅇㅑㅁㅏㄴ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㅇㅓㅈㅣㅇㅣㄷㅏ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㅇㅓㅆㅇㅓㅆ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㅇㅓㅇㅑㅈㅣ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㅇㅓㅇㅑ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅕㅇㅑㄱㅔㅆ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅕㅇㅑㅁㅏㄴ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅕㅈㅣㅇㅣㄷㅏ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅕㅆㅇㅓㅆ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅕㅇㅑㅈㅣ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㄹㄱㅓㄴㅏ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㄹㄲㅔ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅕㅅㅓ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅕㅇㅑ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㅇㅑ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅣㄴㅏ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
        rule("ㅇㅖㅇㅛ", KoreanMorphemeTag.Copula, "noun_이다", "이다", score = 45, displaySurface = "이다"),
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
        rule("ㅇㅡㄹㅣ", KoreanMorphemeTag.PreFinalEnding, "verb_으리", "-(으)리", score = 20),
        rule("ㄹㅣ", KoreanMorphemeTag.PreFinalEnding, "verb_리", "-리", score = 20),
    ).sortedByDescending { it.surfaceJamo.length }

    private val connectiveRules = listOf(
        rule(KoreanTextProcessors.disassemble("라서"), KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", KoreanTextProcessors.disassemble("다"), true, 45),
        rule(KoreanTextProcessors.disassemble("라도"), KoreanMorphemeTag.ConnectiveEnding, "verb_아도_어도_해도", "-아/어도", KoreanTextProcessors.disassemble("다"), true, 45),
        rule("ㅎㅏㅇㅕㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", "ㅎㅏㄷㅏ", true, 50),
        rule("ㅎㅐㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", "ㅎㅏㄷㅏ", true, 50),
        ruleWithBases("ㄹㅇㅝㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄼㄷㅏ", LIEUL_BIEUP_IRREGULAR)), true, 50),
        ruleWithBases("ㅘㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅗㄷㅏ")), true, 50),
        ruleWithBases("ㅇㅘㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅇㅝㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㄹㄹㅏㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄹㅡㄷㅏ", LEU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㄹㄹㅓㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄹㅡㄷㅏ", LEU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅇㅏㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅇㅓㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅏㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄷㅏ"), base("ㅏㄷㅏ"), base("ㅡㄷㅏ", EU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅓㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㄷㅏ"), base("ㅓㄷㅏ"), base("ㅡㄷㅏ", EU_IRREGULAR_VOWEL), base("ㅜㄷㅏ", EU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅐㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅣㄷㅏ"), base("ㅓㄷㅏ"), base("ㅏㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL), base("ㅓㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅔㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅓㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅖㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅇㅕㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅒㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", listOf(base("ㅇㅑㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL)), true, 45),
        rule("ㅅㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아서_어서_해서", "-아/어서", "ㄷㅏ", true, 40),
        rule("ㄱㅓㄴㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_거나", "-거나", "ㄷㅏ", false, 25),
        rule("ㄱㅓㄷㅡㄴ", KoreanMorphemeTag.ConnectiveEnding, "verb_거든", "-거든", "ㄷㅏ", false, 25),
        rule("ㄱㅣㅇㅔ", KoreanMorphemeTag.ConnectiveEnding, "verb_기에", "-기에", "ㄷㅏ", false, 25),
        rule("ㄷㅏㄱㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_다가", "-다가", "ㄷㅏ", false, 25),
        rule("ㄷㅗㄹㅗㄱ", KoreanMorphemeTag.ConnectiveEnding, "verb_도록", "-도록", "ㄷㅏ", false, 25),
        rule("ㄱㅔ", KoreanMorphemeTag.ConnectiveEnding, "verb_게", "-게", "ㄷㅏ", false, 25),
        rule("ㄴㅡㄴㄷㅔ", KoreanMorphemeTag.ConnectiveEnding, "verb_는데", "-는데", "ㄷㅏ", false, 25),
        rule("ㅇㅡㄴㄷㅔ", KoreanMorphemeTag.ConnectiveEnding, "verb_은데", "-은데", "ㄷㅏ", false, 25),
        rule("ㄴㄷㅔ", KoreanMorphemeTag.ConnectiveEnding, "verb_ㄴ데", "-ㄴ데", "ㄷㅏ", false, 25),
        ruleWithBases("ㄹㅇㅜㅁㅕㄴ", KoreanMorphemeTag.ConnectiveEnding, "verb_으면", "-으면", listOf(base("ㄼㄷㅏ", LIEUL_BIEUP_IRREGULAR)), false, 25),
        ruleWithBases("ㅇㅡㅁㅕㄴ", KoreanMorphemeTag.ConnectiveEnding, "verb_으면", "-으면", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)) + hieutDroppedBases, false, 25),
        ruleWithBases("ㅇㅜㅁㅕㄴ", KoreanMorphemeTag.ConnectiveEnding, "verb_으면", "-으면", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 25),
        ruleWithBases("ㅁㅕㄴ", KoreanMorphemeTag.ConnectiveEnding, "verb_면", "-면", listOf(base("ㄷㅏ")) + hieutDroppedBases, false, 25),
        ruleWithBases("ㄹㅇㅜㄴㅣㄲㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_으니까", "-으니까", listOf(base("ㄼㄷㅏ", LIEUL_BIEUP_IRREGULAR)), false, 25),
        ruleWithBases("ㅇㅡㄴㅣㄲㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_으니까", "-으니까", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)) + hieutDroppedBases, false, 25),
        ruleWithBases("ㄴㅣㄲㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_니까", "-니까", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 25),
        rule("ㅇㅡㄹㅕㄱㅗ", KoreanMorphemeTag.ConnectiveEnding, "verb_으려고", "-으려고", "ㄷㅏ", false, 25),
        rule("ㄹㅕㄱㅗ", KoreanMorphemeTag.ConnectiveEnding, "verb_려고", "-려고", "ㄷㅏ", false, 25),
        rule("ㅈㅣㅁㅏㄴ", KoreanMorphemeTag.ConnectiveEnding, "verb_지만", "-지만", "ㄷㅏ", false, 35),
        rule("ㄱㅗ", KoreanMorphemeTag.ConnectiveEnding, "verb_고", "-고", "ㄷㅏ", false, 30),
    ).sortedByDescending { it.surfaceJamo.length }

    private val auxiliaryConnectiveRules = listOf(
        rule("ㅎㅐ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", "ㅎㅏㄷㅏ", true, 50),
        rule("ㄱㅗ", KoreanMorphemeTag.ConnectiveEnding, "verb_고", "-고", "ㄷㅏ", false, 45),
        rule("ㅈㅣ", KoreanMorphemeTag.ConnectiveEnding, "verb_지", "-지", "ㄷㅏ", false, 45),
        ruleWithBases("ㅇㅘ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅇㅝ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㄹㄹㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㄹㅡㄷㅏ", LEU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㄹㄹㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㄹㅡㄷㅏ", LEU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅇㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅇㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㄷㅏ"), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL)), true, 50),
        ruleWithBases("ㅏ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㄷㅏ"), base("ㅏㄷㅏ"), base("ㅡㄷㅏ", EU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅓ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㄷㅏ"), base("ㅓㄷㅏ"), base("ㅡㄷㅏ", EU_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅐ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㅣㄷㅏ"), base("ㅓㄷㅏ"), base("ㅏㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL), base("ㅓㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL)), true, 45),
        ruleWithBases("ㅔ", KoreanMorphemeTag.ConnectiveEnding, "verb_아_어_해", "-아/어", listOf(base("ㅓㅎㄷㅏ", HIEUT_IRREGULAR_VOWEL)), true, 45),
    ).sortedByDescending { it.surfaceJamo.length }

    private val adnominalRules = listOf(
        ruleWithBases("ㄴㅡㄴ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-는", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 25),
        ruleWithBases("ㅇㅡㄴ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-은", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 25),
        ruleWithBases("ㅇㅜㄴ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-은", listOf(base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 25),
        ruleWithBases("ㄴ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-ㄴ", listOf(base("ㄷㅏ"), base("ㄹㄷㅏ", RIEUL_DROP)), false, 25),
        ruleWithBases("ㅇㅡㄹ", KoreanMorphemeTag.AdnominalEnding, "verb_ㄴ_은_는_ㄹ_을", "-을", listOf(base("ㄷㅏ"), base("ㄷㄷㅏ", DIGEUT_IRREGULAR_VOWEL), base("ㅅㄷㅏ", SIOT_IRREGULAR_VOWEL), base("ㅂㄷㅏ", BIEUP_IRREGULAR_VOWEL)), false, 25),
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
    Auxiliary,
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
