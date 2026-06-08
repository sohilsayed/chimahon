package chimahon.ocr

import android.graphics.Bitmap
import tachiyomi.decoder.ImageDecoder
import java.io.ByteArrayInputStream
import java.io.IOException

object OcrBitmapDecoder {

    fun decode(data: ByteArray, sampleSize: Int = 1): Bitmap {
        require(data.isNotEmpty()) { "Image data is empty" }

        return decodeWithReaderDecoder(data, sampleSize)
            ?: throw IOException("Failed to decode image for OCR")
    }

    private fun decodeWithReaderDecoder(data: ByteArray, sampleSize: Int): Bitmap? {
        val decoder = ImageDecoder.newInstance(ByteArrayInputStream(data)) ?: return null
        return try {
            decoder.decode(sampleSize = sampleSize)?.ensureSoftwareArgb8888()
        } finally {
            decoder.recycle()
        }
    }

    private fun Bitmap.ensureSoftwareArgb8888(): Bitmap {
        if (config == Bitmap.Config.ARGB_8888) return this
        val copy = copy(Bitmap.Config.ARGB_8888, false) ?: return this
        recycle()
        return copy
    }
}
