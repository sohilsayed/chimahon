package chimahon.ocr


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.min

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

private fun resizeIfNeeded(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= IMAGE_MAX_DIMENSION && h <= IMAGE_MAX_DIMENSION) return bitmap
    val scale = min(IMAGE_MAX_DIMENSION.toFloat() / w, IMAGE_MAX_DIMENSION.toFloat() / h)
    val newW = (w * scale).toInt().coerceAtLeast(1)
    val newH = (h * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
}
