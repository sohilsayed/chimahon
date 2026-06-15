package eu.kanade.tachiyomi.data.ocr

import android.content.Context
import androidx.core.app.NotificationCompat
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
import java.util.zip.ZipInputStream

class ModelDownloader(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val RELEASE_BASE =
            "https://github.com/sohilsayed/chimahon-local-models/releases/latest/download"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isDownloaded: Boolean
        get() = File(context.filesDir, "screenai_models/lots_multiscript_v8_runner.binarypb").isFile

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

            body.byteStream().use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val target = File(context.filesDir, entry.name)
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

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}

private fun Context.cancelNotification(id: Int) {
    val manager = androidx.core.app.NotificationManagerCompat.from(this)
    manager.cancel(id)
}
