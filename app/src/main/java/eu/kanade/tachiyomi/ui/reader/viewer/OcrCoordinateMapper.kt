package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Bitmap
import android.graphics.RectF
import chimahon.ocr.OcrBitmapDecoder
import okio.BufferedSource
import okio.Buffer
import tachiyomi.core.common.util.system.logcat
import logcat.LogPriority
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Coordinate remapping for OCR blocks under reader transformations.
 *
 * OCR blocks store normalized coordinates (0.0–1.0) relative to the **original** image.
 * When the reader applies transformations (crop borders, split, merge, webtoon splitAndMerge)
 * the displayed bitmap differs from the original, so block coordinates must be remapped.
 *
 * ## Crop border detection
 *
 * We port the *exact* algorithm from tachiyomi/image-decoder's `borders.cpp` to Kotlin so
 * the computed crop rect is pixel-perfect with what ImageDecoder produces.
 *
 * Constants (from borders.h):
 *   - `thresholdForBlack` = 191  (255 * 0.75)
 *   - `thresholdForWhite` = 63   (255 – 191)
 *   - `filledRatioLimit`  = 0.0025
 *
 * The scan works on a grayscale bitmap sampled every 2nd pixel.
 */
object OcrCoordinateMapper {

    // ──────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────

    /**
     * Remap blocks from full-image normalized space into the **left or right half** after
     * a wide image is split in half.
     *
     * - Blocks whose center falls in the chosen (kept) half are kept; others are discarded.
     * - x-coordinates are rescaled: [0.0, 0.5] → [0.0, 1.0] for LEFT, [0.5, 1.0] → [0.0, 1.0] for RIGHT.
     * - y-coordinates are unchanged.
     *
     * @param blocks OCR blocks with coords normalized to the original wide image.
     * @param keepLeft true = keep left half  (RIGHT side label in splitInHalf maps to keepLeft=true for L2R viewers)
     */
    fun mapToSplit(
        blocks: List<OcrTextBlock>,
        keepLeft: Boolean,
    ): List<OcrTextBlock> {
        val offset = if (keepLeft) 0f else 0.5f
        return blocks.mapNotNull { block ->
            val centerX = (block.xmin + block.xmax) / 2f
            // Keep only blocks whose center is in the chosen half
            val inHalf = if (keepLeft) centerX <= 0.5f else centerX >= 0.5f
            if (!inHalf) return@mapNotNull null

            // Rescale from half-space [offset, offset+0.5] → [0, 1]
            val newXmin = ((block.xmin - offset) * 2f).coerceIn(0f, 1f)
            val newXmax = ((block.xmax - offset) * 2f).coerceIn(0f, 1f)
            if (newXmax <= newXmin) return@mapNotNull null

            val newGeometries = block.lineGeometries?.mapNotNull { geo ->
                val geoCenterX = (geo.xmin + geo.xmax) / 2f
                val geoInHalf = if (keepLeft) geoCenterX <= 0.5f else geoCenterX >= 0.5f
                if (!geoInHalf) return@mapNotNull null
                OcrLineGeometry(
                    xmin = ((geo.xmin - offset) * 2f).coerceIn(0f, 1f),
                    ymin = geo.ymin,
                    xmax = ((geo.xmax - offset) * 2f).coerceIn(0f, 1f),
                    ymax = geo.ymax,
                    rotation = geo.rotation,
                )
            }

            block.copy(
                xmin = newXmin,
                xmax = newXmax,
                lineGeometries = newGeometries,
            )
        }
    }

