package chimahon.ocr

/** Returns whether [char] can begin a dictionary lookup. */
fun isOcrLookupStartChar(char: Char): Boolean {
    if (char.isWhitespace()) return false
    val type = Character.getType(char)
    return type != Character.CONNECTOR_PUNCTUATION.toInt() &&
        type != Character.DASH_PUNCTUATION.toInt() &&
        type != Character.START_PUNCTUATION.toInt() &&
        type != Character.END_PUNCTUATION.toInt() &&
        type != Character.INITIAL_QUOTE_PUNCTUATION.toInt() &&
        type != Character.FINAL_QUOTE_PUNCTUATION.toInt() &&
        type != Character.OTHER_PUNCTUATION.toInt() &&
        type != Character.MATH_SYMBOL.toInt() &&
        type != Character.CURRENCY_SYMBOL.toInt() &&
        type != Character.MODIFIER_SYMBOL.toInt() &&
        type != Character.OTHER_SYMBOL.toInt()
}

/** Returns the lookup suffix beginning at [start], matching manga OCR behavior. */
fun extractOcrLookupText(text: String, start: Int): String {
    val result = StringBuilder()
    var index = start.coerceIn(0, text.length)
    while (index < text.length && isOcrLookupStartChar(text[index])) {
        result.append(text[index])
        index++
    }
    return result.toString()
}
