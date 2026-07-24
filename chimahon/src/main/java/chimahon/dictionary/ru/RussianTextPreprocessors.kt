package chimahon.dictionary.ru

object RussianTextPreprocessors {

    fun yoToE(text: String): List<String> = listOf(
        text,
        text.replace('ё', 'е').replace('Ё', 'Е'),
        text.replace('е', 'ё').replace('Е', 'Ё'),
    )

    fun removeRussianDiacritics(text: String): List<String> = listOf(
        text,
        text.replace("\u0301", ""),
    )

    fun allVariants(text: String): List<String> {
        return removeRussianDiacritics(text).flatMap { yoToE(it) }.distinct()
    }
}