    /**
     * Merge OCR blocks from two pages onto a unified merged-bitmap coordinate space.
     *
     * The merged bitmap is produced by [ImageUtil.mergeBitmaps]:
     *   - Width = page1W + centerMarginPx + page2W
     *   - Height = max(page1H, page2H)
     *   - Each page is vertically centered inside the total height.
     *
     * @param blocks1   OCR blocks for page 1, normalized to page 1's original dims.
     * @param page1W    pixel width of page 1
     * @param page1H    pixel height of page 1
     * @param blocks2   OCR blocks for page 2, normalized to page 2's original dims.
     * @param page2W    pixel width of page 2
     * @param page2H    pixel height of page 2
     * @param isLTR     true if page1 is on the left (L2R reader)
     * @param centerMarginPx  pixel gap between pages in the merged bitmap
     */
    fun mapToMerged(
        blocks1: List<OcrTextBlock>,
        page1W: Int,
        page1H: Int,
        blocks2: List<OcrTextBlock>,
        page2W: Int,
        page2H: Int,
        isLTR: Boolean,
        centerMarginPx: Int,
    ): List<OcrTextBlock> {
        val totalW = (page1W + centerMarginPx + page2W).toFloat()
        val totalH = max(page1H, page2H).toFloat()

        if (totalW <= 0 || totalH <= 0) return blocks1

        // page positions in the merged bitmap
        val leftW: Int
        val leftH: Int
        val rightW: Int
        val rightH: Int
        val leftBlocks: List<OcrTextBlock>
        val rightBlocks: List<OcrTextBlock>

        if (isLTR) {
            leftW = page1W; leftH = page1H; leftBlocks = blocks1
            rightW = page2W; rightH = page2H; rightBlocks = blocks2
        } else {
            leftW = page2W; leftH = page2H; leftBlocks = blocks2
            rightW = page1W; rightH = page1H; rightBlocks = blocks1
        }

        val leftXOffset = 0f
        val leftYOffset = (totalH - leftH) / 2f
        val rightXOffset = leftW + centerMarginPx.toFloat()
        val rightYOffset = (totalH - rightH) / 2f

        val mapped1 = remapToCanvas(leftBlocks, leftW.toFloat(), leftH.toFloat(), leftXOffset, leftYOffset, totalW, totalH)
        val mapped2 = remapToCanvas(rightBlocks, rightW.toFloat(), rightH.toFloat(), rightXOffset, rightYOffset, totalW, totalH)

        return mapped1 + mapped2
    }

    /**
     * Remap blocks to match the cropped image that ImageDecoder returns when cropBorders=true.
     *
     * Reads the image source stream, detects crop margins using the same algorithm as the
     * native image-decoder library (ports borders.cpp exactly), then adjusts OCR coordinates
     * so they align with the cropped bitmap.
     *
     * Returns original blocks unchanged if:
     * - The stream cannot be decoded
     * - No crop is detected (full image is returned)
     *
     * @param blocks  OCR blocks normalized to the original (uncropped) image
     * @param stream  A BufferedSource for the image (will be peeked, not consumed)
     */
    fun mapToCropped(
        blocks: List<OcrTextBlock>,
        stream: BufferedSource,
    ): List<OcrTextBlock> {
        val cropRect = detectCropRect(stream) ?: return blocks
        if (cropRect.left == 0f && cropRect.top == 0f && cropRect.right == 1f && cropRect.bottom == 1f) {
            return blocks
        }
        return remapToCrop(blocks, cropRect)
    }

    /**
     * Remap blocks for Webtoon "splitAndMerge" mode.
     *
     * [ImageUtil.splitAndMerge] splits a wide image and stacks the two halves vertically.
     * The upper half is the RIGHT side (or left when inverted), lower half is the other side.
     *
     * @param blocks    OCR blocks normalized to the original wide image
     * @param upperIsRight  true if the upper stack is the right half (default Webtoon behavior)
     */
    fun mapToSplitAndMerge(
        blocks: List<OcrTextBlock>,
        upperIsRight: Boolean = true,
    ): List<OcrTextBlock> {
        // After splitAndMerge the resulting image has:
        //   Width  = half the original width
        //   Height = 2x the original height (both halves stacked)
        //
        // Upper half = RIGHT (x in [0.5..1] original) → y in [0..0.5] new
        // Lower half = LEFT  (x in [0..0.5] original) → y in [0.5..1] new

        return blocks.mapNotNull { block ->
            val centerX = (block.xmin + block.xmax) / 2f
            val isInRight = centerX >= 0.5f

            val (xOffset, yOffset) = if (upperIsRight) {
                if (isInRight) Pair(0.5f, 0f) else Pair(0f, 0.5f)
            } else {
                if (isInRight) Pair(0.5f, 0.5f) else Pair(0f, 0f)
            }

            // Rescale x from [xOffset, xOffset+0.5] → [0, 1]
            val newXmin = ((block.xmin - xOffset) * 2f).coerceIn(0f, 1f)
            val newXmax = ((block.xmax - xOffset) * 2f).coerceIn(0f, 1f)
            // Rescale y: original y stays, but it now occupies only 1/2 of the new height
            // new_y = yOffset + old_y * 0.5
            val newYmin = (yOffset + block.ymin * 0.5f).coerceIn(0f, 1f)
            val newYmax = (yOffset + block.ymax * 0.5f).coerceIn(0f, 1f)

            if (newXmax <= newXmin || newYmax <= newYmin) return@mapNotNull null

            val newGeometries = block.lineGeometries?.mapNotNull { geo ->
                val geoCenterX = (geo.xmin + geo.xmax) / 2f
                if ((geoCenterX >= 0.5f) != isInRight) return@mapNotNull null
                OcrLineGeometry(
                    xmin = ((geo.xmin - xOffset) * 2f).coerceIn(0f, 1f),
                    ymin = (yOffset + geo.ymin * 0.5f).coerceIn(0f, 1f),
                    xmax = ((geo.xmax - xOffset) * 2f).coerceIn(0f, 1f),
                    ymax = (yOffset + geo.ymax * 0.5f).coerceIn(0f, 1f),
                    rotation = geo.rotation,
                )
            }

            block.copy(
                xmin = newXmin,
                ymin = newYmin,
                xmax = newXmax,
                ymax = newYmax,
                lineGeometries = newGeometries,
            )
        }
    }

