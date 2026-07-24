package chimahon.util

import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.min

object ImageEncoder {

    data class EncodingResult(
        val bytes: ByteArray,
    )

    /**
     * Resizes and encodes a bitmap according to user requirements:
     * - Max resolution: 720p (720x1280 or 1280x720)
     * - Quality: 0.7 (70%)
     * - Format: WebP (Lossy)
     */
    fun encode(
        bitmap: Bitmap,
        quality: Float = 0.7f,
        maxShortSide: Int = 720,
        maxLongSide: Int = 1280,
    ): EncodingResult {
        val resized = resizeIfNeeded(bitmap, maxShortSide, maxLongSide)
        val intQuality = (quality * 100).toInt().coerceIn(0, 100)

        val out = ByteArrayOutputStream()

        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        resized.compress(format, intQuality, out)

        if (resized !== bitmap) {
            resized.recycle()
        }

        return EncodingResult(out.toByteArray())
    }

    private fun resizeIfNeeded(bitmap: Bitmap, maxShortSide: Int, maxLongSide: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height

        val isPortrait = h > w
        val currentShortSide = if (isPortrait) w else h
        val currentLongSide = if (isPortrait) h else w

        if (currentShortSide <= maxShortSide && currentLongSide <= maxLongSide) {
            return bitmap
        }

        val scale = min(
            maxShortSide.toFloat() / currentShortSide,
            maxLongSide.toFloat() / currentLongSide,
        )

        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
