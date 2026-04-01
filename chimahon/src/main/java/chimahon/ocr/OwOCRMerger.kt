package chimahon.ocr

import java.util.ArrayDeque
import java.util.Collections.emptyList
import kotlin.math.abs

/**
 * Port of owocr's paragraph reconstruction pipeline.
 * Replaces the UnionFind-based approach in [LensMerger] with connected-components + BFS.
 *
 * Usage:
 * ```
 * val engineLines = lensEngine.recognize(bytes, language)
 * val ocrResults = OwOCRMerger.merge(engineLines, config)
 * ```
 *
 * Outputs [OcrResult] (same type as [LensMerger]) .
 */
internal object OwOCRMerger {

    fun merge(engineLines: List<EngineLine>, config: MergeConfig): List<OcrResult> {
        if (!config.enabled || engineLines.isEmpty()) return emptyList()

        val lines = createLineDictionaries(engineLines, config)
        if (lines.isEmpty()) return emptyList()

        val paragraphs = createParagraphsFromLines(lines, config)
        val merged = if (config.mergeCloseParagraphs) {
            mergeCloseParagraphs(paragraphs, config)
        } else {
            paragraphs.map {
                it.paragraphObj
            }
        }
        val rows = groupParagraphsIntoRows(merged)
        val reordered = reorderParagraphsInRows(rows)
        val flattened = flattenRowsToParagraphs(reordered)
        return flattened
    }

    // ================================================================
    // STAGE 1: createLineDictionaries
    // ================================================================

    internal data class LineDict(
        val text: String,
        val bbox: NormalizedBBox,
        val isVertical: Boolean,
        val isRtl: Boolean,
        val characterSize: Double,
        val hasJpText: Boolean,
        val hasKanji: Boolean,
        val rotation: Double = 0.0,
        var isFurigana: Boolean = false,
        var paragraphId: Int? = null,
    )

    private fun createLineDictionaries(lines: List<EngineLine>, config: MergeConfig): List<LineDict> {
        return lines.mapNotNull { line ->
            if (line.text.isBlank()) return@mapNotNull null
            if (config.language == OcrLanguage.JAPANESE && !line.hasJpText) return@mapNotNull null
            if (config.language == OcrLanguage.CHINESE && !line.hasKanji) return@mapNotNull null

            val isVertical = line.writingDirection == WritingDirection.TTB
            val isRtl = line.writingDirection == WritingDirection.RTL ||
                ((!isVertical) && config.language.isRtl)

            LineDict(
                text = line.text,
                bbox = line.bbox,
                isVertical = isVertical,
                isRtl = isRtl,
                characterSize = line.characterSize,
                hasJpText = line.hasJpText,
                hasKanji = line.hasKanji,
                rotation = line.rotation,
            )
        }
    }

    // ================================================================
    // STAGE 2: createParagraphsFromLines
    // ================================================================

    internal data class ParagraphObj(
        val text: String,
        val bbox: NormalizedBBox,
        val writingDirection: WritingDirection,
        val lines: List<EngineLine>,
    )

    private fun createParagraphsFromLines(lines: List<LineDict>, config: MergeConfig): List<ParagraphWithMeta> {
        val grouped = mutableSetOf<Int>()
        val allParagraphs = mutableListOf<ParagraphWithMeta>()

        fun groupLines(isVertical: Boolean, isRtl: Boolean) {
            val indices = lines.indices.filter { i ->
                lines[i].isVertical == isVertical &&
                    lines[i].isRtl == isRtl &&
                    i !in grouped
            }
            if (indices.size < 2) return

            val components = findConnectedComponents(
                items = indices.map { lines[it] },
                shouldConnect = { a, b -> shouldGroupInSameParagraph(a, b, isVertical, config) },
                getStartCoord = if (isVertical) {
                    { it -> it.bbox.top }
                } else if (isRtl) {
                    { it -> it.bbox.right }
                } else {
                    { it -> it.bbox.left }
                },
                getEndCoord = if (isVertical) {
                    { it -> it.bbox.bottom }
                } else if (isRtl) {
                    { it -> it.bbox.left }
                } else {
                    { it -> it.bbox.right }
                },
            )

            for (component in components) {
                if (component.size > 1) {
                    val originalIndices = component.map { indices[it] }
                    val paragraphLines = originalIndices.map { lines[it] }
                    val para = createParagraphFromLines(paragraphLines, isVertical, isRtl, config)
                    allParagraphs.add(para)
                    grouped.addAll(originalIndices)
                }
            }
        }

        groupLines(isVertical = true, isRtl = false)
        groupLines(isVertical = false, isRtl = true)
        groupLines(isVertical = false, isRtl = false)

        // Ungrouped lines become single-line paragraphs
        val ungrouped = lines.indices.filter { it !in grouped }.map { lines[it] }
        for (line in ungrouped) {
            allParagraphs.add(createParagraphFromLines(listOf(line), line.isVertical, line.isRtl, config))
        }

        return allParagraphs
    }

