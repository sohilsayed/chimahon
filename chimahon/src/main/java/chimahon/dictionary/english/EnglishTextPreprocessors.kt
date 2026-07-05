package chimahon.dictionary.english

object EnglishTextPreprocessors {

    fun decapitalize(text: String): List<String> {
        val lower = text.lowercase()
        return if (lower != text) listOf(text, lower) else listOf(text)
    }
}
