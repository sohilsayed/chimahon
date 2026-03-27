package chimahon.ocr

internal interface OcrEngine {
    val name: String
    suspend fun recognize(bytes: ByteArray, language: OcrLanguage): List<EngineLine>
}
