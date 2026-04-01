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
    val constituentBoxes: List<BoundingBox>? = null,
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
    val language: OcrLanguage = OcrLanguage.JAPANESE,
    val addSpaceOnMerge: Boolean? = null,
    val furiganaFilter: Boolean = true,
    val mergeCloseParagraphs: Boolean = true,
    val supportCenterAlignedText: Boolean = true,
    // Legacy field kept for backward compat with old LensMerger
    @Deprecated("Use characterSize-relative thresholds instead")
    val fontSizeRatio: Double = 3.0,
)
