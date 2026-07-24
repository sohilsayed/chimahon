package chimahon.ocr

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint

/** Shared line renderer used by manga and novel OCR SSIV overlays. */
object OcrTextOverlayPainter {
    fun drawLine(
        canvas: Canvas,
        text: String,
        rect: RectF,
        rotation: Float,
        vertical: Boolean,
        density: Float,
        scaleCompensation: Float,
        minimumTextSize: Float = 8f * density,
        textPaint: TextPaint,
        backgroundPaint: Paint,
        borderPaint: Paint? = null,
        highlightPaint: Paint? = null,
        highlightRange: IntRange? = null,
        drawVerticalCharacter: ((Canvas, Char, Float, Float, Float, Paint) -> Unit)? = null,
        verticalCenter: ((Float, Int, Float, Char) -> Float)? = null,
    ) {
        if (text.isBlank() || rect.width() <= 0f || rect.height() <= 0f) return
        canvas.save()
        canvas.rotate(rotation, rect.centerX(), rect.centerY())
        canvas.drawRect(rect, backgroundPaint)
        borderPaint?.let { canvas.drawRect(rect, it) }

        if (vertical) {
            drawVertical(canvas, text, rect, density, scaleCompensation, minimumTextSize, textPaint, highlightPaint, highlightRange, drawVerticalCharacter, verticalCenter)
        } else {
            drawHorizontal(canvas, text, rect, density, scaleCompensation, minimumTextSize, textPaint, highlightPaint, highlightRange)
        }
        canvas.restore()
    }

    private fun drawHorizontal(
        canvas: Canvas,
        text: String,
        rect: RectF,
        density: Float,
        scaleCompensation: Float,
        minimumTextSize: Float,
        textPaint: TextPaint,
        highlightPaint: Paint?,
        highlightRange: IntRange?,
    ) {
        var low = minimumTextSize
        var high = rect.height() * 2f * scaleCompensation
        var best = low
        repeat(10) {
            val size = (low + high) / 2f
            textPaint.textSize = size
            val fontHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
            if (textPaint.measureText(text) <= rect.width() * 1.05f && fontHeight <= rect.height() * 1.05f) {
                best = size
                low = size + 0.1f
            } else {
                high = size - 0.1f
            }
        }
        textPaint.textSize = best
        textPaint.textAlign = Paint.Align.CENTER
        val baseline = -(textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        val step = rect.width() / text.length.coerceAtLeast(1)
        text.forEachIndexed { index, char ->
            if (highlightRange?.contains(index) == true) {
                highlightPaint?.let { canvas.drawRect(rect.left + step * index, rect.top, rect.left + step * (index + 1), rect.bottom, it) }
            }
            canvas.drawText(char.toString(), rect.left + step * (index + 0.5f), rect.centerY() + baseline, textPaint)
        }
    }

    private fun drawVertical(
        canvas: Canvas,
        text: String,
        rect: RectF,
        density: Float,
        scaleCompensation: Float,
        minimumTextSize: Float,
        textPaint: TextPaint,
        highlightPaint: Paint?,
        highlightRange: IntRange?,
        drawVerticalCharacter: ((Canvas, Char, Float, Float, Float, Paint) -> Unit)?,
        verticalCenter: ((Float, Int, Float, Char) -> Float)?,
    ) {
        val step = rect.height() / text.length.coerceAtLeast(1)
        var low = minimumTextSize
        var high = minOf(rect.width(), step) * 2f * scaleCompensation
        var best = low
        repeat(10) {
            val size = (low + high) / 2f
            textPaint.textSize = size
            val fontHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
            if (textPaint.measureText(text.take(1)) <= rect.width() * 1.05f && fontHeight <= step * 1.05f) {
                best = size
                low = size + 0.1f
            } else {
                high = size - 0.1f
            }
        }
        textPaint.textSize = best
        textPaint.textAlign = Paint.Align.CENTER
        val baseline = -(textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
        text.forEachIndexed { index, char ->
            if (highlightRange?.contains(index) == true) {
                highlightPaint?.let { canvas.drawRect(rect.left, rect.top + step * index, rect.right, rect.top + step * (index + 1), it) }
            }
            val y = verticalCenter?.invoke(rect.top, index, step, char) ?: rect.top + step * (index + 0.5f)
            if (drawVerticalCharacter != null) {
                drawVerticalCharacter(canvas, char, rect.centerX(), y, baseline, textPaint)
            } else {
                canvas.drawText(char.toString(), rect.centerX(), y + baseline, textPaint)
            }
        }
    }
}