    // ================================================================
    // STAGE 2a: createParagraphFromLines
    // ================================================================

    data class ParagraphWithMeta(
        val paragraphObj: OcrResult,
        val writingDirection: WritingDirection,
        val characterSize: Double,
        val isVertical: Boolean,
        val sourceLines: List<LineDict>,
    )

    private fun createParagraphFromLines(
        lines: List<LineDict>,
        isVertical: Boolean,
        isRtl: Boolean,
        config: MergeConfig,
    ): ParagraphWithMeta {
        val sorted = if (isVertical) {
            lines.sortedByDescending { it.bbox.right }
        } else {
            lines.sortedBy { it.bbox.top }
        }

        val mergedFragments = mergeOverlappingLines(sorted, isVertical)
        val filtered = if (lines.none { it.paragraphId != null } && config.furiganaFilter) {
            furiganaFilter(mergedFragments, isVertical)
        } else {
            mergedFragments
        }

        if (filtered.isEmpty()) {
            return createParagraphFromLines(sorted.take(1), isVertical, isRtl, config)
        }

        val left = filtered.minOf { it.bbox.left }
        val right = filtered.maxOf { it.bbox.right }
        val top = filtered.minOf { it.bbox.top }
        val bottom = filtered.maxOf { it.bbox.bottom }

        val textContent = buildString {
            for ((idx, line) in filtered.withIndex()) {
                if (idx > 0) {
                    append('\n')
                }
                append(OcrPreprocessor.clean(line.text))
            }
        }

        val forcedOrientation = if (isVertical) "vertical" else "horizontal"
        val writingDirection = when {
            isVertical -> WritingDirection.TTB
            isRtl -> WritingDirection.RTL
            else -> WritingDirection.LTR
        }

        val paraObj = OcrResult(
            text = textContent,
            tightBoundingBox = BoundingBox(x = left, y = top, width = right - left, height = bottom - top),
            isMerged = filtered.size > 1,
            forcedOrientation = forcedOrientation,
            constituentBoxes = filtered.map { it.bbox.toBoundingBox() },
        )

        val largestLineCharSize = if (isVertical) {
            filtered.maxByOrNull { it.bbox.width }?.characterSize ?: 0.0
        } else {
            filtered.maxByOrNull { it.bbox.height }?.characterSize ?: 0.0
        }

        return ParagraphWithMeta(
            paragraphObj = paraObj,
            writingDirection = writingDirection,
            characterSize = largestLineCharSize,
            isVertical = isVertical,
            sourceLines = filtered,
        )
    }

    // ================================================================
    // STAGE 2b: mergeOverlappingLines (broken fragment fix)
    // ================================================================

    private fun mergeOverlappingLines(lines: List<LineDict>, isVertical: Boolean): List<LineDict> {
        if (lines.size < 2) return lines

        val merged = mutableListOf<LineDict>()
        val usedIndices = mutableSetOf<Int>()

        for (i in lines.indices) {
            if (i in usedIndices) continue
            val mergeGroup = mutableListOf(lines[i])
            usedIndices.add(i)
            var lastLine = lines[i]

            for (j in (i + 1) until lines.size) {
                if (j in usedIndices) continue
                if (shouldMergeLines(lastLine, lines[j], isVertical)) {
                    mergeGroup.add(lines[j])
                    usedIndices.add(j)
                    lastLine = lines[j]
                }
            }

            if (mergeGroup.size > 1) {
                merged.add(mergeMultipleLines(mergeGroup, isVertical))
            } else {
                merged.add(lines[i])
            }
        }
        return merged
    }

    private fun shouldMergeLines(a: LineDict, b: LineDict, isVertical: Boolean): Boolean {
        return if (isVertical) {
            horizontalOverlap(a.bbox, b.bbox) > 0.8 && verticalOverlap(a.bbox, b.bbox) < 0.4
        } else {
            verticalOverlap(a.bbox, b.bbox) > 0.8 && horizontalOverlap(a.bbox, b.bbox) < 0.4
        }
    }

