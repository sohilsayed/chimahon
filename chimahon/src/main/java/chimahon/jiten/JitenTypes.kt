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

@Serializable
data class JitenReviewRequest(
    val wordId: Int,
    val readingIndex: Int,
    val rating: Int,
)

@Serializable
data class JitenReviewResponse(
    val result: String? = null,
)

@Serializable
data class JitenSetVocabularyStateRequest(
    val wordId: Int,
    val readingIndex: Int,
    val state: String,
)

@Serializable
data class JitenSetVocabularyStateResponse(
    val result: String? = null,
)

@Serializable
data class JitenMeaning(
    val glosses: List<String> = emptyList(),
    val partsOfSpeech: List<String> = emptyList(),
)

@Serializable
data class JitenWordCard(
    val wordId: Int,
    val readingIndex: Int,
    val spelling: String,
    val reading: String,
    val frequencyRank: Int = 0,
    val partsOfSpeech: List<String> = emptyList(),
    val meanings: List<JitenMeaning> = emptyList(),
    val cardState: List<String> = emptyList(),
    val pitchAccents: List<Int>? = null,
    val pitchClass: String? = null,
    val conjugations: List<String> = emptyList(),
) {
    val displayWord: String
        get() = if (reading.isNotBlank() && spelling != reading) "$spelling [$reading]" else spelling

    val frequencyDisplay: String?
        get() = if (frequencyRank > 0) "#$frequencyRank" else null

    val flatMeanings: List<String>
        get() = meanings.flatMap { it.glosses }
}

data class WordTapInfo(
    val word: String,
    val wordId: Int,
    val readingIndex: Int,
    val currentState: String,
    val card: JitenWordCard? = null,
    val sentence: String? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
)

object JitenColors {
    val states = mapOf(
        "new" to 0xFF2196F3L,
        "young" to 0xFF4CAF50L,
        "mature" to 0xFF888888L,
        "due" to 0xFFFF9800L,
        "mastered" to 0xFFBBBBBBL,
        "blacklisted" to 0xFFF44336L,
        "not-in-deck" to 0xFFD8B9FAL,
        "locked" to 0xFF777777L,
        "suspended" to 0xFFAAAAAAL,
        "learning" to 0xFF5EA780L,
        "failed" to 0xFFFF0000L,
        "known" to 0xFF70C000L,
        "never-forget" to 0xFF70C000L,
    )

    val grades = mapOf(
        "again" to 0xFFff3b3bL,
        "hard" to 0xFFdf6d2bL,
        "good" to 0xFFD8B9FAL,
        "easy" to 0xFF4fa825L,
    )

    val decks = mapOf(
        "mining" to 0xFFD8B9FAL,
        "never-forget" to 0xFF70C000L,
        "blacklist" to 0xFF777777L,
        "suspend" to 0xFFAAAAAAL,
        "forget" to 0xFFFF3B3BL,
    )

    fun stateColor(state: String): Long = states[state] ?: 0xFF888888L
    fun gradeColor(grade: String): Long = grades[grade] ?: 0xFF888888L
    fun deckColor(deck: String): Long = decks[deck] ?: 0xFF888888L
}

fun knownStateToString(state: Int): String = when (state) {
    0 -> "new"
    1 -> "young"
    2 -> "mature"
    3 -> "blacklisted"
    4 -> "due"
    5 -> "mastered"
    else -> "new"
}

fun stringToKnownStateInt(state: String): Int = when (state) {
    "new" -> 0
    "young" -> 1
    "mature" -> 2
    "blacklisted" -> 3
    "due" -> 4
    "mastered" -> 5
    else -> 0
}
