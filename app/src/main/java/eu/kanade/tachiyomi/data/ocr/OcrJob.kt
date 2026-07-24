package eu.kanade.tachiyomi.data.ocr

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class OcrJob(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val ocrManager: OcrManager = Injekt.get()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.ocr_running))
            setSmallIcon(android.R.drawable.stat_sys_download_done)
        }.build()
        return ForegroundInfo(
            Notifications.ID_OCR_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        // Show foreground notification immediately to keep worker alive
        setForegroundSafely()

        return try {
            ocrManager.runPendingQueue(stopRequested = { isStopped })
            Result.success()
        } catch (e: CancellationException) {
            logcat(LogPriority.WARN, e) { "OcrJob cancelled while processing OCR queue" }
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "OcrJob failed" }
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "OcrJob"

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<OcrJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(TAG)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(TAG)
                .get()
                .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
