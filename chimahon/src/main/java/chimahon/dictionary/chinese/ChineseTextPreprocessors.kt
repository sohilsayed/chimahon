package chimahon.dictionary.chinese

object ChineseTextPreprocessors {
    fun process(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        
        // Yomitan's Chinese normalization: NFC, lowercase, remove whitespace and specific separators
        val normalized = trimmed.lowercase()
            .replace(Regex("[\\s・:'’\\-]"), "")
            .replace("//", "")
            
        return if (normalized != trimmed) listOf(trimmed, normalized) else listOf(trimmed)
    }
}
