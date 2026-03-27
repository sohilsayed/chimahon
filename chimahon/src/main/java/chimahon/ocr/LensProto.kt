package chimahon.ocr

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// ---- Request messages ----

@Serializable
internal data class LensOverlayServerRequest(
    @ProtoNumber(1) val objectsRequest: LensOverlayObjectsRequest? = null,
)

@Serializable
internal data class LensOverlayObjectsRequest(
    @ProtoNumber(1) val requestContext: LensOverlayRequestContext? = null,
    @ProtoNumber(3) val imageData: ImageData? = null,
)

@Serializable
internal data class LensOverlayRequestContext(
    @ProtoNumber(3) val requestId: LensOverlayRequestId? = null,
    @ProtoNumber(4) val clientContext: LensOverlayClientContext? = null,
)

@Serializable
internal data class LensOverlayRequestId(
    @ProtoNumber(1) val uuid: Long = 0L,
    @ProtoNumber(2) val sequenceId: Int = 1,
    @ProtoNumber(3) val imageSequenceId: Int = 1,
)

@Serializable
internal data class LensOverlayClientContext(
    @ProtoNumber(1) val platform: Int = 0, // Platform::Web = 3
    @ProtoNumber(2) val surface: Int = 0, // Surface::Chromium = 4
    @ProtoNumber(4) val localeContext: LocaleContext? = null,
)

@Serializable
internal data class LocaleContext(
    @ProtoNumber(1) val language: String = "",
    @ProtoNumber(2) val region: String = "",
    @ProtoNumber(3) val timeZone: String = "",
)

@Serializable
internal data class ImageData(
    @ProtoNumber(1) val payload: ImagePayload? = null,
    @ProtoNumber(3) val imageMetadata: ImageMetadata? = null,
)

@Serializable
internal data class ImagePayload(
    @ProtoNumber(1) val imageBytes: ByteArray = byteArrayOf(),
) {
    override fun equals(other: Any?): Boolean =
        other is ImagePayload && imageBytes.contentEquals(other.imageBytes)
    override fun hashCode(): Int = imageBytes.contentHashCode()
}

@Serializable
internal data class ImageMetadata(
    @ProtoNumber(1) val width: Int = 0,
    @ProtoNumber(2) val height: Int = 0,
)

// ---- Response messages ----

@Serializable
internal data class LensOverlayServerResponse(
    @ProtoNumber(2) val objectsResponse: LensOverlayObjectsResponse? = null,
)

@Serializable
internal data class LensOverlayObjectsResponse(
    @ProtoNumber(3) val text: Text? = null,
    @ProtoNumber(4) val deepGleams: List<DeepGleamData> = emptyList(),
)

@Serializable
internal data class DeepGleamData(
    @ProtoNumber(10) val translation: TranslationData? = null,
)

@Serializable
internal data class TranslationData(
    @ProtoNumber(1) val status: TranslationStatusMsg? = null,
    @ProtoNumber(4) val translation: String = "",
)

@Serializable
internal data class TranslationStatusMsg(
    @ProtoNumber(1) val code: Int = 0, // TranslationStatus::Success = 1
)

@Serializable
internal data class Text(
    @ProtoNumber(1) val textLayout: TextLayout? = null,
    @ProtoNumber(2) val contentLanguage: String = "",
)

@Serializable
internal data class TextLayout(
    @ProtoNumber(1) val paragraphs: List<TextLayoutParagraph> = emptyList(),
)

@Serializable
internal data class TextLayoutParagraph(
    @ProtoNumber(2) val lines: List<TextLayoutLine> = emptyList(),
    @ProtoNumber(3) val geometry: Geometry? = null,
)

@Serializable
internal data class TextLayoutLine(
    @ProtoNumber(1) val words: List<TextLayoutWord> = emptyList(),
    @ProtoNumber(2) val geometry: Geometry? = null,
)

@Serializable
internal data class TextLayoutWord(
    @ProtoNumber(2) val plainText: String = "",
    @ProtoNumber(3) val textSeparator: String? = null,
    @ProtoNumber(4) val geometry: Geometry? = null,
)

@Serializable
internal data class Geometry(
    @ProtoNumber(1) val boundingBox: CenterRotatedBox? = null,
)

@Serializable
internal data class CenterRotatedBox(
    @ProtoNumber(1) val centerX: Float = 0f,
    @ProtoNumber(2) val centerY: Float = 0f,
    @ProtoNumber(3) val width: Float = 0f,
    @ProtoNumber(4) val height: Float = 0f,
    @ProtoNumber(5) val rotationZ: Float = 0f,
)