    // ──────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────

    /** Remap block coords from one page's pixel space into a merged canvas normalized space. */
    private fun remapToCanvas(
        blocks: List<OcrTextBlock>,
        pageW: Float,
        pageH: Float,
        xOffsetPx: Float,
        yOffsetPx: Float,
        totalW: Float,
        totalH: Float,
    ): List<OcrTextBlock> {
        return blocks.mapNotNull { block ->
            // un-normalize to page pixels
            val pxMin = block.xmin * pageW
            val pyMin = block.ymin * pageH
            val pxMax = block.xmax * pageW
            val pyMax = block.ymax * pageH

            // shift to canvas position
            val cxMin = pxMin + xOffsetPx
            val cyMin = pyMin + yOffsetPx
            val cxMax = pxMax + xOffsetPx
            val cyMax = pyMax + yOffsetPx

            // normalize to canvas
            val newXmin = (cxMin / totalW).coerceIn(0f, 1f)
            val newYmin = (cyMin / totalH).coerceIn(0f, 1f)
            val newXmax = (cxMax / totalW).coerceIn(0f, 1f)
            val newYmax = (cyMax / totalH).coerceIn(0f, 1f)

            if (newXmax <= newXmin || newYmax <= newYmin) return@mapNotNull null

            val newGeometries = block.lineGeometries?.mapNotNull { geo ->
                val gpxMin = geo.xmin * pageW + xOffsetPx
                val gpyMin = geo.ymin * pageH + yOffsetPx
                val gpxMax = geo.xmax * pageW + xOffsetPx
                val gpyMax = geo.ymax * pageH + yOffsetPx
                val gnXmin = (gpxMin / totalW).coerceIn(0f, 1f)
                val gnYmin = (gpyMin / totalH).coerceIn(0f, 1f)
                val gnXmax = (gpxMax / totalW).coerceIn(0f, 1f)
                val gnYmax = (gpyMax / totalH).coerceIn(0f, 1f)
                if (gnXmax <= gnXmin || gnYmax <= gnYmin) return@mapNotNull null
                OcrLineGeometry(gnXmin, gnYmin, gnXmax, gnYmax, geo.rotation)
            }

            block.copy(
                xmin = newXmin, ymin = newYmin,
                xmax = newXmax, ymax = newYmax,
                lineGeometries = newGeometries,
            )
        }
    }

