package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager
import kotlin.math.abs

/**
 * OCR-aware subclass of [SubsamplingScaleImageView].
 *
 * Extends SSIV to render OCR text boxes and handle OCR-specific touch interactions.
 * All OCR state is read from [ocrHost] — this view is a thin shell with zero state of its own.
 *
 * ## Architecture
 * - onDraw: renders OCR boxes and text (reads state from ocrHost)
 * - onTouchEvent: dispatches to GestureDetector for single tap detection on blocks
 * - gestureDetector: uses safe methods (onDown, onSingleTapUp) per SSIV wiki §9
 * - All hit testing done in source pixel space (via viewToSourceCoord)
 *
 * ## Coordinate Spaces
 * - Normalized (0.0–1.0): OcrTextBlock storage format
 * - Source pixels (sWidth × sHeight): SSIV's image pixel space
 * - View/Screen pixels: current screen position after zoom/pan transformation
 */
class OcrSubsamplingImageView(
    context: Context,
) : SubsamplingScaleImageView(context) {

    // Reference to parent ReaderPageImageView for state access
    var ocrHost: ReaderPageImageView? = null

    // Gesture detector for tap handling
    private val gestureDetector = GestureDetector(context, OcrGestureListener())

    // Cache hit test result to avoid redundant calculations during a single gesture
    private var cachedHitBlock: OcrTextBlock? = null
    private var cachedHitX = Float.NaN
    private var cachedHitY = Float.NaN
    private var downOnOcrBox = false
    private var downX = 0f
    private var downY = 0f
    private var swipeReleased = false

    // Webtoon mode keeps scroll/zoom gestures in RecyclerView instead of SSIV.
    var forwardTouchToSuper: Boolean = true

    var dismissHandledInThisGesture = false

    // Text paint for rendering OCR text
    private val textPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 14f * context.resources.displayMetrics.density
        color = Color.BLACK
    }

    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f * context.resources.displayMetrics.density
        color = Color.argb(180, 0, 170, 255)
    }

    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(180, 255, 255, 255)
    }

    private val highlightPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(100, 130, 150, 200) // Soft blue highlight
    }

    // ==================== Rendering ====================

    override fun onDraw(canvas: Canvas) {
        // Always call super first — required by SSIV wiki
        super.onDraw(canvas)

        // Guard 1: SSIV must be ready before using sWidth/sHeight
        if (!isReady) return

        // Guard 2: no host or OCR disabled
        val host = ocrHost ?: return
        if (!host.ocrEnabled) return

        // Guard 3: no blocks to draw
        if (host.ocrBlocks.isEmpty()) return

        // Draw all blocks: borders always visible, text only if active
        for (block in host.ocrBlocks) {
            drawOcrBlock(canvas, block, host)
        }
    }

    /**
     * Draw a single OCR block: border always, text if active.
     */
    private fun drawOcrBlock(canvas: Canvas, block: OcrTextBlock, host: ReaderPageImageView) {
        // Calculate center and size, then apply scale around center
        val centerX = (block.xmin + block.xmax) / 2f
        val centerY = (block.ymin + block.ymax) / 2f
        val width = block.xmax - block.xmin
        val height = block.ymax - block.ymin
        val scale = host.ocrBoxScale

        // Convert normalized coords to source pixel coords with scale applied around center
        val srcXMin = (centerX - (width * scale) / 2f) * sWidth
        val srcYMin = (centerY - (height * scale) / 2f) * sHeight
        val srcXMax = (centerX + (width * scale) / 2f) * sWidth
        val srcYMax = (centerY + (height * scale) / 2f) * sHeight

        // Convert source coords to screen coords
        val tlScreen = sourceToViewCoord(srcXMin, srcYMin) ?: return
        val brScreen = sourceToViewCoord(srcXMax, srcYMax) ?: return

        val screenW = brScreen.x - tlScreen.x
        val screenH = brScreen.y - tlScreen.y

        if (host.ocrOutlineVisible) {
            canvas.drawRect(
                tlScreen.x,
                tlScreen.y,
                brScreen.x,
                brScreen.y,
                borderPaint,
            )
        }

        // Draw text if this block is active
        if (block == host.activeOcrBlock) {
            drawActiveOcrText(canvas, block, host, tlScreen, brScreen, screenW, screenH)
        }
    }

    /**
     * Draw text inside active OCR block with background.
     */
    private fun drawActiveOcrText(
        canvas: Canvas,
        block: OcrTextBlock,
        host: ReaderPageImageView,
        tlScreen: PointF,
        brScreen: PointF,
        screenW: Float,
        screenH: Float,
    ) {
        canvas.save()

        val geometries = block.lineGeometries
        if (geometries != null && geometries.size == block.lines.size) {
            // Per-line rendering logic (matches reference userscript)
            var currentOffset = 0
            for (i in block.lines.indices) {
                val text = block.lines[i]
                drawLineOrientedText(canvas, text, currentOffset, geometries[i], block.vertical, host)
                currentOffset += text.length
            }
        } else {
            // Fallback: block-level rendering
            // Draw semi-transparent background for the entire block only if no geometries
            canvas.drawRect(
                tlScreen.x,
                tlScreen.y,
                brScreen.x,
                brScreen.y,
                backgroundPaint,
            )
            if (block.vertical) {
                drawVerticalOcrText(canvas, block, tlScreen, screenW, screenH, host)
            } else {
                val layout = buildLayoutForHorizontal(block, screenW, screenH, host)
                host.ocrLayoutCache = Pair(block, layout)
                canvas.translate(tlScreen.x, tlScreen.y)
                layout.draw(canvas)
            }
        }

        canvas.restore()
    }

    /**
     * Draw a single line with its own orientation and scale.
     */
    private fun drawLineOrientedText(
        canvas: Canvas,
        text: String,
        lineStartOffset: Int,
        geo: OcrLineGeometry,
        isVertical: Boolean,
        host: ReaderPageImageView,
    ) {
        if (text.isBlank()) return

        // Convert normalized to screen
        val centerX = (geo.xmin + geo.xmax) / 2f
        val centerY = (geo.ymin + geo.ymax) / 2f
        val width = geo.xmax - geo.xmin
        val height = geo.ymax - geo.ymin
        val boxScale = host.ocrBoxScale

        val srcXMin = (centerX - (width * boxScale) / 2f) * sWidth
        val srcYMin = (centerY - (height * boxScale) / 2f) * sHeight
        val srcXMax = (centerX + (width * boxScale) / 2f) * sWidth
        val srcYMax = (centerY + (height * boxScale) / 2f) * sHeight

        val tl = sourceToViewCoord(srcXMin, srcYMin) ?: return
        val br = sourceToViewCoord(srcXMax, srcYMax) ?: return
        val sW = br.x - tl.x
        val sH = br.y - tl.y

        canvas.save()

        // Rotate around center of the line box
        canvas.rotate(geo.rotation, tl.x + sW / 2f, tl.y + sH / 2f)

        // Draw line-level background (now respects rotation)
        canvas.drawRect(tl.x, tl.y, br.x, br.y, backgroundPaint)

        // Auto-detect direction if not explicitly vertical at block level.
        // A box significantly taller than wide is almost certainly vertical Tategumi.
        val lineIsVertical = isVertical || (height / width > 1.2f)

        if (lineIsVertical) {
            // For vertical lines, we use per-character column draw
            drawColumnText(canvas, text, lineStartOffset, tl, sW, sH, host)
        } else {
            // For horizontal lines, we now use binary search for ideal font size
            drawHorizontalLineText(canvas, text, lineStartOffset, tl, sW, sH, host)
        }

        canvas.restore()
    }

    private fun drawHorizontalLineText(canvas: Canvas, text: String, lineStartOffset: Int, tl: PointF, sW: Float, sH: Float, host: ReaderPageImageView) {
        val density = context.resources.displayMetrics.density

        // Binary search for maximizing font size, similar to userscript's findBestFit
        var low = 8f * density
        var high = sH * 2.0f // Allow more headroom for short text in wide boxes
        var bestSize = low

        // Approx 10 iterations for precision
        repeat(10) {
            val mid = (low + high) / 2f
            textPaint.textSize = mid
            val width = textPaint.measureText(text)

            // Matching reference findBestFit: horizontal search is width-based only
            if (width <= sW * 1.05f) {
                bestSize = mid
                low = mid + 0.1f
            } else {
                high = mid - 0.1f
            }
        }

        textPaint.textSize = bestSize
        textPaint.textAlign = Paint.Align.CENTER

        val fm = textPaint.fontMetrics
        val baselineShift = -(fm.ascent + fm.descent) / 2f

        // Centered drawing
        val charWidth = sW / text.length.coerceAtLeast(1)
        val matchedStart = host.activeOcrCharOffset - lineStartOffset
        val matchedEnd = matchedStart + host.activeOcrMatchedCount

        text.forEachIndexed { i, ch ->
            val xCenter = tl.x + charWidth * (i + 0.5f)
            val yCenter = tl.y + sH / 2f
            
            if (host.activeOcrMatchedCount > 0 && i in matchedStart until matchedEnd) {
                canvas.drawRect(
                    tl.x + charWidth * i,
                    tl.y,
                    tl.x + charWidth * (i + 1),
                    tl.y + sH,
                    highlightPaint
                )
            }
            
            canvas.drawText(ch.toString(), xCenter, yCenter + baselineShift, textPaint)
        }
    }

    private fun drawColumnText(canvas: Canvas, text: String, lineStartOffset: Int, tl: PointF, sW: Float, sH: Float, host: ReaderPageImageView) {
        val density = context.resources.displayMetrics.density
        val rowStep = sH / text.length.coerceAtLeast(1)

        // Binary search for maximizing vertical font size
        var low = 8f * density
        var high = minOf(sW, rowStep) * 2.0f
        var bestSize = low

        repeat(10) {
            val mid = (low + high) / 2f
            textPaint.textSize = mid
            // For vertical CJK, width is roughly equal to textSize
            // We measure one character to be sure
            val charWidth = textPaint.measureText(text.take(1))
            val fm = textPaint.fontMetrics
            val charHeight = fm.descent - fm.ascent

            if (charWidth <= sW * 1.05f && charHeight <= rowStep * 1.05f) {
                bestSize = mid
                low = mid + 0.1f
            } else {
                high = mid - 0.1f
            }
        }

        textPaint.textSize = bestSize
        textPaint.textAlign = Paint.Align.CENTER

        val fm = textPaint.fontMetrics
        val baselineShift = -(fm.ascent + fm.descent) / 2f
        val x = tl.x + sW / 2f
        val matchedStart = host.activeOcrCharOffset - lineStartOffset
        val matchedEnd = matchedStart + host.activeOcrMatchedCount

        text.forEachIndexed { i, ch ->
            val yCenter = tl.y + rowStep * (i + 0.5f)

            if (host.activeOcrMatchedCount > 0 && i in matchedStart until matchedEnd) {
                canvas.drawRect(
                    tl.x,
                    tl.y + rowStep * i,
                    tl.x + sW,
                    tl.y + rowStep * (i + 1),
                    highlightPaint
                )
            }

            canvas.drawText(ch.toString(), x, yCenter + baselineShift, textPaint)
        }
    }

    private fun drawVerticalOcrText(
        canvas: Canvas,
        block: OcrTextBlock,
        tlScreen: PointF,
        screenW: Float,
        screenH: Float,
        host: ReaderPageImageView,
    ) {
        val columns = block.lines.filter { it.isNotEmpty() }.ifEmpty { listOf(block.fullText) }
        if (columns.isEmpty()) return

        val columnCount = columns.size.coerceAtLeast(1)
        val maxCharsPerColumn = columns.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1

        val density = context.resources.displayMetrics.density
        val columnWidth = (screenW / columnCount).coerceAtLeast(1f)
        val rowStep = (screenH / maxCharsPerColumn).coerceAtLeast(1f)

        // Fixed uniform grid: all columns use the same vertical step for each character.
        val targetSize = minOf(columnWidth, rowStep) * 0.8f
        textPaint.textSize = targetSize.coerceAtLeast(8f * density)
        textPaint.textAlign = Paint.Align.CENTER

        val fm = textPaint.fontMetrics
        val baselineShift = -(fm.ascent + fm.descent) / 2f
        val contentTop = tlScreen.y + (screenH - rowStep * maxCharsPerColumn) / 2f

        var currentOffset = 0
        columns.forEachIndexed { columnIndex, text ->
            // Japanese vertical text convention: first column starts at right edge.
            val x = tlScreen.x + screenW - columnWidth * (columnIndex + 0.5f)
            val matchedStart = host.activeOcrCharOffset - currentOffset
            val matchedEnd = matchedStart + host.activeOcrMatchedCount

            text.forEachIndexed { charIndex, ch ->
                val yCenter = contentTop + rowStep * (charIndex + 0.5f)

                if (host.activeOcrMatchedCount > 0 && charIndex in matchedStart until matchedEnd) {
                    canvas.drawRect(
                        x - columnWidth / 2f,
                        contentTop + rowStep * charIndex,
                        x + columnWidth / 2f,
                        contentTop + rowStep * (charIndex + 1),
                        highlightPaint
                    )
                }

                canvas.drawText(ch.toString(), x, yCenter + baselineShift, textPaint)
            }
            currentOffset += text.length
        }
    }

    /**
     * Build StaticLayout for horizontal text, with shrink-to-fit.
     */
    private fun buildLayoutForHorizontal(
        block: OcrTextBlock,
        screenW: Float,
        screenH: Float,
        host: ReaderPageImageView,
    ): StaticLayout {
        // Check cache first
        host.ocrLayoutCache?.takeIf { it.first == block }?.second?.let { return it }

        val lineCount = block.lines.size.coerceAtLeast(1)
        var textSize = screenH / lineCount / 1.2f // slight padding

        // Build layout with shrink-to-fit loop
        var layout: StaticLayout
        do {
            textPaint.textSize = textSize.coerceAtLeast(8f * context.resources.displayMetrics.density)
            layout = StaticLayout.Builder.obtain(
                block.fullText,
                0,
                block.fullText.length,
                textPaint,
                screenW.toInt().coerceAtLeast(1),
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            if (layout.height > screenH && textSize > 8f * context.resources.displayMetrics.density) {
                textSize *= 0.85f
            } else {
                break
            }
        } while (true)

        return layout
    }

    private fun getParentScaleCompensation(): Float {
        // Webtoon can apply parent RecyclerView scaling; compensate OCR text size so rendering
        // remains comparable to paged mode at the same visual zoom.
        if (forwardTouchToSuper) return 1f

        var accumulatedScale = 1f
        var current: View? = this
        while (true) {
            val view = current ?: break
            val sx = abs(view.scaleX).coerceAtLeast(0.01f)
            val sy = abs(view.scaleY).coerceAtLeast(0.01f)
            accumulatedScale *= minOf(sx, sy)
            current = view.parent as? View
        }

        return (1f / accumulatedScale).coerceIn(1f, 2f)
    }

    // ==================== Touch Handling ====================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val host = ocrHost
        val ocrEnabled = host?.ocrEnabled == true
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downOnOcrBox = false
            swipeReleased = false
            dismissHandledInThisGesture = false
        }

        // On ACTION_DOWN, check if we hit an OCR block and cache the result
        if (event.actionMasked == MotionEvent.ACTION_DOWN && ocrEnabled) {
            cachedHitBlock = hitTestSource(event.x, event.y)
            cachedHitX = event.x
            cachedHitY = event.y
            downX = event.x
            downY = event.y
            downOnOcrBox = cachedHitBlock != null
            
            // Dismiss active block immediately when touching outside it.
            if (cachedHitBlock == null && host?.activeOcrBlock != null) {
                host.dismissActiveOcrBlock()
                dismissHandledInThisGesture = true
            }

            if (downOnOcrBox) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_MOVE && downOnOcrBox && !swipeReleased) {
            val dx = kotlin.math.abs(event.x - downX)
            val dy = kotlin.math.abs(event.y - downY)
            if (dx > touchSlop || dy > touchSlop) {
                swipeReleased = true
                if (!forwardTouchToSuper) {
                    // Webtoon mode: Release intercept lock so RecyclerView can handle scrolling
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }

        // Let gesture detector process the event first (for safe single tap methods)
        val gestureResult = if (ocrEnabled && !swipeReleased) {
            gestureDetector.onTouchEvent(event)
        } else {
            false
        }

        var superResult = false
        if (forwardTouchToSuper) {
            superResult = super.onTouchEvent(event)
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
            // Clear hit test cache
            cachedHitBlock = null
            cachedHitX = Float.NaN
            cachedHitY = Float.NaN
            downOnOcrBox = false
            swipeReleased = false
        }

        return gestureResult || superResult
    }

    /**
     * Hit test in source pixel space (view coordinate → source coordinate → normalized coordinate).
     *
     * Returns the first block that contains the point, or null if no block is hit.
     */
    private fun hitTestSource(viewX: Float, viewY: Float): OcrTextBlock? {
        val host = ocrHost?.takeIf { it.ocrEnabled } ?: return null
        if (!isReady) return null

        // Convert view coordinates to source coordinates
        val sourcePoint = viewToSourceCoord(viewX, viewY) ?: return null

        // Convert source coordinates to normalized coordinates
        val nx = sourcePoint.x / sWidth
        val ny = sourcePoint.y / sHeight

        // Keep OCR hit testing precise but allow tiny edge tolerance for side taps.
        val edgePaddingViewPx = 2f * context.resources.displayMetrics.density
        val sourcePaddingPx = edgePaddingViewPx / scale.coerceAtLeast(1f)
        val nxPadding = sourcePaddingPx / sWidth
        val nyPadding = sourcePaddingPx / sHeight

        // Apply the same scale to hit area as used in drawing
        val boxScale = host.ocrBoxScale

        // Prioritize currently active block so overlapping OCR boxes don't steal taps.
        host.activeOcrBlock?.let { active ->
            if (blockContainsPoint(active, nx, ny, nxPadding, nyPadding, boxScale)) {
                return active
            }
        }

        // Otherwise resolve first matching block.
        return host.ocrBlocks.firstOrNull { block ->
            blockContainsPoint(block, nx, ny, nxPadding, nyPadding, boxScale)
        }
    }

    private fun blockContainsPoint(
        block: OcrTextBlock,
        nx: Float,
        ny: Float,
        nxPadding: Float,
        nyPadding: Float,
        boxScale: Float,
    ): Boolean {
        val centerX = (block.xmin + block.xmax) / 2f
        val centerY = (block.ymin + block.ymax) / 2f
        val width = block.xmax - block.xmin
        val height = block.ymax - block.ymin

        val scaledXMin = centerX - (width * boxScale) / 2f
        val scaledYMin = centerY - (height * boxScale) / 2f
        val scaledXMax = centerX + (width * boxScale) / 2f
        val scaledYMax = centerY + (height * boxScale) / 2f

        return nx >= (scaledXMin - nxPadding) && nx <= (scaledXMax + nxPadding) &&
            ny >= (scaledYMin - nyPadding) && ny <= (scaledYMax + nyPadding)
    }

    /**
     * Test if a screen point lies within any OCR block.
     * Used by external viewers (pager/webtoon) to filter tap gestures.
     */
    fun isPointOnActiveOrAnyOcrBlock(viewX: Float, viewY: Float): Boolean {
        // Can be called by outside viewers
        return hitTestSource(viewX, viewY) != null
    }

    // ==================== Gesture Listener ====================

    /**
     * Get cached hit test result if coordinates match, otherwise recalculate.
     * Allows gesture detector to reuse ACTION_DOWN hit test result.
     */
    private fun getCachedHitBlock(viewX: Float, viewY: Float): OcrTextBlock? {
        // Use cache if coordinates are close enough (within 3dp tolerance)
        val tolerance = 3f * context.resources.displayMetrics.density
        if (!cachedHitX.isNaN() && !cachedHitY.isNaN() &&
            kotlin.math.abs(viewX - cachedHitX) < tolerance &&
            kotlin.math.abs(viewY - cachedHitY) < tolerance
        ) {
            return cachedHitBlock
        }
        // Fallback: recalculate (gesture detector may call with slightly different coordinates)
        return hitTestSource(viewX, viewY)
    }

    /**
     * Gesture listener using safe methods only (per SSIV wiki §9).
     */
    inner class OcrGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            val hitBlock = getCachedHitBlock(e.x, e.y)
            // Outside-touch dismissal is handled on ACTION_DOWN; only consume when tapping a block.
            return hitBlock != null
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val host = ocrHost ?: return false
            val block = getCachedHitBlock(e.x, e.y)
            if (block == null) {
                if (host.activeOcrBlock != null) {
                    host.dismissActiveOcrBlock()
                    return true
                }
                return false
            }
            // Pass both view and screen coordinates
            return host.handleOcrTap(block, e.x, e.y, e.rawX, e.rawY)
        }

        // onLongPress intentionally NOT overridden
        // Long press on block falls through to pager.longTapListener (normal behavior)
    }
}
