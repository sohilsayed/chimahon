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
object OwOCRMerger {

    fun merge(engineLines: List<EngineLine>, config: MergeConfig): List<OcrResult> {
        if (!config.enabled || engineLines.isEmpty()) {
            return emptyList()
        }

        val useJapaneseLogic = config.language.isJapanese
        var lines = createLineDictionaries(engineLines, config)
        if (lines.isEmpty()) {
            return emptyList()
        }

        if (config.furiganaFilter && useJapaneseLogic) {
            lines = globalFuriganaFilter(lines, config)
        }

        val paragraphs = if (useJapaneseLogic) {
            createParagraphsFromLines(lines, config)
        } else {
            createParagraphsFromLinesLegacy(lines, config)
        }
        val merged = if (config.mergeCloseParagraphs) {
            if (useJapaneseLogic) {
                mergeCloseParagraphs(paragraphs, config)
            } else {
                mergeCloseParagraphsLegacy(paragraphs, config)
            }
        } else {
            paragraphs.map {
                it.paragraphObj
            }
        }
        val rows = groupParagraphsIntoRows(merged, useJapaneseLogic)
        val reordered = reorderParagraphsInRows(rows, useJapaneseLogic)
        val flattened = flattenRowsToParagraphs(reordered)
        return flattened
    }

    // ================================================================
    // STAGE 1: createLineDictionaries
    // ================================================================

    data class LineDict(
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
            // We shouldn't drop lines just because they lack JP text, they might be punctuation!
            // if (config.language == OcrLanguage.JAPANESE && !line.hasJpText) return@mapNotNull null
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
        val allParagraphs = mutableListOf<ParagraphWithMeta>()
        val used = BooleanArray(lines.size)
        
        for (i in lines.indices) {
            if (used[i]) continue
            val cluster = mutableListOf(lines[i])
            used[i] = true
            
            val queue = ArrayDeque<LineDict>()
            queue.add(lines[i])
            
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (j in lines.indices) {
                    if (!used[j]) {
                        val candidate = lines[j]
                        val isVertical = if (current.bbox.width * current.bbox.height > candidate.bbox.width * candidate.bbox.height) {
                            current.isVertical
                        } else {
                            candidate.isVertical
                        }
                        
                        if (shouldGroupInSameParagraph(current, candidate, isVertical, config)) {
                            cluster.add(candidate)
                            queue.add(candidate)
                            used[j] = true
                        }
                    }
                }
            }
            
            // Re-determine orientation based on largest box
            val isVertical = cluster.maxByOrNull { it.bbox.width * it.bbox.height }?.isVertical ?: true
            val isRtl = cluster.first().isRtl
            
            allParagraphs.add(createParagraphFromLines(cluster, isVertical, isRtl, config))
        }
        
