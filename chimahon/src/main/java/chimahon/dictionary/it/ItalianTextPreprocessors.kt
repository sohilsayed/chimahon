package chimahon.dictionary.it

object ItalianTextPreprocessors {

    fun removeApostrophedWords(text: String): List<String> {
        val cleaned = text.replace(Regex("(l|dell|all|dall|nell|sull|coll|un|quest|quell|c|n)['\u2019]"), "")
        return if (cleaned != text) listOf(text, cleaned) else listOf(text)
    }

    fun removeAlphabeticDiacritics(text: String): List<String> {
        val cleaned = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
        return if (cleaned != text) listOf(text, cleaned) else listOf(text)
    }

    fun decapitalize(text: String): List<String> {
        val lower = text.lowercase()
        return if (lower != text) listOf(text, lower) else listOf(text)
    }

    fun allVariants(text: String): List<String> {
        return decapitalize(text).flatMap { removeAlphabeticDiacritics(it) }
            .flatMap { removeApostrophedWords(it) }
            .distinct()
    }
}
