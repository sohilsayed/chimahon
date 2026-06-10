package eu.kanade.tachiyomi.ui.dictionary

import android.graphics.Bitmap
import chimahon.ocr.OcrResult
import eu.kanade.tachiyomi.ui.reader.viewer.OcrLineGeometry
import eu.kanade.tachiyomi.ui.reader.viewer.OcrTextBlock
import eu.kanade.tachiyomi.ui.reader.viewer.fullText
import eu.kanade.tachiyomi.ui.reader.viewer.uniformCharOffset
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

internal fun Bitmap.toScreenLookupOcrPngBytes(maxPixels: Int = 3_000_000): ByteArray {
    val sourcePixels = width.toLong() * height.toLong()
    val bitmapForOcr = if (sourcePixels > maxPixels) {
        val scale = sqrt(maxPixels.toDouble() / sourcePixels.toDouble())
        Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        this
    }

    return ByteArrayOutputStream().use { output ->
        bitmapForOcr.compress(Bitmap.CompressFormat.PNG, 100, output)
        if (bitmapForOcr !== this) bitmapForOcr.recycle()
        output.toByteArray()
    }
}

internal fun List<OcrResult>.toScreenLookupBlocks(language: String): List<OcrTextBlock> {
    return mapNotNull { result ->
        val bbox = result.tightBoundingBox
        val xmin = bbox.x.toFloat().coerceIn(0f, 1f)
        val ymin = bbox.y.toFloat().coerceIn(0f, 1f)
        val xmax = (bbox.x + bbox.width).toFloat().coerceIn(0f, 1f)
        val ymax = (bbox.y + bbox.height).toFloat().coerceIn(0f, 1f)
        val lines = result.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        if (xmax <= xmin || ymax <= ymin || lines.isEmpty()) {
            null
        } else {
            OcrTextBlock(
                xmin = xmin,
                ymin = ymin,
                xmax = xmax,
                ymax = ymax,
                lines = lines,
                vertical = result.forcedOrientation == "vertical",
                lineGeometries = result.constituentBoxes?.map { lineBox ->
                    OcrLineGeometry(
                        xmin = lineBox.x.toFloat().coerceIn(0f, 1f),
                        ymin = lineBox.y.toFloat().coerceIn(0f, 1f),
                        xmax = (lineBox.x + lineBox.width).toFloat().coerceIn(0f, 1f),
                        ymax = (lineBox.y + lineBox.height).toFloat().coerceIn(0f, 1f),
                        rotation = (lineBox.rotation ?: 0.0).toFloat(),
                    )
                },
                language = language,
            )
        }
    }
}

internal fun OcrTextBlock.screenLookupCharOffset(tapX: Float, tapY: Float): Int {
    val geometries = lineGeometries
    if (geometries != null && geometries.size == lines.size) {
        for (i in geometries.indices) {
            val geo = geometries[i]
            val line = lines[i]
            val inLine = if (vertical) {
                tapX >= geo.xmin && tapX <= geo.xmax
            } else {
                tapY >= geo.ymin && tapY <= geo.ymax
            }
            if (inLine) {
                val lineLen = line.length.coerceAtLeast(1)
                val localX = tapX - geo.xmin
                val localY = tapY - geo.ymin
                val geoWidth = (geo.xmax - geo.xmin).coerceAtLeast(0.001f)
                val geoHeight = (geo.ymax - geo.ymin).coerceAtLeast(0.001f)
                val charInLine = if (vertical) {
                    (localY / (geoHeight / lineLen)).toInt().coerceIn(0, lineLen - 1)
                } else {
                    (localX / (geoWidth / lineLen)).toInt().coerceIn(0, lineLen - 1)
                }
                return lines.take(i).sumOf { it.length } + charInLine
            }
        }
    }

    val blockWidth = (xmax - xmin).coerceAtLeast(0.001f)
    val blockHeight = (ymax - ymin).coerceAtLeast(0.001f)
    val localX = (tapX - xmin).coerceIn(0f, blockWidth)
    val localY = (tapY - ymin).coerceIn(0f, blockHeight)
    return uniformCharOffset(this, localX, localY, blockWidth, blockHeight)
        .coerceIn(0, fullText.lastIndex.coerceAtLeast(0))
}
