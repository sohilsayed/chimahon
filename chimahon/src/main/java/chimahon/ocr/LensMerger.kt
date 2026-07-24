package chimahon.ocr

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val JAPANESE_REGEX = Regex("[\u3040-\u309F\u30A0-\u30FF\u3400-\u9FFF]")
private val LINE_NOISE_REGEX = Regex("^[|—_ノヘく/\\\\:;]$")
private val KANJI_REGEX = Regex("[\u4E00-\u9FFF\u3400-\u4DBF]")
private val KATAKANA_REGEX = Regex("[\u30A0-\u30FF]")

internal object LensMerger {

    private fun postProcessText(text: String, language: OcrLanguage): String =
        if (language.prefersNoSpace) text.replace(Regex("\\s+"), "") else text

    fun flattenToPixelLines(
        lensResult: LensResult,
        imageWidth: Int,
        imageHeight: Int,
        language: OcrLanguage,
    ): List<OcrResult> {
        val lines = mutableListOf<OcrResult>()
        for (paragraph in lensResult.paragraphs) {
            for (line in paragraph.lines) {
                val geometry = line.geometry ?: continue
                val cleanText = OcrPreprocessor.clean(postProcessText(line.text, language))
                if (cleanText.isBlank()) continue

                val rotation = geometry.rotationZ.toDouble()
                val cx = geometry.centerX.toDouble() * imageWidth
                val cy = geometry.centerY.toDouble() * imageHeight
                val w = geometry.width.toDouble() * imageWidth
                val h = geometry.height.toDouble() * imageHeight
                val hw = w / 2.0
                val hh = h / 2.0

                // Orientation detection (mirrors logic.rs)
                val isVertical = if (language.prefersVertical) {
                    if (abs(rotation) > 0.1) {
                        abs(abs(rotation) - PI / 2) < 0.5
                    } else {
                        w <= h
                    }
                } else {
                    false
                }

                val rotationDeg = Math.toDegrees(rotation)
                lines.add(
                    OcrResult(
                        text = cleanText,
                        tightBoundingBox = BoundingBox(
                            x = cx - hw,
                            y = cy - hh,
                            width = w,
                            height = h,
                            rotation = rotationDeg,
                        ),
                        isMerged = false,
                        forcedOrientation = if (isVertical) "vertical" else "horizontal",
                    ),
                )
            }
        }
        return lines
    }

    private data class Point(val x: Double, val y: Double)

    private fun getBoundingBoxCorners(bbox: BoundingBox): List<Point> {
        val cx = bbox.x + bbox.width / 2.0
        val cy = bbox.y + bbox.height / 2.0
        val hw = bbox.width / 2.0
        val hh = bbox.height / 2.0
        val rotation = bbox.rotation ?: 0.0
        val cosA = cos(rotation)
        val sinA = sin(rotation)
        return listOf(Point(-hw, -hh), Point(hw, -hh), Point(hw, hh), Point(-hw, hh))
            .map { p -> Point(p.x * cosA - p.y * sinA + cx, p.x * sinA + p.y * cosA + cy) }
    }

