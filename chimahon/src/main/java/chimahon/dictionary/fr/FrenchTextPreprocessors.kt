package chimahon.dictionary.fr

object FrenchTextPreprocessors {

    fun apostropheVariants(text: String): List<String> = listOf(
        text,
        text.replace('\'', '\u2019'),
        text.replace('\u2019', '\''),
    )

    fun decapitalize(text: String): List<String> {
        val lower = text.lowercase()
        return if (lower != text) listOf(text, lower) else listOf(text)
    }

    fun allVariants(text: String): List<String> {
        return decapitalize(text).flatMap { apostropheVariants(it) }.distinct()
    }
}
