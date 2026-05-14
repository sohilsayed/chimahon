package chimahon.jiten

class ChapterColorAnalyzer(
    private val apiClient: JitenApiClient,
) {

    suspend fun analyze(
        endpoint: String,
        apiKey: String,
        chapterIndex: Int,
        text: String,
        cache: ParseCache,
    ): List<ColorEntry> {
        if (apiKey.isBlank() || text.isBlank()) return emptyList()

        // Try cache first
        cache.getColors(chapterIndex)?.let { return it }

        // Parse via Jiten API
        val response = apiClient.parse(endpoint, apiKey, text) ?: return emptyList()

        // Build color entries from tokens + vocabulary
        val vocabMap = response.vocabulary.associateBy {
            it.wordId to it.readingIndex
        }

        val colors = mutableListOf<ColorEntry>()

        // Return individual word entries (no merging) so WebView can set wordId/readingIndex attributes
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
            }
        }

        // Cache the result
        cache.setColors(chapterIndex, colors)

        return colors
    }
}