    private fun calculateAabb(points: List<Point>): DoubleArray {
        if (points.isEmpty()) return doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0)
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val w = maxX - minX
        val h = maxY - minY
        return doubleArrayOf(minX + w / 2.0, minY + h / 2.0, w, h, 0.0)
    }

    private fun filterBadBoxes(
        lines: List<OcrResult>,
        pageW: Int,
        pageH: Int,
        config: MergeConfig,
    ): List<OcrResult> {
        val n = lines.size
        val keep = BooleanArray(n) { true }
        val pageArea = pageW.toDouble() * pageH.toDouble()

        // 1. Noise & SFX filter
        for (i in 0 until n) {
            val text = lines[i].text.trim()
            val textLen = text.length
            val boxArea = lines[i].tightBoundingBox.width * lines[i].tightBoundingBox.height

            if (textLen == 1) {
                val ch = text[0]
                // is_ascii_punctuation || is_ascii_digit
                if (ch.code in 33..126 && !ch.isLetter()) {
                    keep[i] = false
                    continue
                }
                if (LINE_NOISE_REGEX.containsMatchIn(text)) {
                    keep[i] = false
                    continue
                }
            }

            if (boxArea < pageArea * 0.0005 &&
                (!config.language.prefersVertical || !JAPANESE_REGEX.containsMatchIn(text))
            ) {
                keep[i] = false
                continue
            }

            if (boxArea > pageArea * 0.30 && textLen < 6) {
                keep[i] = false
                continue
            }
        }

        // 2. Overlap / ghost detection
        for (i in 0 until n) {
            if (!keep[i]) continue
            for (j in 0 until n) {
                if (i == j || !keep[j]) continue
                val a = lines[i].tightBoundingBox
                val b = lines[j].tightBoundingBox
                val xOverlap = minOf(a.x + a.width, b.x + b.width) - maxOf(a.x, b.x)
                val yOverlap = minOf(a.y + a.height, b.y + b.height) - maxOf(a.y, b.y)
                if (xOverlap > 0.0 && yOverlap > 0.0) {
                    val intersectionArea = xOverlap * yOverlap
                    val bArea = b.width * b.height
                    if (intersectionArea > bArea * 0.3) {
                        val aArea = a.width * a.height
                        if (aArea > bArea * 3.0 && intersectionArea > bArea * 0.8) {
                            if (config.language.prefersVertical &&
                                JAPANESE_REGEX.containsMatchIn(lines[j].text) &&
                                !lines[i].text.contains(lines[j].text)
                            ) {
                                continue
                            }
                            keep[j] = false
                        }
                    }
                }
            }
        }

        // 3. Furigana check (Japanese only)
        if (config.language.isJapanese) {
            for (i in 0 until n) {
                if (!keep[i]) continue
                for (j in 0 until n) {
                    if (i == j || !keep[j]) continue
                    val main = lines[i]
                    val sub = lines[j]
                    if (!KANJI_REGEX.containsMatchIn(main.text)) continue
                    val mainThickness = minOf(main.tightBoundingBox.width, main.tightBoundingBox.height)
                    val subThickness = minOf(sub.tightBoundingBox.width, sub.tightBoundingBox.height)
                    if (KANJI_REGEX.containsMatchIn(sub.text) || KATAKANA_REGEX.containsMatchIn(sub.text)) continue
                    if (subThickness > mainThickness * 0.60) continue
                    val proximityLimit = mainThickness * 0.5
                    // Vertical furigana: sub is to the right of main
                    val xGapV = sub.tightBoundingBox.x - (main.tightBoundingBox.x + main.tightBoundingBox.width)
                    val yOverlapV =
                        minOf(
                            main.tightBoundingBox.y + main.tightBoundingBox.height,
                            sub.tightBoundingBox.y + sub.tightBoundingBox.height,
                        ) -
                            maxOf(main.tightBoundingBox.y, sub.tightBoundingBox.y)
                    val isVerticalFurigana = xGapV > -mainThickness * 0.5 && xGapV < proximityLimit && yOverlapV > 0.0
                    // Horizontal furigana: sub is above main
                    val yGapH = main.tightBoundingBox.y - (sub.tightBoundingBox.y + sub.tightBoundingBox.height)
                    val xOverlapH =
                        minOf(
                            main.tightBoundingBox.x + main.tightBoundingBox.width,
                            sub.tightBoundingBox.x + sub.tightBoundingBox.width,
                        ) -
                            maxOf(main.tightBoundingBox.x, sub.tightBoundingBox.x)
                    val isHorizontalFurigana = yGapH > -mainThickness * 0.5 && yGapH < proximityLimit && xOverlapH > 0.0
                    if (isVerticalFurigana || isHorizontalFurigana) {
                        keep[j] = false
                    }
                }
            }
        }

        return lines.filterIndexed { i, _ -> keep[i] }
    }

    private data class ProcessedLine(
        val isVertical: Boolean,
        val fontSize: Double,
        val lengthMain: Double,
        val minMain: Double,
        val maxMain: Double,
        val minCross: Double,
        val maxCross: Double,
    )

    private fun areLinesMergeable(a: ProcessedLine, b: ProcessedLine, config: MergeConfig): Boolean {
        if (a.isVertical != b.isVertical) return false

        val maxFont = maxOf(a.fontSize, b.fontSize)
        val minFont = minOf(a.fontSize, b.fontSize)
        val fontRatio = maxFont / minFont
        if (fontRatio > config.fontSizeRatio) return false

        val rawOverlapMain = minOf(a.maxMain, b.maxMain) - maxOf(a.minMain, b.minMain)
        // Panel/Vertical Continuity Check
        if (rawOverlapMain < -minFont * 0.5) return false

        val overlapMain = maxOf(0.0, rawOverlapMain)
        val gapCross = maxOf(0.0, b.minCross - a.maxCross, a.minCross - b.maxCross)
        val baseMetric = minFont
        val globalOverlap = overlapMain / maxOf(a.lengthMain, b.lengthMain)

        // 1. TOUCHING: merge anything that touches
        if (gapCross < baseMetric * 0.2) return true

        val isHighlySimilar = fontRatio < 1.25
        var allowedGap = 0.0

        if (isHighlySimilar) {
            // TIER 2A-2C: tiered by overlap
            allowedGap = when {
                globalOverlap > 0.8 -> 2.0
                globalOverlap > 0.4 -> 0.9
                else -> 1.3
            }
        } else {
            // TIER 3: dissimilar fonts
            if (globalOverlap > 0.5) allowedGap = 0.8
        }

        // Sidebar Protection
        val lenRatio = maxOf(a.lengthMain, b.lengthMain) / minOf(a.lengthMain, b.lengthMain)
        if (lenRatio > 2.5) allowedGap = minOf(allowedGap, 0.8)

        // Font Consistency Check
        if (gapCross > baseMetric * 1.2 && fontRatio > 1.15) return false
        if (gapCross > baseMetric * allowedGap) return false

        // Main Axis Proximity
        if (overlapMain <= 0.0) {
            val gapMain = maxOf(0.0, b.minMain - a.maxMain, a.minMain - b.maxMain)
            if (gapMain > baseMetric * 0.6) return false
        }

        return true
    }

    private class UnionFind(n: Int) {
        val parent = IntArray(n) { it }
        fun find(i: Int): Int {
            if (parent[i] != i) parent[i] = find(parent[i])
            return parent[i]
        }
        fun union(i: Int, j: Int) {
            parent[find(i)] = find(j)
        }
    }

    fun autoMerge(lines: List<OcrResult>, w: Int, h: Int, config: MergeConfig): List<OcrResult> {
        if (!config.enabled || lines.isEmpty()) return lines

        val cleanLines = filterBadBoxes(lines, w, h, config)
        if (cleanLines.isEmpty()) return emptyList()

        val processed = cleanLines.map { l ->
            val b = l.tightBoundingBox
            val lensIsVertical = l.forcedOrientation == "vertical"
            val charCount = l.text.length

            // Use AABB for grouping logic, but keep OBB in OcrResult
            val aabb = calculateAabb(getBoundingBoxCorners(b))
            val abx = aabb[0] - aabb[2] / 2.0
            val aby = aabb[1] - aabb[3] / 2.0
            val abw = aabb[2]
            val abh = aabb[3]

            val isV = if (config.language.prefersVertical) {
                if (charCount == 1) {
                    abh > abw * 0.8
                } else {
                    lensIsVertical || abh > abw
                }
            } else {
                lensIsVertical && abh > abw * 1.1
            }

            val minMain: Double
            val maxMain: Double
            val minCross: Double
            val maxCross: Double
            if (isV) {
                minMain = aby
                maxMain = aby + abh
                minCross = abx
                maxCross = abx + abw
            } else {
                minMain = abx
                maxMain = abx + abw
                minCross = aby
                maxCross = aby + abh
            }

            ProcessedLine(
                isVertical = isV,
                fontSize = if (isV) abw else abh,
                lengthMain = if (isV) abh else abw,
                minMain = minMain,
                maxMain = maxMain,
                minCross = minCross,
                maxCross = maxCross,
            )
        }

        val uf = UnionFind(processed.size)
        for (i in processed.indices) {
            for (j in i + 1 until processed.size) {
                if (areLinesMergeable(processed[i], processed[j], config)) {
                    uf.union(i, j)
                }
            }
        }

        val groups = mutableMapOf<Int, MutableList<Int>>()
        for (i in processed.indices) {
            groups.getOrPut(uf.find(i)) { mutableListOf() }.add(i)
        }

        val results = mutableListOf<OcrResult>()
        for ((_, indices) in groups) {
            if (indices.isEmpty()) continue

            // Single line: just tag orientation
            if (indices.size == 1) {
                val idx = indices[0]
                results.add(
                    cleanLines[idx].copy(
                        forcedOrientation = if (processed[idx].isVertical) "vertical" else "horizontal",
                        constituentBoxes = listOf(cleanLines[idx].tightBoundingBox),
                    ),
                )
                continue
            }

            val isVertical = processed[indices[0]].isVertical

            val groupLines = indices.map { cleanLines[it] }.sortedWith { a, b ->
                val ba = a.tightBoundingBox
                val bb = b.tightBoundingBox
                if (isVertical) {
                    // Vertical Japanese: right-to-left columns, top-to-bottom within column
                    val ra = ba.x + ba.width
                    val rb = bb.x + bb.width
                    if (abs(ra - rb) > 5.0) rb.compareTo(ra) else ba.y.compareTo(bb.y)
                } else {
                    // Horizontal: top-to-bottom rows, left-to-right within row
                    if (abs(ba.y - bb.y) > 5.0) ba.y.compareTo(bb.y) else ba.x.compareTo(bb.x)
                }
            }

            val useSpaceSeparator = config.addSpaceOnMerge ?: !config.language.prefersNoSpace
            val textContent = StringBuilder()
            for ((idx, line) in groupLines.withIndex()) {
                if (idx > 0) {
                    textContent.append('\n')
                }
                textContent.append(line.text)
            }

            // Compute merged AABB from all constituent corners
            val allCorners = groupLines.flatMap { getBoundingBoxCorners(it.tightBoundingBox) }
            val aabb = calculateAabb(allCorners)
            val cx = aabb[0]
            val cy = aabb[1]
            val bw = aabb[2]
            val bh = aabb[3]

            results.add(
                OcrResult(
                    text = textContent.toString(),
                    tightBoundingBox = BoundingBox(
                        x = cx - bw / 2.0,
                        y = cy - bh / 2.0,
                        width = bw,
                        height = bh,
                    ),
                    isMerged = true,
                    forcedOrientation = if (isVertical) "vertical" else "horizontal",
                    constituentBoxes = groupLines.map { it.tightBoundingBox },
                ),
            )
        }

        return results
    }
}
