package chimahon.local.ocr

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import chimahon.ocr.EngineLine
import chimahon.ocr.OcrBitmapDecoder
import chimahon.ocr.OcrLanguage
import com.google.android.libraries.lens.ondevice.nativeapi.LodeSplitRegistry
import com.google.android.libraries.lens.ondevice.nativeapi.OnDeviceEngineNativeApi
import java.io.File

private fun ByteArray.indexOfSubarray(needle: ByteArray): Int {
    if (needle.isEmpty() || needle.size > size) return -1
    for (i in 0..(size - needle.size)) {
        var matched = true
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) {
                matched = false
                break
            }
        }
        if (matched) return i
    }
    return -1
}

class LensEngine(private val context: Context) : chimahon.ocr.OcrEngine {

    override val name: String = "Local OCR"

    private val nativeApi = OnDeviceEngineNativeApi()
    private val registry = LodeSplitRegistry()
    private var initialized = false
    private var lastPrepareMs = 0L
    private var lastNativeInitMs = 0L
    private var lastInitMs = 0L

    private val assetRoot: File
        get() = File(context.filesDir, "screenai_models")

    private val assetMarker: File
        get() = File(assetRoot, ".prepared_version")

    private fun supportedAbi(): String? = when {
        "arm64-v8a" in Build.SUPPORTED_ABIS -> "arm64-v8a"
        "armeabi-v7a" in Build.SUPPORTED_ABIS -> "armeabi-v7a"
        else -> null
    }

