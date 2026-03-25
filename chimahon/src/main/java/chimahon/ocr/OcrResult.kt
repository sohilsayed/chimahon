package chimahon.ocr


data class BoundingBox(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double? = null,
)

data class OcrResult(
    val text: String,
    val tightBoundingBox: BoundingBox,
    val isMerged: Boolean? = null,
    val forcedOrientation: String? = null,
)

data class RawChunk(
    val lines: List<OcrResult>,
    val width: Int,
    val height: Int,
    val globalY: Int,
    val fullWidth: Int,
    val fullHeight: Int,
)

data class OcrDebugResult(
    val rawChunks: List<RawChunk>,
    val mergedResults: List<OcrResult>,
)

data class MergeConfig(
    val enabled: Boolean = true,
    val fontSizeRatio: Double = 3.0,
    val addSpaceOnMerge: Boolean? = null,
    val language: OcrLanguage = OcrLanguage.JAPANESE,
)
