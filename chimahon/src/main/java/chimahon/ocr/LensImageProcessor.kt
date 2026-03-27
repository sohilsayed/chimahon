package chimahon.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.sqrt

internal data class ImageChunk(
    val pngBytes: ByteArray,
    val width: Int,
    val height: Int,
    val globalY: Int,
    val fullWidth: Int,
    val fullHeight: Int,
)

internal data class ProcessedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

internal fun processImageFromBytes(data: ByteArray): ProcessedImage {
    val bitmap = decodeBitmap(data)
    return processImageInternal(bitmap)
}

internal fun splitImageIntoChunks(
    data: ByteArray,
    chunkHeightLimit: Int = 3000,
): List<ImageChunk> {
    val bitmap = decodeBitmap(data)
    val fullWidth = bitmap.width
    val fullHeight = bitmap.height
    val chunks = mutableListOf<ImageChunk>()

    try {
        var currentY = 0
        while (currentY < fullHeight) {
            val currentChunkHeight = min(chunkHeightLimit, fullHeight - currentY)
            if (currentChunkHeight == 0) {
                break
            }

            val chunkBitmap = Bitmap.createBitmap(bitmap, 0, currentY, fullWidth, currentChunkHeight)
            try {
                chunks += ImageChunk(
                    pngBytes = bitmapToPng(chunkBitmap),
                    width = fullWidth,
                    height = currentChunkHeight,
                    globalY = currentY,
                    fullWidth = fullWidth,
                    fullHeight = fullHeight,
                )
            } finally {
                chunkBitmap.recycle()
            }

            currentY += chunkHeightLimit
        }
    } finally {
        bitmap.recycle()
    }

    return chunks
}

private fun decodeBitmap(data: ByteArray): Bitmap {
    return BitmapFactory.decodeByteArray(data, 0, data.size)
        ?: error("Failed to decode image from bytes")
}

private fun processImageInternal(bitmap: Bitmap): ProcessedImage {
    val resized = resizeIfNeeded(bitmap)
    val pngBytes = bitmapToPng(resized)
    if (resized !== bitmap) bitmap.recycle()
    return ProcessedImage(bytes = pngBytes, width = resized.width, height = resized.height)
}

private fun bitmapToPng(bitmap: Bitmap): ByteArray {
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}

/**
 * Dual-strategy image preparation:
 * - Normal pages (aspect ≤ 3:1): resize proportional to 3MP (owocr approach, full-page context)
 * - Extreme webtoon strips (aspect > 3:1 AND > 3MP): chunk at native resolution
 */
internal fun prepareForOcr(data: ByteArray): List<ImageChunk> {
    val bitmap = decodeBitmap(data)
    val w = bitmap.width
    val h = bitmap.height
    val pixelCount = w * h
    val aspectRatio = h.toDouble() / w

    return if (aspectRatio > WEBTOON_ASPECT_RATIO && pixelCount > MAX_TOTAL_PIXELS) {
        // Extreme webtoon: chunk at native resolution
        chunkImage(bitmap)
    } else {
        // Normal page: resize proportional to 3MP, send as single chunk
        val resized = resizeToMaxPixels(bitmap, MAX_TOTAL_PIXELS)
        listOf(
            ImageChunk(
                pngBytes = bitmapToPng(resized),
                width = resized.width,
                height = resized.height,
                globalY = 0,
                fullWidth = resized.width,
                fullHeight = resized.height,
            ),
        )
    }
}

/**
 * Resize bitmap proportionally to fit within maxTotalPixels.
 * Equivalent to owocr's GoogleLens._preprocess: sqrt(maxPixels * aspectRatio) for width.
 */
internal fun resizeToMaxPixels(bitmap: Bitmap, maxTotalPixels: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    val pixelCount = w * h

    if (pixelCount <= maxTotalPixels) return bitmap

    val aspectRatio = w.toDouble() / h
    val newW = sqrt(maxTotalPixels * aspectRatio).toInt().coerceAtLeast(1)
    val newH = (newW / aspectRatio).toInt().coerceAtLeast(1)

    return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
}

/**
 * Chunk image into vertical strips at native resolution.
 */
internal fun chunkImage(
    bitmap: Bitmap,
    chunkHeightLimit: Int = CHUNK_HEIGHT_LIMIT,
): List<ImageChunk> {
    val fullWidth = bitmap.width
    val fullHeight = bitmap.height
    val chunks = mutableListOf<ImageChunk>()

    try {
        var currentY = 0
        while (currentY < fullHeight) {
            val currentChunkHeight = min(chunkHeightLimit, fullHeight - currentY)
            if (currentChunkHeight == 0) break

            val chunkBitmap = Bitmap.createBitmap(bitmap, 0, currentY, fullWidth, currentChunkHeight)
            try {
                chunks += ImageChunk(
                    pngBytes = bitmapToPng(chunkBitmap),
                    width = fullWidth,
                    height = currentChunkHeight,
                    globalY = currentY,
                    fullWidth = fullWidth,
                    fullHeight = fullHeight,
                )
            } finally {
                chunkBitmap.recycle()
            }
            currentY += chunkHeightLimit
        }
    } finally {
        bitmap.recycle()
    }

    return chunks
}

private fun resizeIfNeeded(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= IMAGE_MAX_DIMENSION && h <= IMAGE_MAX_DIMENSION) return bitmap
    val scale = min(IMAGE_MAX_DIMENSION.toFloat() / w, IMAGE_MAX_DIMENSION.toFloat() / h)
    val newW = (w * scale).toInt().coerceAtLeast(1)
    val newH = (h * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
}
