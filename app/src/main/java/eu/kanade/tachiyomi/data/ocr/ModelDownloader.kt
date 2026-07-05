package eu.kanade.tachiyomi.data.ocr

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class ModelDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val RELEASE_BASE =
            "https://github.com/sohilsayed/chimahon-local-models/releases/download/v2.0"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isDownloaded: Boolean
        get() = lensSupportedAbi()?.let { abi ->
            requiredLensFiles(abi).all { it.isFile && it.length() > 0L }
        } ?: false

    private fun lensSupportedAbi(): String? = when {
        "arm64-v8a" in Build.SUPPORTED_ABIS -> "arm64-v8a"
        "armeabi-v7a" in Build.SUPPORTED_ABIS -> "armeabi-v7a"
        else -> null
    }

    private fun requiredLensFiles(abi: String): List<File> {
        val root = File(context.filesDir, "screenai_models")
        return listOf(
            File(root, "lots_multiscript_v8_runner.binarypb"),
            File(root, "lots_multiscript_v8_engine_patched.binarypb"),
            File(root, "third_party/lens/line_detector/v688492737/gocr_group_rpn_text_detection_config_2024_q4.binarypb"),
            File(root, "third_party/lens/line_recognition/v678672708/recognizer_jpan.tflite"),
            File(root, "third_party/lens/line_recognition/v678672708/recognizer_jpan_lm.compact_fst.gz"),
            File(root, "lib/$abi/liblens_ondevice_engine_base.so"),
            File(root, "lib/$abi/liblens_ondevice_engine_play_ml.so"),
        )
    }

    fun triggerDownload() {
        if (isDownloaded) return
        scope.launch {
            context.notify(
                Notifications.ID_OCR_PROGRESS,
                Notifications.CHANNEL_OCR_MODEL_DOWNLOAD,
            ) {
                setSmallIcon(android.R.drawable.stat_sys_download)
                setContentTitle("Downloading OCR models")
                setContentText("Downloading on-device OCR models...")
                setOngoing(true)
                setOnlyAlertOnce(true)
            }
            val result = downloadAndExtract()
            context.cancelNotification(Notifications.ID_OCR_PROGRESS)
            if (result.isSuccess) {
                context.notify(
                    Notifications.ID_OCR_PROGRESS,
                    Notifications.CHANNEL_OCR_MODEL_DOWNLOAD,
                ) {
                    setSmallIcon(android.R.drawable.stat_sys_download_done)
                    setContentTitle("OCR models ready")
                    setContentText("On-device OCR models downloaded successfully")
                    setAutoCancel(true)
                    setOngoing(false)
                }
            } else {
                context.notify(
                    Notifications.ID_OCR_PROGRESS,
                    Notifications.CHANNEL_OCR_MODEL_DOWNLOAD,
                ) {
                    setSmallIcon(android.R.drawable.stat_sys_warning)
                    setContentTitle("OCR model download failed")
                    setContentText(result.exceptionOrNull()?.message ?: "Unknown error")
                    setAutoCancel(true)
                    setOngoing(false)
                }
            }
        }
    }

    suspend fun downloadAndExtract(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val url = "$RELEASE_BASE/models.zip"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    RuntimeException("Download failed: HTTP ${response.code}")
                )
            }

            val body = response.body ?: return@withContext Result.failure(
                RuntimeException("Empty response body")
            )

            body.byteStream().use { input -> extractZip(input) }

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val source = UniFile.fromUri(context, uri)
                ?: return@withContext Result.failure(RuntimeException("Could not open file"))
            source.openInputStream().use { extractZip(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractZip(input: InputStream) {
        val root = context.filesDir.canonicalFile
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(root, entry.name).canonicalFile
                check(target.path == root.path || target.path.startsWith(root.path + File.separator)) {
                    "Unsafe zip entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
            }
        }
    }
}

private fun Context.cancelNotification(id: Int) {
    val manager = androidx.core.app.NotificationManagerCompat.from(this)
    manager.cancel(id)
}
