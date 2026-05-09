package chimahon.dictionary.de

object GermanTextPreprocessors {

    fun eszettVariants(text: String): List<String> = listOf(
        text,
        text.replace("ß", "ss").replace("ẞ", "SS"),
        text.replace("ss", "ß").replace("SS", "ẞ"),
    )

    fun decapitalize(text: String): List<String> {
        val lower = text.lowercase()
        return if (lower != text) listOf(text, lower) else listOf(text)
    }

    fun allVariants(text: String): List<String> {
        return decapitalize(text).flatMap { eszettVariants(it) }.distinct()
    }
}
