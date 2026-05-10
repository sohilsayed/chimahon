package eu.kanade.tachiyomi.data.animedownload

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.asFlow
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.NetworkState
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.networkStateFlow
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDownloadJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val animeDownloadManager: AnimeDownloadManager by lazy { Injekt.get() }
    private val downloadPreferences: DownloadPreferences by lazy { Injekt.get() }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_ANIME_DOWNLOADER_PROGRESS) {
            setContentTitle(applicationContext.getString(R.string.download_notifier_downloader_title))
            setSmallIcon(android.R.drawable.stat_sys_download)
            setColor(ContextCompat.getColor(applicationContext, R.color.ic_launcher))
        }.build()
        return ForegroundInfo(
            Notifications.ID_ANIME_DOWNLOAD_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override suspend fun doWork(): Result {
        var networkCheck = checkNetworkState(
            applicationContext.activeNetworkState(),
            downloadPreferences.downloadOnlyOverWifi().get(),
        )
        var active = networkCheck && animeDownloadManager.downloaderStart()

        if (!active) {
            logcat(LogPriority.WARN) { "AnimeDownloadJob: not active (networkCheck=$networkCheck)" }
            return Result.failure()
        }
        logcat(LogPriority.INFO) { "AnimeDownloadJob: started successfully" }

        setForegroundSafely()

        coroutineScope {
            combineTransform(
                applicationContext.networkStateFlow(),
                downloadPreferences.downloadOnlyOverWifi().changes(),
                transform = { a, b -> emit(checkNetworkState(a, b)) },
            )
                .onEach { networkCheck = it }
                .launchIn(this)
        }

        while (active) {
            active = !isStopped && animeDownloadManager.isRunning && networkCheck
        }

        return Result.success()
    }

    private fun checkNetworkState(state: NetworkState, requireWifi: Boolean): Boolean {
        return if (state.isOnline) {
            val noWifi = requireWifi && !state.isWifi
            if (noWifi) {
                animeDownloadManager.downloaderStop(
                    applicationContext.getString(R.string.download_notifier_text_only_wifi),
                )
            }
            !noWifi
        } else {
            animeDownloadManager.downloaderStop(
                applicationContext.getString(R.string.download_notifier_no_network),
            )
            false
        }
    }

    companion object {
        private const val TAG = "AnimeDownloader"

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<AnimeDownloadJob>()
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
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

        fun isRunningFlow(context: Context): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkLiveData(TAG)
                .asFlow()
                .map { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }
        }
    }
}
