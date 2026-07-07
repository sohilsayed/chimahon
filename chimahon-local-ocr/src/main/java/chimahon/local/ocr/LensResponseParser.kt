package chimahon.local.ocr

import chimahon.ocr.EngineLine
import chimahon.ocr.NormalizedBBox
import chimahon.ocr.OcrLanguage
import chimahon.ocr.WritingDirection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min

data class ParsedLensResponse(
    val lines: List<EngineLine>,
    val boxSource: String,
    val coordinateWidth: Int,
    val coordinateHeight: Int,
)

object LensResponseParser {

    private data class Field(
        val number: Int,
        val wireType: Int,
        val text: String? = null,
        val numberValue: Double? = null,
        val child: Message? = null,
    )

    private class Message(
        val start: Int,
        val end: Int,
        val parent: Message?,
        val depth: Int,
    ) {
        val fields = mutableListOf<Field>()
    }

    private const val DIR_LINE = 4
    private const val DIR_LINE_ALT = 3
    private const val DIR_CHAR = 2
    private const val DIR_CHAR_ALT = 0

    private val blockedTexts = setOf(
        "Jpan", "Hani", "Kore", "Latn", "Arab", "Cyrl", "Grek",
        "Hebr", "Thai", "Deva", "Beng", "Gujr", "Guru", "Geor",
        "Knda", "Mlym", "Taml", "Telu", "Sinh",
        "google_ocr", "Unsupported", "Request", "mediapipe",
    )

    private val languageMap = mapOf(
        "ja" to OcrLanguage.JAPANESE,
        "en" to OcrLanguage.ENGLISH,
        "zh" to OcrLanguage.CHINESE,
        "ko" to OcrLanguage.KOREAN,
        "ar" to OcrLanguage.ARABIC,
        "es" to OcrLanguage.SPANISH,
        "fr" to OcrLanguage.FRENCH,
        "de" to OcrLanguage.GERMAN,
        "pt" to OcrLanguage.PORTUGUESE,
        "ru" to OcrLanguage.RUSSIAN,
    )

    fun parse(data: ByteArray, imageWidth: Int = 0, imageHeight: Int = 0): ParsedLensResponse {
        val root = parseMessage(data, 0, data.size, null, 0)
        val innerData = root?.let { findInnerDataMessage(it) }
        if (innerData == null) {
            return ParsedLensResponse(emptyList(), "none", imageWidth, imageHeight)
        }

        val imageInfo = innerData.fields.firstOrNull { it.number == 2 }?.child
            ?: return ParsedLensResponse(emptyList(), "none", imageWidth, imageHeight)

        val coordinateWidth = imageInfo.fields
            .firstOrNull { it.number == 1 && it.numberValue != null }
            ?.numberValue?.toInt() ?: imageWidth

        val coordinateHeight = imageInfo.fields
            .firstOrNull { it.number == 2 && it.numberValue != null }
            ?.numberValue?.toInt()
            ?: if (coordinateWidth > 0 && imageWidth > 0 && imageHeight > 0) {
                (coordinateWidth.toDouble() * imageHeight / imageWidth).toInt()
            } else {
                imageHeight
            }

        val entries = imageInfo.fields.filter { it.number == 7 && it.child != null }
        val lines = buildLines(entries, coordinateWidth, coordinateHeight)

        return ParsedLensResponse(lines, "native", coordinateWidth, coordinateHeight)
    }

    private fun findInnerDataMessage(root: Message): Message? {
        fun visit(message: Message): Message? {
            val imgInfo = message.fields.firstOrNull { it.number == 2 }?.child ?: return null
            val hasWidth = imgInfo.fields.any { it.number == 1 && it.numberValue != null && it.numberValue.toInt() in 100..20000 }
            val hasHeight = imgInfo.fields.any { it.number == 2 && it.numberValue != null && it.numberValue.toInt() in 100..20000 }
            val hasEntries = imgInfo.fields.count { it.number == 7 && it.child != null } >= 2
            if (hasWidth && hasHeight && hasEntries) return message
            for (field in message.fields) {
                field.child?.let { visit(it) }?.let { return it }
            }
            return null
        }
        return visit(root)
    }