    private fun mergeMultipleLines(lines: List<LineDict>, isVertical: Boolean): LineDict {
        val sorted = if (isVertical) {
            lines.sortedBy { it.bbox.centerY }
        } else {
            lines.sortedBy { it.bbox.centerX }
        }

        val mergedText = OcrPreprocessor.clean(sorted.joinToString("") { it.text })
        val left = sorted.minOf { it.bbox.left }
        val right = sorted.maxOf { it.bbox.right }
        val top = sorted.minOf { it.bbox.top }
        val bottom = sorted.maxOf { it.bbox.bottom }
        val totalDim = sorted.sumOf {
            if (isVertical) it.bbox.height else it.bbox.width
        }
        val charSize = totalDim / mergedText.length.coerceAtLeast(1)

        return LineDict(
            text = mergedText,
            bbox = NormalizedBBox(left, top, right, bottom),
            isVertical = isVertical,
            isRtl = lines[0].isRtl,
            characterSize = charSize,
            hasJpText = sorted.any { it.hasJpText },
            hasKanji = sorted.any { it.hasKanji },
            rotation = sorted[0].rotation, // Take rotation from first line in group
        )
    }

    // ================================================================
    // STAGE 2c: shouldGroupInSameParagraph
    // ================================================================

    private fun shouldGroupInSameParagraph(
        line1: LineDict,
        line2: LineDict,
        isVertical: Boolean,
        config: MergeConfig,
    ): Boolean {
        val characterSize = maxOf(line1.characterSize, line2.characterSize)

        if (isVertical) {
            val hDist = horizontalDistance(line1.bbox, line2.bbox)
            val lineWidth = maxOf(line1.bbox.width, line2.bbox.width)
            if (hDist >= lineWidth) return false
            if (abs(line1.bbox.top - line2.bbox.top) < characterSize) return true
        } else {
            val vDist = verticalDistance(line1.bbox, line2.bbox)
            val lineHeight = maxOf(line1.bbox.height, line2.bbox.height)
            if (vDist >= lineHeight * 2) return false

            val coord1 = if (line1.isRtl) line2.bbox.right else line1.bbox.left
            val coord2 = if (line1.isRtl) line1.bbox.right else line2.bbox.left
            if (abs(coord1 - coord2) < 2 * characterSize) return true

            if (config.supportCenterAlignedText && horizontalOverlap(line1.bbox, line2.bbox) > 0.9) return true
        }

        // Furigana fallback
        val filtered = furiganaFilter(listOf(line1, line2), isVertical)
        if (filtered.size == 1) {
            line1.isFurigana = true
            return true
        }

        return false
    }

    // ================================================================
    // STAGE 2d: furiganaFilter
    // ================================================================

    private fun furiganaFilter(lines: List<LineDict>, isVertical: Boolean): List<LineDict> {
        val filtered = mutableListOf<LineDict>()
        for (i in lines.indices) {
            if (lines[i].isFurigana) continue
            if (i >= lines.size - 1) {
                filtered.add(lines[i])
                continue
            }
            val next = lines[i + 1]

            // Content check
            if (!(lines[i].hasJpText && next.hasJpText)) {
                filtered.add(lines[i])
                continue
            }
            if (lines[i].hasKanji) {
                filtered.add(lines[i])
                continue
            }
            if (!next.hasKanji) {
                filtered.add(lines[i])
                continue
            }

            val curBbox = lines[i].bbox
            val nextBbox = next.bbox

            val passedPosition = if (isVertical) {
                val minHDist = abs(nextBbox.width - curBbox.width) / 2
                val maxHDist = nextBbox.width + curBbox.width / 2
                val hDist = curBbox.centerX - nextBbox.centerX
                val vOverlap = verticalOverlap(curBbox, nextBbox)
                hDist > minHDist && hDist < maxHDist && vOverlap > 0.4
            } else {
                val minVDist = abs(nextBbox.height - curBbox.height) / 2
                val maxVDist = nextBbox.height + curBbox.height / 2
                val vDist = nextBbox.centerY - curBbox.centerY
                val hOverlap = horizontalOverlap(curBbox, nextBbox)
                vDist > minVDist && vDist < maxVDist && hOverlap > 0.4
            }

            if (!passedPosition) {
                filtered.add(lines[i])
                continue
            }

            // Size check (< 85%)
            val passedSize = if (isVertical) {
                lines[i].characterSize < next.characterSize * 0.85
            } else {
                curBbox.height < nextBbox.height * 0.85
            }

            if (!passedSize) {
                filtered.add(lines[i])
                continue
            }
            // else: furigana detected, skip
        }
        return filtered
    }

