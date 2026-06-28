package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.RectF
import okio.BufferedSource
import tachiyomi.decoder.ImageDecoder
import java.io.ByteArrayInputStream
import kotlin.math.max

/**
 * Coordinate remapping for OCR blocks under reader transformations.
 *
 * OCR blocks store normalized coordinates (0.0–1.0) relative to the **original** image.
 * When the reader applies transformations (crop borders, split, merge, webtoon splitAndMerge)
 * the displayed bitmap differs from the original, so block coordinates must be remapped.
 *
 * Crop rect is read directly from the native [ImageDecoder] which runs the C++ `borders.cpp`
 * algorithm — no Kotlin reimplementation needed.
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
            val inHalf = if (keepLeft) centerX <= 0.5f else centerX >= 0.5f
            if (!inHalf) return@mapNotNull null

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
     * Reads the crop rect directly from the native [ImageDecoder], then adjusts OCR coordinates
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
     * Remap OCR blocks after seam-augmented OCR.
     *
     * When page N's image is augmented with the top of page N+1 before OCR,
     * all coordinates are normalized to the taller augmented image. This function
     * rescales y-coordinates back into page N's original [0,1] space and clips
     * blocks that span the seam boundary.
     *
     * Blocks whose remapped ymax exceeds 1.0 are discarded (they belong to the
     * seam strip, which page N+1 will find on its own).
     *
     * @param blocks  OCR blocks normalized to the augmented image
     * @param originalHeight  pixel height of the unaugmented page
     * @param augmentedHeight  pixel height of the augmented image (original + seam)
     */
    fun remapSeamAugmented(
        blocks: List<OcrTextBlock>,
        originalHeight: Int,
        augmentedHeight: Int,
    ): List<OcrTextBlock> {
        if (originalHeight <= 0 || augmentedHeight <= originalHeight) return blocks
        val ratio = originalHeight.toFloat() / augmentedHeight

        return blocks.mapNotNull { block ->
            val newYmin = block.ymin / ratio
            val newYmax = block.ymax / ratio

            if (newYmax <= 0f || newYmin >= 1f) return@mapNotNull null

            val clippedYmin = newYmin.coerceIn(0f, 1f)
            val clippedYmax = newYmax.coerceIn(0f, 1f)
            if (clippedYmax <= clippedYmin) return@mapNotNull null

            val newGeometries = block.lineGeometries?.mapNotNull { geo ->
                val gnYmin = (geo.ymin / ratio).coerceIn(0f, 1f)
                val gnYmax = (geo.ymax / ratio).coerceIn(0f, 1f)
                if (gnYmax <= gnYmin) return@mapNotNull null
                geo.copy(ymin = gnYmin, ymax = gnYmax)
            }

            block.copy(
                ymin = clippedYmin,
                ymax = clippedYmax,
                lineGeometries = newGeometries,
            )
        }
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
        return blocks.mapNotNull { block ->
            val centerX = (block.xmin + block.xmax) / 2f
            val isInRight = centerX >= 0.5f

            val (xOffset, yOffset) = if (upperIsRight) {
                if (isInRight) Pair(0.5f, 0f) else Pair(0f, 0.5f)
            } else {
                if (isInRight) Pair(0.5f, 0.5f) else Pair(0f, 0f)
            }

            val newXmin = ((block.xmin - xOffset) * 2f).coerceIn(0f, 1f)
            val newXmax = ((block.xmax - xOffset) * 2f).coerceIn(0f, 1f)
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
            val pxMin = block.xmin * pageW
            val pyMin = block.ymin * pageH
            val pxMax = block.xmax * pageW
            val pyMax = block.ymax * pageH

            val cxMin = pxMin + xOffsetPx
            val cyMin = pyMin + yOffsetPx
            val cxMax = pxMax + xOffsetPx
            val cyMax = pyMax + yOffsetPx

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
            val newXmin = ((block.xmin - cropRect.left) / cropW).coerceIn(0f, 1f)
            val newYmin = ((block.ymin - cropRect.top) / cropH).coerceIn(0f, 1f)
            val newXmax = ((block.xmax - cropRect.left) / cropW).coerceIn(0f, 1f)
            val newYmax = ((block.ymax - cropRect.top) / cropH).coerceIn(0f, 1f)

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
    // Crop border detection (delegates to native ImageDecoder)
    // ──────────────────────────────────────────────────

    fun detectCropRect(stream: BufferedSource): RectF? {
        val decoder = try {
            val bytes = stream.peek().readByteArray()
            ImageDecoder.newInstance(ByteArrayInputStream(bytes), cropBorders = true, displayProfile = null)
        } catch (e: Exception) {
            null
        } ?: return null

        try {
            return detectCropRect(decoder)
        } finally {
            decoder.recycle()
        }
    }

    private fun detectCropRect(decoder: ImageDecoder): RectF? {
        if (decoder.cropX == 0 && decoder.cropY == 0 &&
            decoder.width == decoder.originalWidth &&
            decoder.height == decoder.originalHeight
        ) {
            return null
        }
        return RectF(
            decoder.cropX.toFloat() / decoder.originalWidth,
            decoder.cropY.toFloat() / decoder.originalHeight,
            (decoder.cropX + decoder.width).toFloat() / decoder.originalWidth,
            (decoder.cropY + decoder.height).toFloat() / decoder.originalHeight,
        )
    }
}