    private fun buildLines(
        entries: List<Field>,
        imageWidth: Int,
        imageHeight: Int,
    ): List<EngineLine> {
        val parsed = entries.mapNotNull { parseEntry(it, imageWidth, imageHeight) }
        val result = mutableListOf<EngineLine>()
        var i = 0
        val seenTexts = mutableSetOf<String>()
        while (i < parsed.size) {
            if (parsed[i].dir != DIR_LINE && parsed[i].dir != DIR_LINE_ALT) {
                i++
                continue
            }
            i++
            while (i < parsed.size && parsed[i].dir != DIR_LINE && parsed[i].dir != DIR_LINE_ALT) {
                val e = parsed[i]
                if ((e.dir == DIR_CHAR || e.dir == DIR_CHAR_ALT) && e.text != null && e.text !in blockedTexts && e.text !in seenTexts) {
                    seenTexts += e.text
                    val bestBox = (
                        e.msg?.let { findBox(it, imageWidth, imageHeight) }
                            ?: e.bbox
                            ?: continue
                        )
                    result.add(
                        EngineLine(
                            text = e.text,
                            bbox = bestBox,
                            writingDirection = inferDirection(bestBox, e.rawAngleDegrees),
                            language = languageMap[e.language] ?: OcrLanguage.JAPANESE,
                        ),
                    )
                }
                i++
            }
        }
        return result
    }

    private data class RawEntry(
        val dir: Int,
        val text: String?,
        val bbox: NormalizedBBox?,
        val rawAngleDegrees: Double,
        val language: String?,
        val msg: Message?,
    )

    private fun parseEntry(
        entry: Field,
        imageWidth: Int,
        imageHeight: Int,
    ): RawEntry? {
        val msg = entry.child ?: return null
        val dir = msg.fields.firstOrNull { it.number == 4 }?.numberValue?.toInt() ?: return null
        if (dir != DIR_LINE && dir != DIR_LINE_ALT && dir != DIR_CHAR && dir != DIR_CHAR_ALT) return null
        val text = msg.fields.firstOrNull { it.number == 15 }?.text
            ?.let { cleanText(it) }
        val lang = msg.fields.firstOrNull { it.number == 13 }?.child
            ?.fields?.firstOrNull { it.number == 1 }?.text
        val (bbox, angleDeg) = extractBbox(msg, imageWidth, imageHeight)
        return RawEntry(dir, text, bbox, angleDeg, lang, msg)
    }

    // ---- bbox extraction ----

    private fun extractBbox(
        msg: Message,
        imageWidth: Int,
        imageHeight: Int,
    ): Pair<NormalizedBBox?, Double> {
        val rect = msg.fields.firstOrNull { it.number == 2 }?.child
            ?.fields?.firstOrNull { it.number == 2 }?.child ?: return null to 0.0
        val x = rect.fields.firstOrNull { it.number == 1 }?.numberValue ?: return null to 0.0
        val y = rect.fields.firstOrNull { it.number == 2 }?.numberValue ?: return null to 0.0
        val w = rect.fields.firstOrNull { it.number == 3 }?.numberValue ?: return null to 0.0
        val h = rect.fields.firstOrNull { it.number == 4 }?.numberValue ?: return null to 0.0
        val angle = rect.fields.firstOrNull { it.number == 5 }?.numberValue ?: 0.0
        if (w <= 0.0 || h <= 0.0 || imageWidth <= 0 || imageHeight <= 0) return null to angle

        val left = (x / imageWidth).coerceIn(0.0, 1.0)
        val top = (y / imageHeight).coerceIn(0.0, 1.0)
        val right = ((x + w) / imageWidth).coerceIn(0.0, 1.0)
        val bottom = ((y + h) / imageHeight).coerceIn(0.0, 1.0)
        if (right - left < 0.003 || bottom - top < 0.003) return null to angle

        return NormalizedBBox(left, top, right, bottom, Math.toRadians(angle)) to angle
    }

    // ---- legacy recursive bbox search (fallback) ----

    private fun findBox(owner: Message, imageWidth: Int, imageHeight: Int): NormalizedBBox? {
        var current: Message? = owner
        var hops = 0
        while (current != null && hops < 5) {
            candidateBox(current, imageWidth, imageHeight)?.let { return it }
            directChildBox(current, imageWidth, imageHeight)?.let { return it }
            current = current.parent
            hops++
        }
        return null
    }

    private fun directChildBox(message: Message, imageWidth: Int, imageHeight: Int): NormalizedBBox? {
        for (field in message.fields) {
            val child = field.child ?: continue
            candidateBox(child, imageWidth, imageHeight)?.let { return it }
        }
        return null
    }