    // ================================================================
    // STAGE 3: mergeCloseParagraphs
    // ================================================================

    private fun mergeCloseParagraphs(paragraphs: List<ParagraphWithMeta>, config: MergeConfig): List<OcrResult> {
        if (paragraphs.size < 2) return paragraphs.map { it.paragraphObj }

        val merged = mutableListOf<OcrResult>()

        fun mergeOrientation(isVertical: Boolean) {
            val indices = paragraphs.indices.filter {
                (paragraphs[it].writingDirection == WritingDirection.TTB) == isVertical
            }
            if (indices.size <= 1) {
                for (i in indices) merged.add(paragraphs[i].paragraphObj)
                return
            }

            val components = findConnectedComponents(
                items = indices.map { paragraphs[it] },
                shouldConnect = { a, b ->
                    val charSize = maxOf(a.characterSize, b.characterSize)
                    if (isVertical) {
                        verticalDistance(a.paragraphObj.tightBoundingBox, b.paragraphObj.tightBoundingBox) <=
                            2 * charSize &&
                            horizontalOverlap(
                                a.paragraphObj.tightBoundingBox,
                                b.paragraphObj.tightBoundingBox,
                            ) >
                            0.9
                    } else {
                        a.writingDirection == b.writingDirection &&
                            horizontalDistance(
                                a.paragraphObj.tightBoundingBox,
                                b.paragraphObj.tightBoundingBox,
                            ) <=
                            3 * charSize &&
                            verticalOverlap(a.paragraphObj.tightBoundingBox, b.paragraphObj.tightBoundingBox) >
                            0.9
                    }
                },
                getStartCoord = if (isVertical) {
                    { it -> it.paragraphObj.tightBoundingBox.x }
                } else {
                    { it -> it.paragraphObj.tightBoundingBox.y }
                },
                getEndCoord = if (isVertical) {
                    { it ->
                        it.paragraphObj.tightBoundingBox.x +
                            it.paragraphObj.tightBoundingBox.width
                    }
                } else {
                    { it -> it.paragraphObj.tightBoundingBox.y + it.paragraphObj.tightBoundingBox.height }
                },
            )

            for (component in components) {
                val origIndices = component.map { indices[it] }
                if (component.size == 1) {
                    merged.add(paragraphs[origIndices[0]].paragraphObj)
                } else {
                    val componentParas = origIndices.map { paragraphs[it] }
                    // Use original source lines to preserve their individual bboxes
                    val allLineDicts = componentParas.flatMap { it.sourceLines }
                    val result = createParagraphFromLines(
                        allLineDicts,
                        isVertical,
                        componentParas.first().writingDirection == WritingDirection.RTL,
                        MergeConfig(furiganaFilter = false, language = config.language),
                    )
                    merged.add(result.paragraphObj)
                }
            }
        }

        mergeOrientation(isVertical = true)
        mergeOrientation(isVertical = false)
        return merged
    }

    private fun BoundingBox.toNormalized(): NormalizedBBox =
        NormalizedBBox(x, y, x + width, y + height)
    
    private fun NormalizedBBox.toBoundingBox(): BoundingBox =
        BoundingBox(x = left, y = top, width = width, height = height, rotation = rotation)

    // ================================================================
    // STAGE 4: groupParagraphsIntoRows
    // ================================================================

    data class Row(
        val paragraphs: List<OcrResult>,
        val isVerticalOrRtl: Boolean,
        val writingDirection: WritingDirection,
    )