        return allParagraphs
    }

    private fun createParagraphsFromLinesLegacy(lines: List<LineDict>, config: MergeConfig): List<ParagraphWithMeta> {
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
                shouldConnect = { a, b -> shouldGroupInSameParagraphLegacy(a, b, isVertical, config) },
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
                sweepPadding = 0.0,
            )

            for (component in components) {
                if (component.size > 1) {
                    val originalIndices = component.map { indices[it] }
                    val paragraphLines = originalIndices.map { lines[it] }
                    val para = createParagraphFromLinesLegacy(paragraphLines, isVertical, isRtl, config)
                    allParagraphs.add(para)
                    grouped.addAll(originalIndices)
                }
            }
        }

        groupLines(isVertical = true, isRtl = false)
        groupLines(isVertical = false, isRtl = true)
        groupLines(isVertical = false, isRtl = false)

        val ungrouped = lines.indices.filter { it !in grouped }.map { lines[it] }
        for (line in ungrouped) {
            allParagraphs.add(createParagraphFromLinesLegacy(listOf(line), line.isVertical, line.isRtl, config))
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

        val filtered = sorted

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

        val avgRotation = if (filtered.isNotEmpty()) {
            filtered.map { it.bbox.rotation }.average()
        } else 0.0

        val paraObj = OcrResult(
            text = textContent,
            tightBoundingBox = BoundingBox(x = left, y = top, width = right - left, height = bottom - top, rotation = Math.toDegrees(avgRotation)),
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

    private fun createParagraphFromLinesLegacy(
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
            return createParagraphFromLinesLegacy(sorted.take(1), isVertical, isRtl, config)
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

        // Sort by primary axis to enable sliding window
        val sorted = if (isVertical) {
            lines.sortedBy { it.bbox.top }
        } else {
            lines.sortedBy { it.bbox.left }
        }

        val merged = mutableListOf<LineDict>()
        val usedIndices = mutableSetOf<Int>()

        for (i in sorted.indices) {
            if (i in usedIndices) continue
            val mergeGroup = mutableListOf(sorted[i])
            usedIndices.add(i)
            var lastLine = sorted[i]

            // Only check lines that could possibly overlap
            for (j in (i + 1) until sorted.size) {
                if (j in usedIndices) continue
                val nextLine = sorted[j]

                // Optimization: Break early if the next line starts far past where this one ends
                // Lines that are far apart on the primary axis cannot be fragments of the same line.
                if (isVertical) {
                    if (nextLine.bbox.top - lastLine.bbox.bottom > 0.5 * lastLine.characterSize) break
                } else {
                    if (nextLine.bbox.left - lastLine.bbox.right > 0.5 * lastLine.characterSize) break
                }

                if (shouldMergeLines(lastLine, nextLine, isVertical)) {
                    mergeGroup.add(nextLine)
                    usedIndices.add(j)
                    lastLine = nextLine
                }
            }

            if (mergeGroup.size > 1) {
                merged.add(mergeMultipleLines(mergeGroup, isVertical))
            } else {
                merged.add(sorted[i])
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
        
        val imgW = config.imageWidth ?: 1000.0
        val imgH = config.imageHeight ?: 1000.0
        
        val w1 = line1.bbox.width * imgW
        val h1 = line1.bbox.height * imgH
        val w2 = line2.bbox.width * imgW
        val h2 = line2.bbox.height * imgH
        
        val charSize1 = if (line1.isVertical) w1 else h1
        val charSize2 = if (line2.isVertical) w2 else h2
        val characterSize = maxOf(charSize1, charSize2)
        
        val t1 = minOf(w1, h1)
        val t2 = minOf(w2, h2)
        
        var thicknessRatio = 1.0
        if (t1 > 0 && t2 > 0) {
            thicknessRatio = maxOf(t1, t2) / minOf(t1, t2)
        }
        
        var rotDiff = abs(line1.bbox.rotation - line2.bbox.rotation)
        while (rotDiff > Math.PI) rotDiff -= Math.PI
        if (rotDiff > Math.PI / 2) rotDiff = Math.PI - rotDiff
        val distToMultipleOfPiHalf = minOf(rotDiff, abs(Math.PI / 2 - rotDiff))
        if (distToMultipleOfPiHalf >= 0.1) return false

        val area1 = w1 * h1
        val area2 = w2 * h2
        val areaRatio = if (maxOf(area1, area2) > 0) minOf(area1, area2) / maxOf(area1, area2) else 1.0
        
        val isPunctuation = areaRatio < 0.10
        
        val maxHDistMult: Double
        val minDensityReq: Double
        if (isPunctuation) {
            maxHDistMult = 1.0
            minDensityReq = 0.40
        } else if (thicknessRatio > 1.8) {
            maxHDistMult = 0.50
            minDensityReq = 0.55
        } else {
            maxHDistMult = 0.75
            minDensityReq = 0.50
        }
        
        val furiganaHDistMult = if (thicknessRatio < 1.35 && !isPunctuation) 1.25 else 0.0
        
        val mergedLeft = minOf(line1.bbox.left, line2.bbox.left) * imgW
        val mergedTop = minOf(line1.bbox.top, line2.bbox.top) * imgH
        val mergedRight = maxOf(line1.bbox.right, line2.bbox.right) * imgW
        val mergedBottom = maxOf(line1.bbox.bottom, line2.bbox.bottom) * imgH
        val mergedArea = maxOf(0.0, mergedRight - mergedLeft) * maxOf(0.0, mergedBottom - mergedTop)
        
        if (mergedArea > 0 && (area1 + area2) / mergedArea < minDensityReq) {
            return false
        }
        
        if (isVertical) {
            val hDist = horizontalDistance(line1.bbox, line2.bbox) * imgW
            val lineWidth = (w1 + w2) / 2
            
            val yMinDiff = abs(line1.bbox.top - line2.bbox.top) * imgH
            val yOverlap = maxOf(0.0, minOf(line1.bbox.bottom, line2.bbox.bottom) * imgH - maxOf(line1.bbox.top, line2.bbox.top) * imgH)
            val minH = minOf(h1, h2)
            
            val yMaxDiff = abs(line1.bbox.bottom - line2.bbox.bottom) * imgH
            if (yMinDiff > characterSize * 2.0 && yMaxDiff > characterSize * 2.0) {
                return false // Staggered lines are probably separate bubbles
            }
            
            if (hDist < lineWidth * maxHDistMult) {
                if (yOverlap > minH * 0.5) {
                    return true
                }
            }
            
            if (furiganaHDistMult > 0 && hDist < lineWidth * furiganaHDistMult) {
                if (yMinDiff < characterSize * 0.3 && yOverlap > minH * 0.5) {
                    return true
                }
            }
            
            if (thicknessRatio < 2.5 && hDist < lineWidth * 1.0) {
                if (yMinDiff < characterSize * 0.5 && yOverlap > minH * 0.5) {
                    return true
                }
            }
            
            return false
        } else {
            val vDist = verticalDistance(line1.bbox, line2.bbox) * imgH
            val lineHeight = maxOf(h1, h2)
            if (vDist >= lineHeight * 1.5) return false
            
            val coord1 = line2.bbox.right * imgW
            val coord2 = line1.bbox.right * imgW
            if (abs(coord1 - coord2) < 1.5 * characterSize) return true
            
            if (config.supportCenterAlignedText && horizontalOverlap(line1.bbox, line2.bbox) > 0.9) return true
            
            return false
        }
    }

    private fun shouldGroupInSameParagraphLegacy(
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
            if (coord1 - coord2 < 2 * characterSize) return true

            if (config.supportCenterAlignedText && horizontalOverlap(line1.bbox, line2.bbox) > 0.9) return true
        }

        val filtered = furiganaFilter(listOf(line1, line2), isVertical)
        if (filtered.size == 1) {
            line1.isFurigana = true
            return true
        }

        return false
    }

    // ================================================================
    // STAGE 2d: globalFuriganaFilter (Hybrid Filter)
    // ================================================================

    private val KATAKANA_REGEX = Regex("[\u30A0-\u30FF]")
    private val KANJI_REGEX = Regex("[\u4E00-\u9FFF\u3400-\u4DBF]")

    private fun furiganaFilter(lines: List<LineDict>, isVertical: Boolean): List<LineDict> {
        val filtered = mutableListOf<LineDict>()
        for (i in lines.indices) {
            if (lines[i].isFurigana) continue
            if (i >= lines.size - 1) {
                filtered.add(lines[i])
                continue
            }
            val next = lines[i + 1]

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
            if (next.isFurigana) {
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

            val passedSize = if (isVertical) {
                lines[i].characterSize < next.characterSize * 0.85
            } else {
                curBbox.height < nextBbox.height * 0.85
            }

            if (!passedSize) {
                filtered.add(lines[i])
            }
        }
        return filtered
    }

    private fun globalFuriganaFilter(lines: List<LineDict>, config: MergeConfig): List<LineDict> {
        val n = lines.size
        val keep = BooleanArray(n) { true }

        val alphaRegex = Regex("[A-Za-z]")
        val digitRegex = Regex("[0-9]")
        
        val imgW = config.imageWidth ?: 1000.0
        val imgH = config.imageHeight ?: 1000.0

        for (j in 0 until n) {
            if (!keep[j]) continue

            val sub = lines[j]
            val subText = sub.text

            if (KANJI_REGEX.containsMatchIn(subText)) continue

            val hasAlpha = alphaRegex.containsMatchIn(subText)
            val hasKatakana = KATAKANA_REGEX.containsMatchIn(subText)
            if (hasAlpha && !hasKatakana) continue

            val hasDigit = digitRegex.containsMatchIn(subText)
            if (hasDigit && !hasKatakana && !hasAlpha) continue

            val subBbox = sub.bbox
            val subThickness = minOf(subBbox.width * imgW, subBbox.height * imgH)

            for (i in 0 until n) {
                if (i == j || !keep[i]) continue

                val main = lines[i]
                if (!KANJI_REGEX.containsMatchIn(main.text)) continue

                val mainBbox = main.bbox
                val mainThickness = minOf(mainBbox.width * imgW, mainBbox.height * imgH)

                if (subThickness > mainThickness * 0.75) continue

                val proximityLimit = mainThickness * 0.30
                val overlapRatio = 0.05

                // Vertical check
                val xOverlap = maxOf(0.0, minOf(subBbox.right, mainBbox.right) - maxOf(subBbox.left, mainBbox.left)) * imgW
                val xGapV = if (xOverlap > 0.0) {
                    0.0
                } else {
                    minOf(
                        abs(subBbox.left - mainBbox.right),
                        abs(subBbox.right - mainBbox.left),
                    ) * imgW
                }

                val oy = maxOf(0.0, minOf(subBbox.bottom, mainBbox.bottom) - maxOf(subBbox.top, mainBbox.top)) * imgH
                val h1 = maxOf(0.001, subBbox.height * imgH)
                val h2 = maxOf(0.001, mainBbox.height * imgH)
                val yOverlapV = oy / minOf(h1, h2)

                val isVerticalFurigana = xGapV < proximityLimit && yOverlapV > overlapRatio

                // Horizontal check
                val yOverlap = maxOf(0.0, minOf(subBbox.bottom, mainBbox.bottom) - maxOf(subBbox.top, mainBbox.top)) * imgH
                val yGapH = if (yOverlap > 0.0) {
                    0.0
                } else {
                    minOf(
                        abs(subBbox.top - mainBbox.bottom),
                        abs(subBbox.bottom - mainBbox.top),
                    ) * imgH
                }

                val ox = maxOf(0.0, minOf(subBbox.right, mainBbox.right) - maxOf(subBbox.left, mainBbox.left)) * imgW
                val w1 = maxOf(0.001, subBbox.width * imgW)
                val w2 = maxOf(0.001, mainBbox.width * imgW)
                val xOverlapH = ox / minOf(w1, w2)

                val isHorizontalFurigana = yGapH < proximityLimit && xOverlapH > overlapRatio

                var isFuri = false
                if (mainBbox.height * imgH > mainBbox.width * imgW * 1.2) {
                    if (isVerticalFurigana && mainBbox.centerX < subBbox.centerX) isFuri = true
                } else if (mainBbox.width * imgW > mainBbox.height * imgH * 1.2) {
                    if (isHorizontalFurigana && mainBbox.centerY > subBbox.centerY) isFuri = true
                } else {
                    if (isVerticalFurigana && mainBbox.centerX < subBbox.centerX) isFuri = true
                    if (isHorizontalFurigana && mainBbox.centerY > subBbox.centerY) isFuri = true
                }

                if (isFuri) {
                    keep[j] = false
                    break
                }
            }
        }

        return lines.filterIndexed { i, _ -> keep[i] }
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
                    var rotDiff = Math.toRadians(abs((a.paragraphObj.tightBoundingBox.rotation ?: 0.0) - (b.paragraphObj.tightBoundingBox.rotation ?: 0.0)))
                    while (rotDiff > Math.PI) rotDiff -= Math.PI
                    if (rotDiff > Math.PI / 2) rotDiff = Math.PI - rotDiff
                    val distToMultipleOfPiHalf = minOf(rotDiff, abs(Math.PI / 2 - rotDiff))
                    
                    if (distToMultipleOfPiHalf >= 0.1) return@findConnectedComponents false

                    if (isVertical) {
                        val widthRatio = minOf(a.paragraphObj.tightBoundingBox.width, b.paragraphObj.tightBoundingBox.width) / 
                                         maxOf(0.001, maxOf(a.paragraphObj.tightBoundingBox.width, b.paragraphObj.tightBoundingBox.width))
                        verticalDistance(a.paragraphObj.tightBoundingBox, b.paragraphObj.tightBoundingBox) <=
                            0.5 * charSize &&
                            widthRatio > 0.95 &&
                            horizontalOverlap(
                                a.paragraphObj.tightBoundingBox,
                                b.paragraphObj.tightBoundingBox,
                            ) >
                            0.95
                    } else {
                        val heightRatio = minOf(a.paragraphObj.tightBoundingBox.height, b.paragraphObj.tightBoundingBox.height) / 
                                          maxOf(0.001, maxOf(a.paragraphObj.tightBoundingBox.height, b.paragraphObj.tightBoundingBox.height))
                        a.writingDirection == b.writingDirection &&
                            horizontalDistance(
                                a.paragraphObj.tightBoundingBox,
                                b.paragraphObj.tightBoundingBox,
                            ) <=
                            2 * charSize &&
                            heightRatio > 0.6 &&
                            verticalOverlap(a.paragraphObj.tightBoundingBox, b.paragraphObj.tightBoundingBox) >
                            0.7
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

    private fun mergeCloseParagraphsLegacy(paragraphs: List<ParagraphWithMeta>, config: MergeConfig): List<OcrResult> {
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
                sweepPadding = 0.0,
            )

            for (component in components) {
                val origIndices = component.map { indices[it] }
                if (component.size == 1) {
                    merged.add(paragraphs[origIndices[0]].paragraphObj)
                } else {
                    val componentParas = origIndices.map { paragraphs[it] }
                    val allLineDicts = componentParas.flatMap { it.sourceLines }
                    val result = createParagraphFromLinesLegacy(
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
        BoundingBox(x = left, y = top, width = width, height = height, rotation = Math.toDegrees(rotation))

    // ================================================================
    // STAGE 4: groupParagraphsIntoRows
    // ================================================================

    data class Row(
        val paragraphs: List<OcrResult>,
        val isVerticalOrRtl: Boolean,
        val writingDirection: WritingDirection,
    )

    private fun groupParagraphsIntoRows(paragraphs: List<OcrResult>, useJapaneseLogic: Boolean): List<Row> {
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
            sweepPadding = if (useJapaneseLogic) 0.2 else 0.0,
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

    private fun reorderParagraphsInRows(rows: List<Row>, useJapaneseLogic: Boolean): List<Row> {
        return rows.map { row ->
            if (row.paragraphs.size < 2) return@map row
            val sorted = row.paragraphs.sortedBy { it.tightBoundingBox.x }
            val reordered = if (row.isVerticalOrRtl) sorted.reversed() else sorted
            val withMixedOrientation = if (useJapaneseLogic) {
                reorderMixedOrientationBlocks(reordered)
            } else {
                reorderMixedOrientationBlocksLegacy(reordered, row.isVerticalOrRtl)
            }
            row.copy(paragraphs = withMixedOrientation)
        }
    }

    private fun reorderMixedOrientationBlocks(paragraphs: List<OcrResult>): List<OcrResult> {
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

    private fun reorderMixedOrientationBlocksLegacy(paragraphs: List<OcrResult>, isVerticalOrRtl: Boolean): List<OcrResult> {
        if (paragraphs.size < 2) return paragraphs

        val result = mutableListOf<OcrResult>()
        val currentBlock = mutableListOf(paragraphs[0])
        var currentOrientation = paragraphs[0].forcedOrientation == "vertical"

        for (para in paragraphs.drop(1)) {
            val paraOrientation = para.forcedOrientation == "vertical"

            if (paraOrientation == currentOrientation) {
                currentBlock.add(para)
            } else {
                if (currentOrientation != isVerticalOrRtl) {
                    currentBlock.reverse()
                }
                result.addAll(currentBlock)
                currentBlock.clear()
                currentBlock.add(para)
                currentOrientation = paraOrientation
            }
        }

        if (currentOrientation != isVerticalOrRtl) {
            currentBlock.reverse()
        }
        result.addAll(currentBlock)

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
        sweepPadding: Double = 0.2,
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

            activeItems.retainAll { active ->
                if (isReverseSweep) {
                    active.end < currentStart - sweepPadding
                } else {
                    active.end > currentStart - sweepPadding
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