    /** Remap blocks so they align with a cropped sub-region of the original image. */
    fun remapToCrop(
        blocks: List<OcrTextBlock>,
        cropRect: RectF,
    ): List<OcrTextBlock> {
        val cropW = cropRect.width()
        val cropH = cropRect.height()
        if (cropW <= 0f || cropH <= 0f) return blocks

        return blocks.mapNotNull { block ->
            // Translate block into crop-relative coords and rescale 0→1
            val newXmin = ((block.xmin - cropRect.left) / cropW).coerceIn(0f, 1f)
            val newYmin = ((block.ymin - cropRect.top) / cropH).coerceIn(0f, 1f)
            val newXmax = ((block.xmax - cropRect.left) / cropW).coerceIn(0f, 1f)
            val newYmax = ((block.ymax - cropRect.top) / cropH).coerceIn(0f, 1f)

            // Discard blocks entirely outside the crop window
            if (newXmax <= 0f || newYmax <= 0f || newXmin >= 1f || newYmin >= 1f) {
                return@mapNotNull null
            }
            if (newXmax <= newXmin || newYmax <= newYmin) return@mapNotNull null

            val newGeometries = block.lineGeometries?.mapNotNull { geo ->
                val gnXmin = ((geo.xmin - cropRect.left) / cropW).coerceIn(0f, 1f)
                val gnYmin = ((geo.ymin - cropRect.top) / cropH).coerceIn(0f, 1f)
                val gnXmax = ((geo.xmax - cropRect.left) / cropW).coerceIn(0f, 1f)
                val gnYmax = ((geo.ymax - cropRect.top) / cropH).coerceIn(0f, 1f)
                if (gnXmax <= gnXmin || gnYmax <= gnYmin) return@mapNotNull null
                OcrLineGeometry(gnXmin, gnYmin, gnXmax, gnYmax, geo.rotation)
            }

            block.copy(
                xmin = newXmin, ymin = newYmin,
                xmax = newXmax, ymax = newYmax,
                lineGeometries = newGeometries,
            )
        }
    }

    // ──────────────────────────────────────────────────
    // Crop border detection (port of borders.cpp)
    // ──────────────────────────────────────────────────

    /**
     * Detect the crop rect that image-decoder would apply if cropBorders=true.
     *
     * Exact port of `borders.cpp / findBorders` from tachiyomi/image-decoder.
     * Constants from borders.h:
     *   thresholdForBlack = (255 * 0.75).toInt() = 191
     *   thresholdForWhite = 255 - 191 = 64
     *   filledRatioLimit  = 0.0025f
     *
     * Returns a normalized RectF (left, top, right, bottom) in [0,1] space,
     * or null if detection fails.
     */
    fun detectCropRect(stream: BufferedSource): RectF? {
        return try {
            // Peek so we don't consume the stream
            val bytes = stream.peek().readByteArray()
            val bitmap = OcrBitmapDecoder.decode(bytes, sampleSize = 2)
            try {
                findBorders(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "OcrCoordinateMapper: crop detection failed" }
            null
        }
    }

    // Constants from borders.h (THRESHOLD = 0.75)
    private const val THRESHOLD = 0.75f
    private val THRESHOLD_FOR_BLACK = (255f * THRESHOLD).toInt()        // 191
    private val THRESHOLD_FOR_WHITE = (255f - 255f * THRESHOLD).toInt() // 64
    private const val FILLED_RATIO_LIMIT = 0.0025f

    private fun isBlackPixel(pixels: IntArray, width: Int, x: Int, y: Int): Boolean {
        val argb = pixels[y * width + x]
        val gray = (argb shr 16) and 0xFF  // R channel of ARGB_8888
        return gray < THRESHOLD_FOR_BLACK
    }

    private fun isWhitePixel(pixels: IntArray, width: Int, x: Int, y: Int): Boolean {
        val argb = pixels[y * width + x]
        val gray = (argb shr 16) and 0xFF
        return gray > THRESHOLD_FOR_WHITE
    }

    /**
     * findBorders — port of borders.cpp's findBorders().
     * Returns a normalized RectF [0..1] that represents the *kept* region.
     */
    private fun findBorders(bitmap: Bitmap): RectF? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val top = findBorderTop(pixels, w, h)
        val bottom = findBorderBottom(pixels, w, h)
        val left = findBorderLeft(pixels, w, h, top, bottom)
        val right = findBorderRight(pixels, w, h, top, bottom)

        if (right <= left || bottom <= top) return null
        // Only return a real crop rect if it actually trims something
        if (left == 0 && top == 0 && right == w && bottom == h) return null

        return RectF(
            left.toFloat() / w,
            top.toFloat() / h,
            right.toFloat() / w,
            bottom.toFloat() / h,
        )
    }

