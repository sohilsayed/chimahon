@file:OptIn(ExperimentalSerializationApi::class)

package chimahon.ocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

data class LensResult(
    val fullText: String,
    val paragraphs: List<Paragraph>,
    val translation: String?,
)

data class Paragraph(
    val text: String,
    val lines: List<Line>,
    val geometry: GeometryData?,
)

data class Line(
    val text: String,
    val words: List<Word>,
    val geometry: GeometryData?,
)

data class Word(
    val text: String,
    val separator: String,
    val geometry: GeometryData?,
)

data class GeometryData(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotationZ: Float,
    val angleDeg: Float,
)

class LensClient(
    httpClient: OkHttpClient = OkHttpClient(),
    private val apiKey: String = DEFAULT_API_KEY,
) {
    private val client = httpClient.newBuilder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun processImageBytes(bytes: ByteArray, lang: String? = null): LensResult =
        withContext(Dispatchers.IO) {
            val processed = processImageFromBytes(bytes)
            sendRequest(processed, lang)
        }

    /**
     * decode → Lens API → flatten paragraphs→lines → filter + merge → normalize 0..1
     */
    suspend fun processAndMerge(
        bytes: ByteArray,
        language: OcrLanguage = OcrLanguage.JAPANESE,
        addSpaceOnMerge: Boolean? = null,
    ): List<OcrResult> = withContext(Dispatchers.IO) {
        getDebugOcrData(bytes, language, addSpaceOnMerge).mergedResults
    }

    suspend fun getRawOcrData(
        bytes: ByteArray,
        language: OcrLanguage = OcrLanguage.JAPANESE,
    ): List<RawChunk> = withContext(Dispatchers.IO) {
        retryOcr {
            getRawOcrDataInternal(bytes, language)
        }
    }

    enum class MergerType { LEGACY, OWOCR }

    suspend fun getDebugOcrData(
        bytes: ByteArray,
        language: OcrLanguage = OcrLanguage.JAPANESE,
        addSpaceOnMerge: Boolean? = null,
        merger: MergerType = MergerType.OWOCR,
    ): OcrDebugResult = withContext(Dispatchers.IO) {
        retryOcr {
            val rawChunks = getRawOcrDataInternal(bytes, language)
            val config = MergeConfig(language = language, addSpaceOnMerge = addSpaceOnMerge)
            val mergedResults = when (merger) {
                MergerType.LEGACY -> rawChunks.flatMap { chunk ->
                    LensMerger.autoMerge(chunk.lines, chunk.width, chunk.height, config).map { result ->
                        result.normalizeFromChunk(chunk)
                    }
                }
                MergerType.OWOCR -> {
                    val allEngineLines = rawChunks.flatMap { chunk ->
                        chunk.lines.map { it.toEngineLine(chunk, language) }
                    }.distinctBy { it.text + it.bbox.toString() }
                    OwOCRMerger.merge(allEngineLines, config)
                }
            }
            val dedupedResults = if (rawChunks.size > 1) {
                dedupeChunkBoundaryResults(mergedResults)
            } else {
                mergedResults
            }

            OcrDebugResult(
                rawChunks = rawChunks,
                mergedResults = dedupedResults,
            )
        }
    }

    private fun dedupeChunkBoundaryResults(results: List<OcrResult>): List<OcrResult> {
        if (results.size < 2) return results

        // Sort by y to enable sliding window comparison
        val sorted = results.sortedBy { it.tightBoundingBox.y }
        val kept = mutableListOf<OcrResult>()

        for (candidate in sorted) {
            // Only compare with existing items that are close on the Y axis.
            // Since we sorted by Y, we can search backwards from the end of 'kept'
            // and stop once we are out of the 5% height window.
            var isDuplicate = false
            for (i in kept.size - 1 downTo 0) {
                val existing = kept[i]

                // If we are more than 0.5% of the total height away vertically,
                // any subsequent items in the sorted list will also be far away.
                // Note: since results are globally normalized 0.0-1.0, 0.005 is 0.5% of total height.
                if (candidate.tightBoundingBox.y - existing.tightBoundingBox.y > 0.005) {
                    break
                }

                if (sameOrientation(existing, candidate) &&
                    sameText(existing.text, candidate.text) &&
                    (
                        iou(existing.tightBoundingBox, candidate.tightBoundingBox) >= 0.55 ||
                            closeCenters(existing.tightBoundingBox, candidate.tightBoundingBox)
                        )
                ) {
                    isDuplicate = true
                    break
                }
            }

            if (!isDuplicate) {
                kept.add(candidate)
            }
        }
        return kept
    }

    private fun sameText(a: String, b: String): Boolean {
        return a.trim() == b.trim()
    }

    private fun sameOrientation(a: OcrResult, b: OcrResult): Boolean {
        return (a.forcedOrientation ?: "") == (b.forcedOrientation ?: "")
    }

    private fun closeCenters(a: BoundingBox, b: BoundingBox): Boolean {
        val ax = a.x + a.width / 2.0
        val ay = a.y + a.height / 2.0
        val bx = b.x + b.width / 2.0
        val by = b.y + b.height / 2.0
        return hypot(ax - bx, ay - by) <= 0.01
    }

    private fun iou(a: BoundingBox, b: BoundingBox): Double {
        val ax2 = a.x + a.width
        val ay2 = a.y + a.height
        val bx2 = b.x + b.width
        val by2 = b.y + b.height

        val interW = max(0.0, min(ax2, bx2) - max(a.x, b.x))
        val interH = max(0.0, min(ay2, by2) - max(a.y, b.y))
        val interArea = interW * interH
        if (interArea <= 0.0) return 0.0

        val aArea = a.width * a.height
        val bArea = b.width * b.height
        val union = aArea + bArea - interArea
        return if (union <= 0.0) 0.0 else interArea / union
    }

    private suspend fun getRawOcrDataInternal(
        bytes: ByteArray,
        language: OcrLanguage,
    ): List<RawChunk> {
        return prepareForOcr(bytes).map { chunk ->
            val lensResult = processImageBytes(chunk.pngBytes, language.bcp47)
            val flatLines = LensMerger.flattenToPixelLines(lensResult, chunk.width, chunk.height, language)
            RawChunk(
                lines = flatLines,
                width = chunk.width,
                height = chunk.height,
                globalY = chunk.globalY,
                fullWidth = chunk.fullWidth,
                fullHeight = chunk.fullHeight,
            )
        }
    }

    private suspend fun <T> retryOcr(block: suspend () -> T): T {
        var lastError: Throwable? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                if (attempt == MAX_RETRIES) {
                    break
                }
                delay(attempt * RETRY_DELAY_MS)
            }
        }
        throw IOException("OCR pipeline failed after $MAX_RETRIES attempts", lastError)
    }

    private fun sendRequest(image: ProcessedImage, lang: String?): LensResult {
        val request = LensOverlayServerRequest(
            objectsRequest = LensOverlayObjectsRequest(
                requestContext = LensOverlayRequestContext(
                    requestId = LensOverlayRequestId(
                        uuid = Random.nextLong() and Long.MAX_VALUE, // uint64: keep non-negative
                        sequenceId = 1,
                        imageSequenceId = 1,
                    ),
                    clientContext = LensOverlayClientContext(
                        platform = PLATFORM_WEB,
                        surface = SURFACE_CHROMIUM,
                        localeContext = LocaleContext(
                            language = lang?.ifBlank { null } ?: DEFAULT_LANGUAGE,
                            region = DEFAULT_CLIENT_REGION,
                            timeZone = DEFAULT_CLIENT_TIME_ZONE,
                        ),
                    ),
                ),
                imageData = ImageData(
                    payload = ImagePayload(imageBytes = image.bytes),
                    imageMetadata = ImageMetadata(width = image.width, height = image.height),
                ),
            ),
        )

        val payloadBytes = ProtoBuf.encodeToByteArray(LensOverlayServerRequest.serializer(), request)
        val respBytes = callApi(payloadBytes)
        val response = ProtoBuf.decodeFromByteArray(LensOverlayServerResponse.serializer(), respBytes)
        return parseResponse(response)
    }

    private fun callApi(requestBytes: ByteArray): ByteArray {
        val httpRequest = Request.Builder()
            .url(LENS_CRUPLOAD_ENDPOINT)
            .header("Content-Type", "application/x-protobuf")
            .header("X-Goog-Api-Key", apiKey)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .post(requestBytes.toRequestBody(PROTOBUF_MEDIA_TYPE))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Glens API error ${response.code}: ${response.body.string()}")
            }
            return response.body.bytes()
        }
    }

    private fun parseResponse(response: LensOverlayServerResponse): LensResult {
        val paragraphList = mutableListOf<Paragraph>()
        val fullTextBuf = StringBuilder()

        response.objectsResponse?.text?.textLayout?.paragraphs?.forEach { p ->
            val parsed = parseParagraph(p)
            fullTextBuf.append(parsed.text).append('\n')
            paragraphList.add(parsed)
        }

        return LensResult(
            fullText = fullTextBuf.trim().toString(),
            paragraphs = paragraphList,
            translation = extractTranslation(response),
        )
    }

    private fun parseParagraph(p: TextLayoutParagraph): Paragraph {
        val lines = p.lines.map { parseLine(it) }
        return Paragraph(
            text = lines.joinToString("\n") { it.text },
            lines = lines,
            geometry = p.geometry?.boundingBox?.let { toGeometryData(it) },
        )
    }

    private fun parseLine(l: TextLayoutLine): Line {
        val words = l.words.map { parseWord(it) }
        return Line(
            text = words.joinToString("") { it.text + it.separator }.trim(),
            words = words,
            geometry = l.geometry?.boundingBox?.let { toGeometryData(it) },
        )
    }

    private fun parseWord(w: TextLayoutWord): Word = Word(
        text = w.plainText,
        separator = w.textSeparator ?: "",
        geometry = w.geometry?.boundingBox?.let { toGeometryData(it) },
    )

    private fun toGeometryData(box: CenterRotatedBox): GeometryData = GeometryData(
        centerX = box.centerX,
        centerY = box.centerY,
        width = box.width,
        height = box.height,
        rotationZ = box.rotationZ,
        angleDeg = Math.toDegrees(box.rotationZ.toDouble()).toFloat(),
    )

    private fun extractTranslation(response: LensOverlayServerResponse): String? {
        val parts = response.objectsResponse?.deepGleams
            ?.mapNotNull { gleam ->
                val t = gleam.translation ?: return@mapNotNull null
                if (t.status?.code == TRANSLATION_SUCCESS && t.translation.isNotEmpty()) {
                    t.translation
                } else {
                    null
                }
            }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return parts.joinToString("\n")
    }

    private fun OcrResult.normalizeFromChunk(chunk: RawChunk): OcrResult {
        val box = tightBoundingBox
        val globalPixelY = box.y + chunk.globalY.toDouble()
        val normalizedBBox = box.copy(
            x = box.x / chunk.fullWidth.toDouble(),
            y = globalPixelY / chunk.fullHeight.toDouble(),
            width = box.width / chunk.fullWidth.toDouble(),
            height = box.height / chunk.fullHeight.toDouble(),
        )

        val normalizedConstituents = constituentBoxes?.map { c ->
            val cgY = c.y + chunk.globalY.toDouble()
            c.copy(
                x = c.x / chunk.fullWidth.toDouble(),
                y = cgY / chunk.fullHeight.toDouble(),
                width = c.width / chunk.fullWidth.toDouble(),
                height = c.height / chunk.fullHeight.toDouble(),
            )
        }

        return copy(
            tightBoundingBox = normalizedBBox,
            constituentBoxes = normalizedConstituents,
        )
    }

    private fun OcrResult.toEngineLine(chunk: RawChunk, language: OcrLanguage): EngineLine {
        val box = tightBoundingBox
        val normBox = NormalizedBBox(
            left = box.x / chunk.fullWidth.toDouble(),
            top = (box.y + chunk.globalY.toDouble()) / chunk.fullHeight.toDouble(),
            right = (box.x + box.width) / chunk.fullWidth.toDouble(),
            bottom = (box.y + box.height + chunk.globalY.toDouble()) / chunk.fullHeight.toDouble(),
            rotation = box.rotation ?: 0.0,
        )
        val direction = when (forcedOrientation) {
            "vertical" -> WritingDirection.TTB
            else -> WritingDirection.LTR
        }
        return EngineLine(
            text = text,
            bbox = normBox,
            writingDirection = direction,
            language = language,
        )
    }

    companion object {
        private val PROTOBUF_MEDIA_TYPE = "application/x-protobuf".toMediaType()
        private const val DEFAULT_LANGUAGE = "en"
        private const val PLATFORM_WEB = 3
        private const val SURFACE_CHROMIUM = 4
        private const val TRANSLATION_SUCCESS = 1
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1_000L
    }
}
