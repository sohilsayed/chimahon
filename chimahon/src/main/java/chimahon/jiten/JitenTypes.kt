package chimahon.jiten

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class JitenParseRequest(
    val text: List<String>,
)

@Serializable
data class JitenParseResponse(
    val tokens: List<List<JitenToken>>,
    val vocabulary: List<JitenRawVocabulary>,
)

@Serializable
data class JitenToken(
    val start: Int,
    val end: Int,
    val wordId: Int,
    val readingIndex: Int,
    val sentence: String? = null,
    val pitchClass: String? = null,
    val rubies: List<JitenRuby>? = null,
    val conjugations: List<String>? = null,
)

@Serializable
data class JitenRuby(
    val text: String,
    val start: Int,
    val end: Int,
    val length: Int,
)

@Serializable
data class JitenRawVocabulary(
    val wordId: Int,
    val readingIndex: Int,
    val spelling: String,
    val reading: String,
    val frequencyRank: Int = 0,
    val partsOfSpeech: List<String> = emptyList(),
    @SerialName("meaningsChunks")
    val meaningsChunks: List<List<String>> = emptyList(),
    @SerialName("meaningsPartOfSpeech")
    val meaningsPartOfSpeech: List<String> = emptyList(),
    val knownState: List<Int> = emptyList(),
    val pitchAccents: List<Int>? = null,
)

@Serializable
data class ColorEntry(
    val startOffset: Int,
    val length: Int,
    val state: String,
    val wordId: Int = -1,
    val readingIndex: Int = -1,
)

@Serializable
data class JitenLookupVocabularyRequest(
    val words: List<List<Int>>,
)

@Serializable
data class JitenLookupVocabularyResponse(
    val result: List<List<Int>> = emptyList(),
)

@Serializable
data class JitenErrorResponse(
    val error_message: String? = null,
)

fun knownStateToString(state: Int): String = when (state) {
    0 -> "new"
    1 -> "young"
    2 -> "mature"
    3 -> "blacklisted"
    4 -> "due"
    5 -> "mastered"
    else -> "new"
}