    private fun findBorderTop(pixels: IntArray, width: Int, height: Int): Int {
        val filledLimit = (width * FILLED_RATIO_LIMIT / 2).roundToInt()

        var whitePixels = 0
        var blackPixels = 0
        for (x in 0 until width step 2) {
            if (isBlackPixel(pixels, width, x, 0)) blackPixels++
            else if (isWhitePixel(pixels, width, x, 0)) whitePixels++
        }

        if (blackPixels > filledLimit) {
            // Dark border → look for white content
            for (y in 1 until height) {
                var filledCount = 0
                for (x in 0 until width step 2) {
                    if (isWhitePixel(pixels, width, x, y)) filledCount++
                }
                if (filledCount > filledLimit) return y
            }
        } else {
            // White border → look for black content
            for (y in 1 until height) {
                var filledCount = 0
                for (x in 0 until width step 2) {
                    if (isBlackPixel(pixels, width, x, y)) filledCount++
                }
                if (filledCount > filledLimit) return y
            }
        }
        return 0
    }

    private fun findBorderBottom(pixels: IntArray, width: Int, height: Int): Int {
        val filledLimit = (width * FILLED_RATIO_LIMIT / 2).roundToInt()
        val lastY = height - 1

        var whitePixels = 0
        var blackPixels = 0
        for (x in 0 until width step 2) {
            if (isBlackPixel(pixels, width, x, lastY)) blackPixels++
            else if (isWhitePixel(pixels, width, x, lastY)) whitePixels++
        }

        if (blackPixels > filledLimit) {
            for (y in height - 2 downTo 1) {
                var filledCount = 0
                for (x in 0 until width step 2) {
                    if (isWhitePixel(pixels, width, x, y)) filledCount++
                }
                if (filledCount > filledLimit) return y + 1
            }
        } else {
            for (y in height - 2 downTo 1) {
                var filledCount = 0
                for (x in 0 until width step 2) {
                    if (isBlackPixel(pixels, width, x, y)) filledCount++
                }
                if (filledCount > filledLimit) return y + 1
            }
        }
        return height
    }

    private fun findBorderLeft(pixels: IntArray, width: Int, height: Int, top: Int, bottom: Int): Int {
        val totalRows = bottom - top
        if (totalRows <= 0) return 0
        val filledLimit = (totalRows * FILLED_RATIO_LIMIT / 2).roundToInt()

        var whitePixels = 0
        var blackPixels = 0
        for (y in top until bottom step 2) {
            if (isBlackPixel(pixels, width, 0, y)) blackPixels++
            else if (isWhitePixel(pixels, width, 0, y)) whitePixels++
        }

        if (blackPixels > filledLimit) {
            for (x in 1 until width) {
                var filledCount = 0
                for (y in top until bottom step 2) {
                    if (isWhitePixel(pixels, width, x, y)) filledCount++
                }
                if (filledCount > filledLimit) return x
            }
        } else {
            for (x in 1 until width) {
                var filledCount = 0
                for (y in top until bottom step 2) {
                    if (isBlackPixel(pixels, width, x, y)) filledCount++
                }
                if (filledCount > filledLimit) return x
            }
        }
        return 0
    }

    private fun findBorderRight(pixels: IntArray, width: Int, height: Int, top: Int, bottom: Int): Int {
        val totalRows = bottom - top
        if (totalRows <= 0) return width
        val filledLimit = (totalRows * FILLED_RATIO_LIMIT / 2).roundToInt()
        val lastX = width - 1

        var whitePixels = 0
        var blackPixels = 0
        for (y in top until bottom step 2) {
            if (isBlackPixel(pixels, width, lastX, y)) blackPixels++
            else if (isWhitePixel(pixels, width, lastX, y)) whitePixels++
        }

        if (blackPixels > filledLimit) {
            for (x in width - 2 downTo 1) {
                var filledCount = 0
                for (y in top until bottom step 2) {
                    if (isWhitePixel(pixels, width, x, y)) filledCount++
                }
                if (filledCount > filledLimit) return x + 1
            }
        } else {
            for (x in width - 2 downTo 1) {
                var filledCount = 0
                for (y in top until bottom step 2) {
                    if (isBlackPixel(pixels, width, x, y)) filledCount++
                }
                if (filledCount > filledLimit) return x + 1
            }
        }
        return width
    }
}
