package eu.kanade.tachiyomi.data.animedownload

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.util.regex.Pattern

internal class AnimeDownloadNotifier(private val context: Context) {

    private val preferences: SecurityPreferences by injectLazy()

    private val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_ANIME_DOWNLOADER_PROGRESS) {
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setAutoCancel(false)
            setOnlyAlertOnce(true)
        }
    }

    private val errorNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_ANIME_DOWNLOADER_ERROR) {
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setAutoCancel(false)
        }
    }

    private var isDownloading = false

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    fun dismissProgress() {
        context.cancelNotification(Notifications.ID_ANIME_DOWNLOAD_PROGRESS)
        context.cancelNotification(Notifications.ID_ANIME_DOWNLOAD_PAUSED)
    }

    fun dismissPaused() {
        context.cancelNotification(Notifications.ID_ANIME_DOWNLOAD_PAUSED)
    }

    fun onProgressChange(download: AnimeDownload) {
        with(progressNotificationBuilder) {
            if (!isDownloading) {
                setSmallIcon(android.R.drawable.stat_sys_download)
                clearActions()
                setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
                isDownloading = true
                addAction(
                    R.drawable.ic_pause_24dp,
                    context.stringResource(MR.strings.action_pause),
                    NotificationReceiver.pauseDownloadsPendingBroadcast(context),
                )
            }

            val title = if (preferences.hideNotificationContent().get()) {
                context.stringResource(MR.strings.download_notifier_downloader_title)
            } else {
                val animeTitle = download.anime.title.chop(15)
                val quotedTitle = Pattern.quote(animeTitle)
                val episode = download.episode.name.replaceFirst(
                    "$quotedTitle[\\s]*[-]*[\\s]*".toRegex(RegexOption.IGNORE_CASE),
                    "",
                )
                "$animeTitle - $episode".chop(30)
            }
            setContentTitle(title)
            setContentText("${download.progress}%")
            setProgress(100, download.progress, false)
            setOngoing(true)

            show(Notifications.ID_ANIME_DOWNLOAD_PROGRESS)
        }
    }

    fun onPaused() {
        with(progressNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.chapter_paused))
            setContentText(context.stringResource(MR.strings.download_notifier_download_paused))
            setSmallIcon(R.drawable.ic_pause_24dp)
            setProgress(0, 0, false)
            setOngoing(false)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            addAction(
                R.drawable.ic_play_arrow_24dp,
                context.stringResource(MR.strings.action_resume),
                NotificationReceiver.resumeDownloadsPendingBroadcast(context),
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel_all),
                NotificationReceiver.clearDownloadsPendingBroadcast(context),
            )

            show(Notifications.ID_ANIME_DOWNLOAD_PAUSED)
        }

        isDownloading = false
    }

    fun onComplete() {
        dismissProgress()
        isDownloading = false
    }

    fun onError(error: String? = null, episodeName: String? = null, animeTitle: String? = null) {
        with(errorNotificationBuilder) {
            setContentTitle(
                animeTitle?.plus(": $episodeName")
                    ?: context.stringResource(MR.strings.download_notifier_downloader_title),
            )
            setContentText(error ?: context.stringResource(MR.strings.download_notifier_unknown_error))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            setProgress(0, 0, false)

            show(Notifications.ID_ANIME_DOWNLOAD_ERROR)
        }

        isDownloading = false
    }
}
