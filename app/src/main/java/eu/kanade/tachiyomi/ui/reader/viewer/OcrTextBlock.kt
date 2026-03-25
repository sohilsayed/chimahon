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
)

/**
 * Returns the full text of the block by joining all lines.
 * Lines are concatenated without separator (correct for Japanese, no spaces between lines).
 */
val OcrTextBlock.fullText: String
    get() = lines.joinToString("")