    private fun candidateBox(message: Message, imageWidth: Int, imageHeight: Int): NormalizedBBox? {
        pointCloudBox(message, imageWidth, imageHeight)?.let { return it }

        val values = linkedMapOf<Int, MutableList<Double>>()
        for (field in message.fields) {
            val value = field.numberValue ?: continue
            if (!value.isFinite()) continue
            values.getOrPut(field.number) { mutableListOf() } += value
        }
        fun v(number: Int): Double? = values[number]?.firstOrNull()

        val f1 = v(1)
        val f2 = v(2)
        val f3 = v(3)
        val f4 = v(4)
        val f5 = v(5) ?: 0.0

        if (f1 != null && f2 != null && f3 != null && f4 != null) {
            screenAiRect(f1, f2, f3, f4, f5, imageWidth, imageHeight)?.let { return it }
            centerBox(f1, f2, f3, f4, f5, imageWidth, imageHeight)?.let { return it }
        }

        for (field in message.fields) {
            val child = field.child ?: continue
            candidateBox(child, imageWidth, imageHeight)?.let { return it }
        }
        return null
    }

    private fun pointCloudBox(message: Message, imageWidth: Int, imageHeight: Int): NormalizedBBox? {
        val points = message.fields.mapNotNull { it.child }.mapNotNull { child ->
            val xs = child.fields.firstOrNull { it.number == 1 && it.numberValue != null }?.numberValue
            val ys = child.fields.firstOrNull { it.number == 2 && it.numberValue != null }?.numberValue
            if (xs != null && ys != null && xs.isFinite() && ys.isFinite()) xs to ys else null
        }
        if (points.size < 4) return null

        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        return cornerBox(minX, minY, maxX, maxY, imageWidth, imageHeight)
    }

