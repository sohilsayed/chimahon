package chimahon

data class ImportResult(
    val success: Boolean,
    val termCount: Long,
    val metaCount: Long,
    val mediaCount: Long,
)

data class DictionaryStyle(
    val dictName: String,
    val styles: String,
)

data class Frequency(
    val value: Int,
    val displayValue: String,
)

data class GlossaryEntry(
    val dictName: String,
    val glossary: String,
    val definitionTags: String,
    val termTags: String,
)

data class FrequencyEntry(
    val dictName: String,
    val frequencies: Array<Frequency>,
)

data class PitchEntry(
    val dictName: String,
    val pitchPositions: IntArray,
)

data class Cloze(
    val sentence: String,
    val prefix: String,
    val body: String,
    val bodyKana: String,
    val suffix: String,
)

data class TermResult(
    val expression: String,
    val reading: String,
    val rules: String,
    val glossaries: Array<GlossaryEntry>,
    val frequencies: Array<FrequencyEntry>,
    val pitches: Array<PitchEntry>,
)

data class LookupResult(
    val matched: String,
    val deinflected: String,
    val process: Array<String>,
    val term: TermResult,
    val preprocessorSteps: Int,
)

object HoshiDicts {
    init {
        System.loadLibrary("hoshidicts_jni")
    }

    external fun importDictionary(zipPath: String, outputDir: String): ImportResult
    external fun createLookupObject(): Long
    external fun destroyLookupObject(session: Long)
    external fun rebuildQuery(
        session: Long,
        termPaths: Array<String>,
        freqPaths: Array<String>,
        pitchPaths: Array<String>,
    )

    external fun lookup(session: Long, text: String, maxResults: Int): Array<LookupResult>
    external fun getStyles(session: Long): Array<DictionaryStyle>
    external fun getMediaFile(session: Long, dictName: String, mediaPath: String): ByteArray?
}
