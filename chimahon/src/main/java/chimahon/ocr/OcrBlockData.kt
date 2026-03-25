package chimahon.ocr

import kotlinx.serialization.Serializable

@Serializable
data class OcrBlockData(
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
    val lines: List<String>,
    val vertical: Boolean,
)

@Serializable
data class OcrPageData(
    val blocks: List<OcrBlockData>,
    val language: String,
    val version: Int = 1,
)

@Serializable
data class OcrTextBlock(
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
    val lines: List<String>,
    val vertical: Boolean = false,
)
