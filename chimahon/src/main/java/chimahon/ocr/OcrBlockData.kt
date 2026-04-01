package chimahon.ocr

import kotlinx.serialization.Serializable

@Serializable
data class OcrLineGeometry(
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
    val rotation: Float = 0f,
)

@Serializable
data class OcrBlockData(
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
    val lines: List<String>,
    val vertical: Boolean,
    val lineGeometries: List<OcrLineGeometry>? = null,
)

@Serializable
data class OcrPageData(
    val blocks: List<OcrBlockData>,
    val language: String,
    val version: Int = 2,
)

@Serializable
data class OcrTextBlock(
    val xmin: Float,
    val ymin: Float,
    val xmax: Float,
    val ymax: Float,
    val lines: List<String>,
    val vertical: Boolean = false,
    val lineGeometries: List<OcrLineGeometry>? = null,
)