    private fun groupParagraphsIntoRows(paragraphs: List<OcrResult>): List<Row> {
        if (paragraphs.isEmpty()) return emptyList()
        if (paragraphs.size == 1) {
            return listOf(
                Row(paragraphs, false, WritingDirection.LTR),
            )
        }

        // We need writing direction info from the paragraphs.
        // Derive from forcedOrientation since OcrResult doesn't carry WritingDirection directly.
        val paragraphDirections = paragraphs.map { p ->
            when (p.forcedOrientation) {
                "vertical" -> WritingDirection.TTB
                "rtl" -> WritingDirection.RTL
                else -> WritingDirection.LTR
            }
        }

        val components = findConnectedComponents(
            items = paragraphs,
            shouldConnect = { a, b ->
                val aBottom = a.tightBoundingBox.y + a.tightBoundingBox.height
                val bBottom = b.tightBoundingBox.y + b.tightBoundingBox.height
                val overlapTop = maxOf(a.tightBoundingBox.y, b.tightBoundingBox.y)
                val overlapBottom = minOf(aBottom, bBottom)
                if (overlapBottom <= overlapTop) {
                    false
                } else {
                    val overlapHeight = overlapBottom - overlapTop
                    val smallerHeight = minOf(a.tightBoundingBox.height, b.tightBoundingBox.height)
                    (overlapHeight / smallerHeight.coerceAtLeast(0.001)) > 0.2
                }
            },
            getStartCoord = { it.tightBoundingBox.y },
            getEndCoord = { it.tightBoundingBox.y + it.tightBoundingBox.height },
        )

        return components.map { component ->
            val rowParas = component.map { paragraphs[it] }
            val rowDirs = component.map { paragraphDirections[it] }
            val isVerticalOrRtl = rowDirs.count { it != WritingDirection.LTR } * 2 >= rowDirs.size
            Row(rowParas, isVerticalOrRtl, if (isVerticalOrRtl) WritingDirection.TTB else WritingDirection.LTR)
        }
    }

    // ================================================================
    // STAGE 5: reorderParagraphsInRows
    // ================================================================

    private fun reorderParagraphsInRows(rows: List<Row>): List<Row> {
        return rows.map { row ->
            if (row.paragraphs.size < 2) return@map row
            val sorted = row.paragraphs.sortedBy { it.tightBoundingBox.x }
            val reordered = if (row.isVerticalOrRtl) sorted.reversed() else sorted
            val withMixedOrientation = reorderMixedOrientationBlocks(reordered, row.isVerticalOrRtl)
            row.copy(paragraphs = withMixedOrientation)
        }
    }

    private fun reorderMixedOrientationBlocks(paragraphs: List<OcrResult>, isVerticalOrRtl: Boolean): List<OcrResult> {
        if (paragraphs.size < 2) return paragraphs

        var result = paragraphs
        var madeProgress = true

        while (madeProgress) {
            madeProgress = false
            for (i in 0 until result.size - 1) {
                val p1 = result[i]
                val p2 = result[i + 1]

                val p1Center = if (p1.forcedOrientation == "vertical") p1.tightBoundingBox.y else p1.tightBoundingBox.x
                val p2Center = if (p2.forcedOrientation == "vertical") p2.tightBoundingBox.y else p2.tightBoundingBox.x
                val p1End = if (p1.forcedOrientation ==
                    "vertical"
                ) {
                    p1.tightBoundingBox.y + p1.tightBoundingBox.height
                } else {
                    p1.tightBoundingBox.x +
                        p1.tightBoundingBox.width
                }
                val p2End = if (p2.forcedOrientation ==
                    "vertical"
                ) {
                    p2.tightBoundingBox.y + p2.tightBoundingBox.height
                } else {
                    p2.tightBoundingBox.x +
                        p2.tightBoundingBox.width
                }

                val p1IsVertical = p1.forcedOrientation == "vertical"
                val p2IsVertical = p2.forcedOrientation == "vertical"

                if (p1IsVertical != p2IsVertical) {
                    if (p2IsVertical && p2Center < p1Center + (p1End - p1Center) / 2) {
                        result = result.toMutableList().apply {
                            removeAt(i + 1)
                            add(i, p2)
                        }
                        madeProgress = true
                    } else if (!p2IsVertical && p1Center > p2Center + (p2End - p2Center) / 2) {
                        result = result.toMutableList().apply {
                            removeAt(i + 1)
                            add(i, p2)
                        }
                        madeProgress = true
                    }
                }
            }
        }

        return result
    }

    // ================================================================
    // STAGE 6: flattenRowsToParagraphs
    // ================================================================

    private fun flattenRowsToParagraphs(rows: List<Row>): List<OcrResult> {
        return rows.sortedBy { row ->
            row.paragraphs.minOf { it.tightBoundingBox.y }
        }.flatMap { it.paragraphs }
    }

    // ================================================================
    // UTILITY: findConnectedComponents (sweep-line + BFS)
    // ================================================================