    override suspend fun recognize(
        bytes: ByteArray,
        language: OcrLanguage,
    ): List<EngineLine> {
        if (!initialized) {
            init()
        }
        if (!initialized) return emptyList()
        val bitmap = try {
            OcrBitmapDecoder.decode(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode image for local OCR", e)
            return emptyList()
        }
        return try {
            ocrLines(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private var dumpCounter = 0

    fun ocrLines(bitmap: Bitmap): List<EngineLine> {
        if (!initialized) return emptyList()
        val req = byteArrayOf(0x60, 0x0E, 0x72, 0x00)
        val response = nativeApi.sendRequest(req, bitmap)
        if (response == null || response.isEmpty()) return emptyList()

        if (response.size > 0) {
            try {
                val dumpDir = File(context.cacheDir, "ocr_dumps")
                dumpDir.mkdirs()
                val baseName = "response_${dumpCounter++}_${bitmap.width}x${bitmap.height}"
                val rawFile = File(dumpDir, "$baseName.bin")
                rawFile.writeBytes(response)
                Log.d(TAG, "Dumped raw (${response.size} bytes) to ${rawFile.absolutePath}")

                val parsed = LensResponseParser.parse(response, bitmap.width, bitmap.height)
                val jsonFile = File(dumpDir, "$baseName.json")
                jsonFile.writeText(parsed.toJson())
                Log.d(TAG, "Dumped parsed to ${jsonFile.absolutePath}")

                return parsed.lines
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dump response", e)
            }
        }

        val parsed = LensResponseParser.parse(response, bitmap.width, bitmap.height)
        return parsed.lines
    }

    private fun assetVersion(): String {
        val runner = File(assetRoot, "lots_multiscript_v8_runner.binarypb")
        val runnerLen = if (runner.isFile) runner.length() else -1L
        return "lens-assets-v5|runner=$runnerLen"
    }

    private fun requiredPreparedFiles(root: File): List<File> = listOf(
        File(root, "lots_multiscript_v8_runner.binarypb"),
        File(root, "lots_multiscript_v8_runner_patched.binarypb"),
        File(root, "lots_multiscript_v8_engine_patched.binarypb"),
        File(root, "third_party/lens/line_detector/v688492737/gocr_group_rpn_text_detection_config_2024_q4.binarypb"),
        File(root, "third_party/lens/line_recognition/v678672708/recognizer_jpan.tflite"),
        File(root, "third_party/lens/line_recognition/v678672708/recognizer_jpan_lm.compact_fst.gz"),
    )

    private fun assetsArePrepared(root: File, version: String): Boolean {
        if (!assetMarker.isFile || assetMarker.readText() != version) return false
        return requiredPreparedFiles(root).all { it.isFile && it.length() > 0L }
    }

    private fun prepareAssets(root: File) {
        val version = assetVersion()
        if (assetsArePrepared(root, version)) {
            Log.d(TAG, "Asset tree already prepared at ${root.absolutePath}")
            return
        }

        Log.d(TAG, "Preparing persistent assets at ${root.absolutePath}...")
        patchRunnerDataDir(root)
        assetMarker.parentFile?.mkdirs()
        assetMarker.writeText(version)
        val fileCount = root.walkTopDown().filter { it.isFile }.count()
        Log.d(TAG, "Asset prepare finished: files=$fileCount version=$version")
    }

    private fun patchRunnerDataDir(assetRoot: File) {
        val source = File(assetRoot, "lots_multiscript_v8_runner.binarypb")
        val patched = File(assetRoot, "lots_multiscript_v8_runner_patched.binarypb")
        val dataDir = ".."
        val dataDirBytes = dataDir.toByteArray(Charsets.UTF_8)
        val enginePathBytes = "../lots_multiscript_v8_engine_patched.binarypb".toByteArray(Charsets.UTF_8)
        val original = source.readBytes()
        val engineNeedle = byteArrayOf(
            0x0A, 0x3E, 0x0A, 0x15,
            'o'.code.toByte(), 'c'.code.toByte(), 'r'.code.toByte(), '_'.code.toByte(),
            's'.code.toByte(), 'u'.code.toByte(), 'b'.code.toByte(), 'g'.code.toByte(),
            'r'.code.toByte(), 'a'.code.toByte(), 'p'.code.toByte(), 'h'.code.toByte(),
            '_'.code.toByte(), 't'.code.toByte(), 'e'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(),
            'e'.code.toByte(), 0x12, 0x25, 0x0A, 0x23,
            'l'.code.toByte(), 'o'.code.toByte(), 't'.code.toByte(), 's'.code.toByte(),
            '_'.code.toByte(), 'm'.code.toByte(), 'u'.code.toByte(), 'l'.code.toByte(),
            't'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 'c'.code.toByte(),
            'r'.code.toByte(), 'i'.code.toByte(), 'p'.code.toByte(), 't'.code.toByte(),
            '_'.code.toByte(), 'v'.code.toByte(), '8'.code.toByte(), '_'.code.toByte(),
            'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 'i'.code.toByte(),
            'n'.code.toByte(), 'e'.code.toByte(), '.'.code.toByte(), 'b'.code.toByte(),
            'i'.code.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(),
            'y'.code.toByte(), 'p'.code.toByte(), 'b'.code.toByte(),
        )
        val dataNeedle = byteArrayOf(
            0x0A, 0x0F, 0x0A, 0x08,
            'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
            '_'.code.toByte(), 'd'.code.toByte(), 'i'.code.toByte(), 'r'.code.toByte(),
            0x12, 0x03, 0x0A, 0x01, '.'.code.toByte(),
        )
        val enginePos = original.indexOfSubarray(engineNeedle)
        val dataPos = original.indexOfSubarray(dataNeedle)
        if (enginePos < 0 || dataPos < 0) {
            Log.w(TAG, "Could not patch runner; markers not found engine=$enginePos data=$dataPos")
            return
        }

        fun varint(value: Int): ByteArray {
            var v = value
            val out = ArrayList<Byte>(5)
            while (v > 0x7F) {
                out.add(((v and 0x7F) or 0x80).toByte())
                v = v ushr 7
            }
            out.add(v.toByte())
            return out.toByteArray()
        }

        fun bytesField(field: Int, value: ByteArray): ByteArray {
            return byteArrayOf(((field shl 3) or 2).toByte()) + varint(value.size) + value
        }

        val engineReplacementPayload = bytesField(1, "ocr_subgraph_template".toByteArray(Charsets.UTF_8)) +
            bytesField(2, bytesField(1, enginePathBytes))
        val engineReplacement = bytesField(1, engineReplacementPayload)
        val dataReplacementPayload = bytesField(1, "data_dir".toByteArray(Charsets.UTF_8)) +
            bytesField(2, bytesField(1, dataDirBytes))
        val dataReplacement = bytesField(1, dataReplacementPayload)
        val delta = (engineReplacement.size - engineNeedle.size) +
            (dataReplacement.size - dataNeedle.size)

        val out = original.toMutableList()
        fun replacePrefix(oldPrefix: ByteArray, tagSize: Int, newLength: Int) {
            val prefixPos = out.toByteArray().indexOfSubarray(oldPrefix)
            if (prefixPos < 0) {
                throw IllegalStateException("runner length prefix not found: ${oldPrefix.joinToString("") { "%02X".format(it) }}")
            }
            val newPrefix = oldPrefix.copyOfRange(0, tagSize) + varint(newLength)
            if (newPrefix.size != oldPrefix.size) {
                throw IllegalStateException("runner length prefix changed width")
            }
            for (i in newPrefix.indices) out[prefixPos + i] = newPrefix[i]
        }

        replacePrefix(byteArrayOf(0x0A, 0xEB.toByte(), 0x01), 1, 0xEB + delta)
        replacePrefix(byteArrayOf(0x3A, 0x9A.toByte(), 0x01), 1, 0x9A + delta)
        replacePrefix(byteArrayOf(0xAA.toByte(), 0xE7.toByte(), 0xF7.toByte(), 0x93.toByte(), 0x05, 0x93.toByte(), 0x01), 5, 0x93 + delta)
        replacePrefix(byteArrayOf(0x0A, 0x90.toByte(), 0x01), 1, 0x90 + delta)

        listOf(
            Triple(dataPos, dataNeedle.size, dataReplacement),
            Triple(enginePos, engineNeedle.size, engineReplacement),
        ).sortedByDescending { it.first }.forEach { (pos, size, replacement) ->
            repeat(size) { out.removeAt(pos) }
            out.addAll(pos, replacement.toList())
        }
        patched.writeBytes(out.toByteArray())
        Log.d(TAG, "Patched runner (${patched.length()} bytes)")
    }

    fun init() {
        if (initialized) return
        val initStart = SystemClock.elapsedRealtime()
        Log.i(TAG, "=== LensEngine.init() START ===")
        val destDir = assetRoot

        prepareAssets(destDir)

        try {
            System.loadLibrary("lens_asset_init")
            val abi = supportedAbi()
            if (abi == null) {
                Log.e(TAG, "Unsupported ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
                return
            }
            val libDir = File(assetRoot, "lib/$abi")
            System.load(File(libDir, "liblens_ondevice_engine_base.so").absolutePath)
            System.load(File(libDir, "liblens_ondevice_engine_play_ml.so").absolutePath)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native libraries", e)
            return
        }

        registry.create()
        if (registry.getNativeHandle() == 0L) {
            Log.e(TAG, "nativeCreate returned 0 — base.so init failed")
            return
        }

        val cacheDir = context.cacheDir.absolutePath
        try {
            nativeInitAssetManager(context, cacheDir)
        } catch (e: Throwable) {
            Log.e(TAG, "nativeInitAssetManager threw exception", e)
        }

        try {
            registry.registerModules(listOf("lens_ondevice_engine_play_ml_module"))
        } catch (e: Throwable) {
            Log.e(TAG, "registerModules threw exception", e)
        }

        try {
            nativeApi.init(context, registry)
        } catch (e: Throwable) {
            Log.e(TAG, "nativeApi.init threw exception", e)
        }

        initialized = nativeApi.getNativeHandle() != 0L
        lastInitMs = SystemClock.elapsedRealtime() - initStart
        Log.i(TAG, "=== LensEngine.init() DONE: initialized=$initialized ===")
    }

    fun destroy() {
        nativeApi.destroy()
        registry.destroy()
    }

    private fun ParsedLensResponse.toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"boxSource\": \"$boxSource\",")
        appendLine("  \"coordinateWidth\": $coordinateWidth,")
        appendLine("  \"coordinateHeight\": $coordinateHeight,")
        appendLine("  \"lines\": [")
        for ((i, line) in lines.withIndex()) {
            appendLine("    {")
            appendLine("      \"text\": ${jsonStr(line.text)},")
            appendLine("      \"writingDirection\": \"${line.writingDirection?.name ?: "null"}\",")
            appendLine("      \"characterSize\": ${line.characterSize},")
            appendLine("      \"hasJpText\": ${line.hasJpText},")
            appendLine("      \"hasKanji\": ${line.hasKanji},")
            appendLine("      \"rotation\": ${line.rotation},")
            appendLine("      \"bbox\": {")
            appendLine("        \"left\": ${line.bbox.left},")
            appendLine("        \"top\": ${line.bbox.top},")
            appendLine("        \"right\": ${line.bbox.right},")
            appendLine("        \"bottom\": ${line.bbox.bottom},")
            appendLine("        \"rotation\": ${line.bbox.rotation}")
            appendLine("      }")
            val comma = if (i < lines.lastIndex) "," else ""
            appendLine("    }$comma")
        }
        appendLine("  ]")
        append("}")
    }

    private fun jsonStr(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> append(c)
            }
        }
        append('"')
    }

    fun isInitialized(): Boolean = initialized

    private external fun nativeInitAssetManager(context: android.content.Context, cacheDir: String)

    companion object {
        private const val TAG = "LensEngine"
    }
}
