package eu.kanade.tachiyomi.data.ocr

import android.graphics.Bitmap
import chimahon.ocr.LensClient
import chimahon.ocr.MergeConfig
import chimahon.ocr.OcrLanguage
import chimahon.ocr.OcrResult
import chimahon.ocr.OwOCRMerger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

suspend fun recognizePage(
    bytes: ByteArray,
    language: OcrLanguage,
): List<OcrResult> {
    val dictPrefs = Injekt.get<eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences>()
    val engineType = dictPrefs.ocrEngine().get()

    if (engineType == "local") {
        val localOcrBridge = Injekt.get<LocalOcrBridge>()
        val modelDownloader = Injekt.get<ModelDownloader>()
        if (!modelDownloader.isDownloaded) {
            logcat("OcrEngineSelector", LogPriority.WARN) { "local selected but models not downloaded, triggering download" }
            modelDownloader.triggerDownload()
            return emptyList()
        }
        if (!localOcrBridge.isAvailable) return emptyList()
        if (!localOcrBridge.isInitialized) {
            localOcrBridge.init()
        }
        if (!localOcrBridge.isInitialized) {
            logcat("OcrEngineSelector", LogPriority.WARN) { "local engine failed to initialize" }
            return emptyList()
        }
        val lines = localOcrBridge.recognize(bytes, language)
        if (lines.isEmpty()) {
            logcat("OcrEngineSelector", LogPriority.WARN) { "local engine returned no results" }
            return emptyList()
        }
        return OwOCRMerger.merge(lines, MergeConfig(language = language))
    }

    val lensClient = Injekt.get<LensClient>()
    val debugResult = lensClient.getDebugOcrData(bytes = bytes, language = language)
    return debugResult.mergedResults
}

suspend fun recognizePage(
    bitmap: Bitmap,
    language: OcrLanguage,
): List<OcrResult> {
    val dictPrefs = Injekt.get<eu.kanade.tachiyomi.ui.dictionary.DictionaryPreferences>()
    val engineType = dictPrefs.ocrEngine().get()

    if (engineType == "local") {
        val localOcrBridge = Injekt.get<LocalOcrBridge>()
        val modelDownloader = Injekt.get<ModelDownloader>()
        if (!modelDownloader.isDownloaded) {
            logcat("OcrEngineSelector", LogPriority.WARN) { "local selected but models not downloaded, triggering download" }
            modelDownloader.triggerDownload()
            return emptyList()
        }
        if (!localOcrBridge.isAvailable) return emptyList()
        if (!localOcrBridge.isInitialized) {
            localOcrBridge.init()
        }
        if (!localOcrBridge.isInitialized) {
            logcat("OcrEngineSelector", LogPriority.WARN) { "local engine failed to initialize" }
            return emptyList()
        }
        val bytes = withContext(Dispatchers.Default) {
            bitmap.optimizeForLocalOcr()
        }
        val lines = localOcrBridge.recognize(bytes, language)
        if (lines.isEmpty()) {
            logcat("OcrEngineSelector", LogPriority.WARN) { "local engine returned no results" }
            return emptyList()
        }
        return OwOCRMerger.merge(lines, MergeConfig(language = language))
    }

    val lensClient = Injekt.get<LensClient>()
    val debugResult = lensClient.getDebugOcrData(bitmap = bitmap, language = language)
    return debugResult.mergedResults
}

private fun Bitmap.optimizeForLocalOcr(maxPixels: Int = 3_000_000): ByteArray {
    val sourcePixels = width.toLong() * height.toLong()
    val bitmapForOcr = if (sourcePixels > maxPixels) {
        val scale = sqrt(maxPixels.toDouble() / sourcePixels.toDouble())
        Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        this
    }
    return ByteArrayOutputStream().use { output ->
        bitmapForOcr.compress(Bitmap.CompressFormat.JPEG, 85, output)
        if (bitmapForOcr !== this) bitmapForOcr.recycle()
        output.toByteArray()
    }
}
