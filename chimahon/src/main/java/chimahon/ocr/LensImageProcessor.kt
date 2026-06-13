package chimahon.ocr

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.sqrt

internal data class ImageChunk(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val globalY: Int,
    val fullWidth: Int,
    val fullHeight: Int,
)

data class ProcessedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

internal fun processImageFromBytes(data: ByteArray): ProcessedImage {
    val bitmap = decodeBitmap(data)
    return processImageInternal(bitmap).also { bitmap.recycle() }
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
            chunks += ImageChunk(
                bitmap = chunkBitmap,
                width = fullWidth,
                height = currentChunkHeight,
                globalY = currentY,
                fullWidth = fullWidth,
                fullHeight = fullHeight,
            )

            currentY += chunkHeightLimit
        }
    } finally {
        bitmap.recycle()
    }

    return chunks
}

private fun decodeBitmap(data: ByteArray): Bitmap {
    return OcrBitmapDecoder.decode(data)
}

internal fun processImageInternal(bitmap: Bitmap): ProcessedImage {
    var resized: Bitmap? = null
    return try {
        val resizedBitmap = resizeIfNeeded(bitmap).also { resized = it }
        val pngBytes = bitmapToPng(resizedBitmap)
        ProcessedImage(bytes = pngBytes, width = resizedBitmap.width, height = resizedBitmap.height)
    } finally {
        if (resized != null && resized !== bitmap) {
            resized.recycle()
        }
    }
}

private fun bitmapToPng(bitmap: Bitmap): ByteArray {
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}

/**
 * Dual-strategy image preparation:
 * - Regular pages: resize proportional to 3MP (owocr approach, full-page context)
 * - Very tall or extreme strips: chunk at native resolution in 3000px bands
 */
internal fun prepareForOcr(data: ByteArray): List<ImageChunk> {
    val bitmap = decodeBitmap(data)
    val w = bitmap.width
    val h = bitmap.height
    val pixelCount = w * h
    val aspectRatio = h.toDouble() / w
    val shouldChunkByHeight = h > TALL_IMAGE_CHUNK_THRESHOLD
    val shouldChunkByAspect = aspectRatio > WEBTOON_ASPECT_RATIO && pixelCount > MAX_TOTAL_PIXELS

    return if (shouldChunkByHeight || shouldChunkByAspect) {
        chunkImage(bitmap).also { bitmap.recycle() }
    } else {
        var resized: Bitmap? = null
        try {
            val resizedBitmap = resizeToMaxPixels(bitmap, MAX_TOTAL_PIXELS).also { resized = it }
            listOf(
                ImageChunk(
                    bitmap = resizedBitmap,
                    width = resizedBitmap.width,
                    height = resizedBitmap.height,
                    globalY = 0,
                    fullWidth = resizedBitmap.width,
                    fullHeight = resizedBitmap.height,
                ),
            )
        } finally {
            if (resized != null && resized !== bitmap) {
                bitmap.recycle()
            }
        }
    }
}

internal fun prepareForOcr(bitmap: Bitmap): List<ImageChunk> {
    val w = bitmap.width
    val h = bitmap.height
    val pixelCount = w * h
    val aspectRatio = h.toDouble() / w
    val shouldChunkByHeight = h > TALL_IMAGE_CHUNK_THRESHOLD
    val shouldChunkByAspect = aspectRatio > WEBTOON_ASPECT_RATIO && pixelCount > MAX_TOTAL_PIXELS

    return if (shouldChunkByHeight || shouldChunkByAspect) {
        chunkImage(bitmap)
    } else {
        val resizedBitmap = resizeToMaxPixels(bitmap, MAX_TOTAL_PIXELS)
        listOf(
            ImageChunk(
                bitmap = resizedBitmap,
                width = resizedBitmap.width,
                height = resizedBitmap.height,
                globalY = 0,
                fullWidth = resizedBitmap.width,
                fullHeight = resizedBitmap.height,
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
    chunkOverlapPx: Int = CHUNK_OVERLAP_PX,
): List<ImageChunk> {
    val fullWidth = bitmap.width
    val fullHeight = bitmap.height
    val chunks = mutableListOf<ImageChunk>()

    val step = (chunkHeightLimit - chunkOverlapPx).coerceAtLeast(1)
    var currentY = 0
    while (currentY < fullHeight) {
        val currentChunkHeight = min(chunkHeightLimit, fullHeight - currentY)
        if (currentChunkHeight == 0) break

        val chunkBitmap = Bitmap.createBitmap(bitmap, 0, currentY, fullWidth, currentChunkHeight)
        chunks += ImageChunk(
            bitmap = chunkBitmap,
            width = fullWidth,
            height = currentChunkHeight,
            globalY = currentY,
            fullWidth = fullWidth,
            fullHeight = fullHeight,
        )
        currentY += step
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
