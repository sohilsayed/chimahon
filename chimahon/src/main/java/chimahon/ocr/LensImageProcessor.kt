package chimahon.ocr

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min

data class ProcessedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

internal fun processImageFromBytes(data: ByteArray): ProcessedImage {
    val bitmap = decodeBitmap(data)
    return processImageInternal(bitmap).also { bitmap.recycle() }
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

private fun resizeIfNeeded(bitmap: Bitmap): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= IMAGE_MAX_DIMENSION && h <= IMAGE_MAX_DIMENSION) return bitmap
    val scale = min(IMAGE_MAX_DIMENSION.toFloat() / w, IMAGE_MAX_DIMENSION.toFloat() / h)
    val newW = (w * scale).toInt().coerceAtLeast(1)
    val newH = (h * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
}
