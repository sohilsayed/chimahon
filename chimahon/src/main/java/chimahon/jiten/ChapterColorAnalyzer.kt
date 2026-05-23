package chimahon.jiten

class ChapterColorAnalyzer(
    private val apiClient: JitenApiClient,
) {

    data class AnalyzeResult(
        val colors: List<ColorEntry>,
        val cards: Map<Pair<Int, Int>, JitenWordCard>,
    )

    suspend fun analyze(
        endpoint: String,
        apiKey: String,
        chapterIndex: Int,
        text: String,
        cache: ParseCache,
    ): AnalyzeResult {
        if (apiKey.isBlank() || text.isBlank()) return AnalyzeResult(emptyList(), emptyMap())

        // Try cache first
        val cachedColors = cache.getColors(chapterIndex)
        val cachedCards = cache.getCards(chapterIndex)
        if (cachedColors != null) {
            return AnalyzeResult(cachedColors, cachedCards ?: emptyMap())
        }

        // Parse via Jiten API
        val response = apiClient.parse(endpoint, apiKey, text) ?: return AnalyzeResult(emptyList(), emptyMap())

        // Build vocabulary map
        val vocabMap = response.vocabulary.associateBy {
            it.wordId to it.readingIndex
        }

        val colors = mutableListOf<ColorEntry>()
        val cards = mutableMapOf<Pair<Int, Int>, JitenWordCard>()

        // Build color entries and card data from tokens + vocabulary
        for (paraTokens in response.tokens) {
            for (token in paraTokens) {
                val vocab = vocabMap[token.wordId to token.readingIndex]
                val state = if (vocab != null && vocab.knownState.isNotEmpty()) {
                    knownStateToString(vocab.knownState[0])
                } else {
                    "new"
                }

                colors.add(
                    ColorEntry(
                        startOffset = token.start,
                        length = token.end - token.start,
                        state = state,
                        wordId = token.wordId,
                        readingIndex = token.readingIndex,
                    ),
                )

                // Build card data for popup display (deduplicated by wordId/readingIndex)
                if (vocab != null && cards[token.wordId to token.readingIndex] == null) {
                    val meanings = if (vocab.meaningsChunks.isNotEmpty()) {
                        vocab.meaningsChunks.mapIndexed { i, glosses ->
                            val pos = vocab.meaningsPartOfSpeech.getOrNull(i) ?: ""
                            JitenMeaning(
                                glosses = glosses,
                                partsOfSpeech = if (pos.isNotBlank()) listOf(pos) else emptyList(),
                            )
                        }
                    } else {
                        emptyList()
                    }

                    val cardState = if (vocab.knownState.isNotEmpty()) {
                        listOf(knownStateToString(vocab.knownState[0]))
                    } else {
                        listOf("new")
                    }

                    cards[token.wordId to token.readingIndex] = JitenWordCard(
                        wordId = token.wordId,
                        readingIndex = token.readingIndex,
                        spelling = vocab.spelling,
                        reading = vocab.reading,
                        frequencyRank = vocab.frequencyRank,
                        partsOfSpeech = vocab.partsOfSpeech,
                        meanings = meanings,
                        cardState = cardState,
                        pitchAccents = vocab.pitchAccents,
                        pitchClass = token.pitchClass,
                        conjugations = token.conjugations ?: emptyList(),
                    )
                }
            }
        }

        // Cache the result
        cache.setColors(chapterIndex, colors)
        cache.setCards(chapterIndex, cards)

        return AnalyzeResult(colors, cards)
    }
}
