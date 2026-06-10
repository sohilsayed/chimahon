package eu.kanade.tachiyomi.ui.reader.viewer

/**
 * Represents an OCR-detected text block (normalized coordinates, pre-processed offline).
 *
 * Coordinates are normalized to 0.0–1.0 relative to image dimensions.
 * This format is produced by the Chrome Lens OCR pipeline and avoids
 * dependency on original image pixel dimensions at render time.
 *
 * @param xmin normalized X coordinate of left edge (0.0–1.0)
 * @param ymin normalized Y coordinate of top edge (0.0–1.0)
 * @param xmax normalized X coordinate of right edge (0.0–1.0)
 * @param ymax normalized Y coordinate of bottom edge (0.0–1.0)
 * @param lines list of text strings (one per line within the block)
 * @param vertical true for vertical (tategumi) text, false for horizontal
 */
data class OcrTextBlock(
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
    val lines: List<String>,
    val vertical: Boolean = false,
    val lineGeometries: List<OcrLineGeometry>? = null,
    val language: String = "",
)

/**
 * Geometry for an individual line within a block.
 */
data class OcrLineGeometry(
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
    val rotation: Float = 0f,
)

/**
 * Returns the full text of the block by joining all lines.
 * Lines are concatenated without separator (correct for Japanese, no spaces between lines).
 */
val OcrTextBlock.fullText: String
    get() = lines.joinToString("")

/**
 * Returns block text in reading order derived from the line geometry.
 *
 * OCR engines can return line text in detection order, which is not always the
 * same order the OCR box is read in. Geometry lets Anki sentence export use the
 * same block ordering as the visible text box.
 */
val OcrTextBlock.orderedFullText: String
    get() = orderedLineIndices().joinToString("") { index -> lines[index] }

/**
 * Converts an offset based on [fullText] to the equivalent offset in
 * [orderedFullText].
 */
fun OcrTextBlock.toOrderedOffset(rawOffset: Int): Int {
    if (lines.isEmpty()) return 0

    val safeRawOffset = rawOffset.coerceIn(0, fullText.length)
    var rawLineStart = 0
    var rawLineIndex = lines.lastIndex
    var offsetInLine = 0

    for (i in lines.indices) {
        val lineLength = lines[i].length
        val rawLineEnd = rawLineStart + lineLength
        if (safeRawOffset <= rawLineEnd) {
            rawLineIndex = i
            offsetInLine = (safeRawOffset - rawLineStart).coerceIn(0, lineLength)
            break
        }
        rawLineStart = rawLineEnd
    }

    val orderedIndices = orderedLineIndices()
    val orderedLineStart = orderedIndices
        .takeWhile { it != rawLineIndex }
        .sumOf { lines[it].length }

    return (orderedLineStart + offsetInLine).coerceIn(0, orderedFullText.length)
}

private fun OcrTextBlock.orderedLineIndices(): List<Int> {
    val geometries = lineGeometries
    if (lines.size <= 1 || geometries == null || geometries.size != lines.size) {
        return lines.indices.toList()
    }

    return if (vertical) {
        lines.indices.sortedWith(
            compareByDescending<Int> { geometries[it].centerX }
                .thenBy { geometries[it].ymin },
        )
    } else {
        lines.indices.sortedWith(
            compareBy<Int> { geometries[it].centerY }
                .thenBy { geometries[it].xmin },
        )
    }
}

private val OcrLineGeometry.centerX: Float
    get() = (xmin + xmax) / 2f

private val OcrLineGeometry.centerY: Float
    get() = (ymin + ymax) / 2f

internal fun isLookupStartChar(char: Char): Boolean {
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

internal fun extractOcrLookupString(text: String, start: Int): String {
    val result = StringBuilder()
    var index = start.coerceIn(0, text.length)
    while (index < text.length) {
        val char = text[index]
        if (!isLookupStartChar(char)) break
        result.append(char)
        index++
    }
    return result.toString()
}

internal fun uniformCharOffset(
    block: OcrTextBlock,
    localX: Float,
    localY: Float,
    screenW: Float,
    screenH: Float,
): Int {
    if (block.vertical) {
        val columns = block.lines.ifEmpty { listOf(block.fullText) }
        if (columns.isEmpty()) return 0

        val columnWidth = (screenW / columns.size.coerceAtLeast(1)).coerceAtLeast(1f)
        val maxChars = columns.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
        val rowHeight = (screenH / maxChars).coerceAtLeast(1f)
        val contentTop = (screenH - rowHeight * maxChars) / 2f

        val fromRight = (screenW - localX).coerceIn(0f, screenW)
        val columnIndex = (fromRight / columnWidth)
            .toInt()
            .coerceIn(0, columns.lastIndex)

        val columnText = columns[columnIndex]
        val charsInColumn = columnText.length.coerceAtLeast(1)
        val yInColumn = (localY - contentTop)
            .coerceIn(0f, (rowHeight * charsInColumn - 0.001f).coerceAtLeast(0f))
        val charIndex = (yInColumn / rowHeight)
            .toInt()
            .coerceIn(0, charsInColumn - 1)

        return columns.take(columnIndex).sumOf { it.length } + charIndex
    }

    val lineCount = block.lines.size.coerceAtLeast(1)
    val lineIndex = (localY / (screenH / lineCount))
        .toInt().coerceIn(0, lineCount - 1)
    val line = block.lines[lineIndex]
    val charIndex = (localX / (screenW / line.length.coerceAtLeast(1)))
        .toInt().coerceIn(0, line.length - 1)
    return block.lines.take(lineIndex).sumOf { it.length } + charIndex
}