    internal fun <T> findConnectedComponents(
        items: List<T>,
        shouldConnect: (T, T) -> Boolean,
        getStartCoord: (T) -> Double,
        getEndCoord: (T) -> Double,
    ): List<List<Int>> {
        if (items.isEmpty()) return emptyList()
        if (items.size == 1) return listOf(listOf(0))

        val graph = Array(items.size) { mutableListOf<Int>() }

        val firstStart = getStartCoord(items[0])
        val firstEnd = getEndCoord(items[0])
        val isReverseSweep = firstStart > firstEnd

        val sortedItems = items.indices.map { i -> i to items[i] }
            .sortedBy { (_, item) -> getStartCoord(item) }
            .let { if (isReverseSweep) it.reversed() else it }

        data class ActiveItem(val index: Int, val item: T, val end: Double)

        val activeItems = mutableListOf<ActiveItem>()

        for ((originalIdx, item) in sortedItems) {
            val currentStart = getStartCoord(item)
            val lineEnd = getEndCoord(item)

            // Prune items that no longer overlap on sweep axis
            activeItems.retainAll { active ->
                if (isReverseSweep) {
                    active.end < currentStart
                } else {
                    active.end > currentStart
                }
            }

            // Check against all active items
            for (active in activeItems) {
                if (shouldConnect(item, active.item)) {
                    graph[originalIdx].add(active.index)
                    graph[active.index].add(originalIdx)
                }
            }

            activeItems.add(ActiveItem(originalIdx, item, lineEnd))
        }

        // BFS to find connected components
        val visited = BooleanArray(items.size)
        val components = mutableListOf<List<Int>>()

        for (i in items.indices) {
            if (!visited[i]) {
                val component = mutableListOf<Int>()
                val queue = ArrayDeque<Int>()
                queue.add(i)
                visited[i] = true
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    component.add(node)
                    for (neighbor in graph[node]) {
                        if (!visited[neighbor]) {
                            visited[neighbor] = true
                            queue.add(neighbor)
                        }
                    }
                }
                components.add(component)
            }
        }

        return components
    }

    // ================================================================
    // UTILITY: Overlap & Distance functions (NormalizedBBox)
    // ================================================================

    private fun horizontalOverlap(a: NormalizedBBox, b: NormalizedBBox): Double {
        val overlapLeft = maxOf(a.left, b.left)
        val overlapRight = minOf(a.right, b.right)
        if (overlapRight <= overlapLeft) return 0.0
        val overlapWidth = overlapRight - overlapLeft
        val smallerWidth = minOf(a.width, b.width)
        return if (smallerWidth > 0) overlapWidth / smallerWidth else 0.0
    }

    private fun verticalOverlap(a: NormalizedBBox, b: NormalizedBBox): Double {
        val overlapTop = maxOf(a.top, b.top)
        val overlapBottom = minOf(a.bottom, b.bottom)
        if (overlapBottom <= overlapTop) return 0.0
        val overlapHeight = overlapBottom - overlapTop
        val smallerHeight = minOf(a.height, b.height)
        return if (smallerHeight > 0) overlapHeight / smallerHeight else 0.0
    }

    private fun horizontalDistance(a: NormalizedBBox, b: NormalizedBBox): Double {
        return when {
            a.right < b.left -> b.left - a.right
            b.right < a.left -> a.left - b.right
            else -> 0.0
        }
    }

    private fun verticalDistance(a: NormalizedBBox, b: NormalizedBBox): Double {
        return when {
            a.bottom < b.top -> b.top - a.bottom
            b.bottom < a.top -> a.top - b.bottom
            else -> 0.0
        }
    }

    // ================================================================
    // UTILITY: Overlap & Distance functions (BoundingBox overloads)
    // ================================================================

    private fun horizontalOverlap(a: BoundingBox, b: BoundingBox): Double {
        val aNorm = a.toNormalized()
        val bNorm = b.toNormalized()
        return horizontalOverlap(aNorm, bNorm)
    }

    private fun verticalOverlap(a: BoundingBox, b: BoundingBox): Double {
        val aNorm = a.toNormalized()
        val bNorm = b.toNormalized()
        return verticalOverlap(aNorm, bNorm)
    }

    private fun horizontalDistance(a: BoundingBox, b: BoundingBox): Double {
        val aNorm = a.toNormalized()
        val bNorm = b.toNormalized()
        return horizontalDistance(aNorm, bNorm)
    }

    private fun verticalDistance(a: BoundingBox, b: BoundingBox): Double {
        val aNorm = a.toNormalized()
        val bNorm = b.toNormalized()
        return verticalDistance(aNorm, bNorm)
    }
}
