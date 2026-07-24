package chimahon.ocr

/** Normalized OCR bounds used for shared, scale-aware block hit testing. */
data class OcrNormalizedBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class OcrCharacterHit(
    val offset: Int,
    val distance: Float,
)

data class OcrCharacterLine<T>(
    val value: T,
    val text: String,
    val textOffset: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val rotation: Float,
    val vertical: Boolean,
)

data class OcrCharacterLineHit<T>(
    val value: T,
    val lineOffset: Int,
    val textOffset: Int,
    val distance: Float,
)

object OcrHitTester {
    /**
     * Finds the OCR block whose center is closest to a normalized source coordinate.
     * The scale factors expand around each block's center, matching OCR overlay
     * rendering and preventing selection from drifting at scaled box edges.
     */
    fun <T> findBlock(
        blocks: Iterable<T>,
        x: Float,
        y: Float,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        paddingX: Float = 0f,
        paddingY: Float = 0f,
        bounds: (T) -> OcrNormalizedBounds,
    ): T? = blocks
        .filter { block -> contains(bounds(block), x, y, scaleX, scaleY, paddingX, paddingY) }
        .minByOrNull { block ->
            val box = bounds(block)
            val dx = x - (box.left + box.right) / 2f
            val dy = y - (box.top + box.bottom) / 2f
            dx * dx + dy * dy
        }

    fun contains(
        box: OcrNormalizedBounds,
        x: Float,
        y: Float,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        paddingX: Float = 0f,
        paddingY: Float = 0f,
    ): Boolean {
        val centerX = (box.left + box.right) / 2f
        val centerY = (box.top + box.bottom) / 2f
        val halfWidth = (box.right - box.left) * scaleX / 2f
        val halfHeight = (box.bottom - box.top) * scaleY / 2f
        return x >= centerX - halfWidth - paddingX && x <= centerX + halfWidth + paddingX &&
            y >= centerY - halfHeight - paddingY && y <= centerY + halfHeight + paddingY
    }

    /**
     * Maps a point to the character cell drawn inside a possibly rotated OCR line.
     * Coordinates may be in any space as long as the point and bounds use the same one.
     */
    fun hitCharacter(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        x: Float,
        y: Float,
        rotation: Float,
        vertical: Boolean,
    ): OcrCharacterHit? {
        if (text.isEmpty()) return null

        val width = right - left
        val height = bottom - top
        val centerX = left + width / 2f
        val centerY = top + height / 2f
        val radians = Math.toRadians(-rotation.toDouble())
        val dx = (x - centerX).toDouble()
        val dy = (y - centerY).toDouble()
        val localX = (dx * Math.cos(radians) - dy * Math.sin(radians)).toFloat() + width / 2f
        val localY = (dx * Math.sin(radians) + dy * Math.cos(radians)).toFloat() + height / 2f

        if (localX < 0f || localX > width || localY < 0f || localY > height) return null

        return if (vertical) {
            OcrCharacterHit(
                offset = (localY / (height / text.length.coerceAtLeast(1)))
                    .toInt()
                    .coerceIn(0, text.length - 1),
                distance = kotlin.math.abs(localX - width / 2f),
            )
        } else {
            OcrCharacterHit(
                offset = (localX / (width / text.length.coerceAtLeast(1)))
                    .toInt()
                    .coerceIn(0, text.length - 1),
                distance = kotlin.math.abs(localY - height / 2f),
            )
        }
    }

    /** Selects the nearest rendered OCR line and maps the point to its character. */
    fun <T> hitLines(
        lines: Iterable<OcrCharacterLine<T>>,
        x: Float,
        y: Float,
    ): OcrCharacterLineHit<T>? = lines.mapNotNull { line ->
        hitCharacter(
            text = line.text,
            left = line.left,
            top = line.top,
            right = line.right,
            bottom = line.bottom,
            x = x,
            y = y,
            rotation = line.rotation,
            vertical = line.vertical,
        )?.let { hit ->
            OcrCharacterLineHit(
                value = line.value,
                lineOffset = hit.offset,
                textOffset = line.textOffset + hit.offset,
                distance = hit.distance,
            )
        }
    }.minByOrNull { it.distance }

    /** Uses manga's OCR line-orientation heuristic. */
    fun isLineVertical(
        blockVertical: Boolean,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): Boolean {
        val width = right - left
        if (width <= 0f) return blockVertical
        return blockVertical || (bottom - top) / width > 1.2f
    }
}
