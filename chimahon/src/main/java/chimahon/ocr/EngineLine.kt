package chimahon.ocr

import kotlin.math.abs

internal data class NormalizedBBox(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
}

internal enum class WritingDirection { LTR, TTB, RTL }

/**
 * Shared intermediate representation between any OCR engine and the merger.
 * All coordinates are normalized 0.0–1.0 and axis-aligned.
 */
internal data class EngineLine(
    val text: String,
    val bbox: NormalizedBBox,
    val writingDirection: WritingDirection?,
    val characterSize: Double,
    val hasJpText: Boolean,
    val hasKanji: Boolean,
)

private val CJ_REGEX = Regex("[\u3041-\u3096\u30A1-\u30FA\u4E01-\u9FFF]")
private val KANJI_REGEX_ENGINE = Regex("[\u4E00-\u9FFF]")

internal fun EngineLine(
    text: String,
    bbox: NormalizedBBox,
    writingDirection: WritingDirection?,
    language: OcrLanguage,
): EngineLine {
    val normalizedText = CJ_REGEX.findAll(text).map { it.value }.joinToString("")
    val hasJpText = normalizedText.isNotEmpty()
    val hasKanji = hasJpText && KANJI_REGEX_ENGINE.containsMatchIn(normalizedText)
    val charCount = text.length.coerceAtLeast(1)
    val dimension = if (writingDirection == WritingDirection.TTB) bbox.height else bbox.width
    val characterSize = dimension / charCount

    return EngineLine(
        text = text,
        bbox = bbox,
        writingDirection = writingDirection,
        characterSize = characterSize,
        hasJpText = hasJpText,
        hasKanji = hasKanji,
    )
}