    private fun screenAiRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        angleDegrees: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): NormalizedBBox? {
        if (width <= 0.0 || height <= 0.0) return null
        if (imageWidth <= 0 || imageHeight <= 0) return null
        if (width <= 1.5 && height <= 1.5) return null

        val angleRad = Math.toRadians(angleDegrees)
        val cxOffset = (width / 2.0) * Math.cos(angleRad) - (height / 2.0) * Math.sin(angleRad)
        val cyOffset = (width / 2.0) * Math.sin(angleRad) + (height / 2.0) * Math.cos(angleRad)
        val centerX = x + cxOffset
        val centerY = y + cyOffset

        val normLeft = ((centerX - width / 2.0) / imageWidth)
        val normTop = ((centerY - height / 2.0) / imageHeight)
        val normRight = ((centerX + width / 2.0) / imageWidth)
        val normBottom = ((centerY + height / 2.0) / imageHeight)

        return makeBox(normLeft, normTop, normRight, normBottom, angleRad)
    }

    private fun cornerBox(
        left: Double,
        top: Double,
        right: Double,
        bottom: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): NormalizedBBox? {
        if (right <= left || bottom <= top) return null
        if (right <= 1.5 || bottom <= 1.5) return null
        if (imageWidth <= 0 || imageHeight <= 0) return null
        if (right > imageWidth * 1.2 || bottom > imageHeight * 1.2) return null
        return makeBox(
            left / imageWidth,
            top / imageHeight,
            right / imageWidth,
            bottom / imageHeight,
            0.0,
        )
    }

    private fun centerBox(
        centerX: Double,
        centerY: Double,
        width: Double,
        height: Double,
        rotation: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): NormalizedBBox? {
        if (width <= 0.0 || height <= 0.0) return null
        if (centerX !in -0.2..1.2 || centerY !in -0.2..1.2) return null
        if (width > 1.2 || height > 1.2) return null
        return makeBox(
            centerX - width / 2.0,
            centerY - height / 2.0,
            centerX + width / 2.0,
            centerY + height / 2.0,
            rotation,
        )
    }

    private fun makeBox(left: Double, top: Double, right: Double, bottom: Double, rotation: Double): NormalizedBBox? {
        val l = left.coerceIn(-0.1, 1.1)
        val t = top.coerceIn(-0.1, 1.1)
        val r = right.coerceIn(-0.1, 1.1)
        val b = bottom.coerceIn(-0.1, 1.1)
        val lc = l.coerceIn(0.0, 1.0)
        val tc = t.coerceIn(0.0, 1.0)
        val rc = r.coerceIn(0.0, 1.0)
        val bc = b.coerceIn(0.0, 1.0)
        if (rc - lc < 0.003 || bc - tc < 0.003) return null
        if (rc - lc > 0.98 && bc - tc > 0.98) return null
        return NormalizedBBox(lc, tc, rc, bc, rotation)
    }

    // ---- direction inference ----

    private fun inferDirection(box: NormalizedBBox, protoAngleDegrees: Double): WritingDirection {
        if (abs(protoAngleDegrees) > 5.0) {
            val norm = normalizedRadians(Math.toRadians(protoAngleDegrees))
            if (abs(abs(norm) - Math.PI / 2.0) < 0.5) return WritingDirection.TTB
        }
        return if (box.width <= box.height) WritingDirection.TTB else WritingDirection.LTR
    }

    private fun normalizedRadians(value: Double): Double {
        var radians = value
        val fullTurn = Math.PI * 2.0
        while (radians > Math.PI) radians -= fullTurn
        while (radians < -Math.PI) radians += fullTurn
        return radians
    }

    // ---- text helpers ----

    private fun cleanText(text: String): String {
        val cleaned = text
            .replace('\u0000', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
        if (cleaned.length < 2) return ""
        val printable = cleaned.count { it.code in 0x20..0x7E || it.code >= 0xA0 }
        return if (printable >= cleaned.length * 9 / 10) cleaned else ""
    }

    // ---- protobuf wire-format decoder ----

    private fun parseMessage(
        data: ByteArray,
        start: Int,
        end: Int,
        parent: Message?,
        depth: Int,
    ): Message? {
        if (depth > 14 || start >= end) return null
        var pos = start
        val message = Message(start, end, parent, depth)

        while (pos < end) {
            val keyRead = readVarint(data, pos, end) ?: return null
            val key = keyRead.first
            pos = keyRead.second
            val fieldNumber = (key ushr 3).toInt()
            val wireType = (key and 7).toInt()
            if (fieldNumber == 0) return null

            when (wireType) {
                0 -> {
                    val valueRead = readVarint(data, pos, end) ?: return null
                    pos = valueRead.second
                    message.fields += Field(fieldNumber, wireType, numberValue = valueRead.first.toDouble())
                }
                1 -> {
                    if (pos + 8 > end) return null
                    val value = ByteBuffer.wrap(data, pos, 8)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .double
                    pos += 8
                    message.fields += Field(fieldNumber, wireType, numberValue = value)
                }
                2 -> {
                    val lenRead = readVarint(data, pos, end) ?: return null
                    val len = lenRead.first
                    if (len < 0L || len > Int.MAX_VALUE) return null
                    pos = lenRead.second
                    val next = pos + len.toInt()
                    if (next < pos || next > end) return null

                    val text = readableText(data, pos, next)
                    val child = parseMessage(data, pos, next, message, depth + 1)
                    message.fields += Field(fieldNumber, wireType, text = text, child = child)
                    pos = next
                }
                5 -> {
                    if (pos + 4 > end) return null
                    val value = ByteBuffer.wrap(data, pos, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .float
                        .toDouble()
                    pos += 4
                    message.fields += Field(fieldNumber, wireType, numberValue = value)
                }
                else -> return null
            }
        }

        return if (pos == end && message.fields.isNotEmpty()) message else null
    }

    private fun readVarint(data: ByteArray, start: Int, end: Int): Pair<Long, Int>? {
        var pos = start
        var shift = 0
        var value = 0L
        while (pos < end && shift < 64) {
            val b = data[pos].toInt() and 0xFF
            value = value or ((b and 0x7F).toLong() shl shift)
            pos++
            if ((b and 0x80) == 0) return value to pos
            shift += 7
        }
        return null
    }

    private fun readableText(data: ByteArray, start: Int, end: Int): String? {
        val size = end - start
        if (size !in 2..500) return null
        val text = try {
            data.copyOfRange(start, end).toString(Charsets.UTF_8)
        } catch (_: Throwable) {
            return null
        }
        if ('\uFFFD' in text) return null
        val cleaned = text
            .replace('\u0000', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
        if (cleaned.length < 2) return null
        val printable = cleaned.count { it.code in 0x20..0x7E || it.code >= 0xA0 }
        return if (printable >= cleaned.length * 9 / 10) cleaned else null
    }
}
