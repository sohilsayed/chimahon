package eu.kanade.tachiyomi.data.dictionary

import android.content.Context
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

class DictionaryUpdateNotifier(private val context: Context) {

    private val progressBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DICTIONARY_UPDATE) {
            setSmallIcon(android.R.drawable.stat_sys_download)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setAutoCancel(false)
        }
    }

    private val checkingBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DICTIONARY_UPDATE) {
            setSmallIcon(eu.kanade.tachiyomi.R.drawable.ic_sync_24dp)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setAutoCancel(false)
        }
    }

    fun showChecking() {
        checkingBuilder.apply {
            setContentTitle("Checking for updates")
            setContentText("Looking for new dictionary versions…")
            setProgress(0, 0, true)
        }
        context.notify(Notifications.ID_DICT_UPDATE_CHECKING, checkingBuilder.build())
    }

    fun hideChecking() {
        context.cancelNotification(Notifications.ID_DICT_UPDATE_CHECKING)
    }

    fun showNoUpdates() {
        hideChecking()
        context.notify(
            Notifications.ID_DICT_UPDATE_NO_UPDATES,
            Notifications.CHANNEL_DICTIONARY_UPDATE,
        ) {
            setSmallIcon(eu.kanade.tachiyomi.R.drawable.ic_sync_24dp)
            setContentTitle("Dictionary check complete")
            setContentText("All dictionaries are up to date")
            setAutoCancel(true)
            setOngoing(false)
        }
    }

    fun showProgress(dictName: String, current: Int, total: Int) {
        progressBuilder.apply {
            setContentTitle(context.stringResource(MR.strings.dict_update_progress_title))
            setContentText(dictName)
            if (total > 0) {
                setProgress(total, current, false)
            } else {
                setProgress(0, 0, true)
            }
        }
        context.notify(Notifications.ID_DICT_UPDATE_PROGRESS, progressBuilder.build())
    }

    fun showComplete(updatedCount: Int) {
        hideChecking()
        context.cancelNotification(Notifications.ID_DICT_UPDATE_PROGRESS)

        if (updatedCount <= 0) return

        context.notify(
            Notifications.ID_DICT_UPDATE_COMPLETE,
            Notifications.CHANNEL_DICTIONARY_UPDATE,
        ) {
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentTitle(context.stringResource(MR.strings.dict_update_complete_title))
            setContentText(
                context.stringResource(MR.strings.dict_update_complete_text, updatedCount),
            )
            setAutoCancel(true)
            setOngoing(false)
        }
    }

    fun showError(dictName: String, error: String) {
        context.notify(
            Notifications.ID_DICT_UPDATE_PROGRESS,
            Notifications.CHANNEL_DICTIONARY_UPDATE,
        ) {
            setSmallIcon(android.R.drawable.stat_sys_warning)
            setContentTitle(context.stringResource(MR.strings.dict_update_error_title))
            setContentText("$dictName: $error")
            setAutoCancel(true)
            setOngoing(false)
        }
    }

    /**
     * Show a notification for a single updated dictionary with version info.
     * [index] is used to derive a unique notification ID so multiple updates
     * produce separate notifications.
     */
    fun showUpdateResult(dictName: String, oldRevision: String?, newRevision: String?, index: Int) {
        val versionText = buildString {
            append(dictName)
            append(": ")
            if (oldRevision != null) append(oldRevision) else append("?")
            append(" → ")
            if (newRevision != null) append(newRevision) else append("?")
        }
        context.notify(
            Notifications.ID_DICT_UPDATE_RESULT + index,
            Notifications.CHANNEL_DICTIONARY_UPDATE,
        ) {
            setSmallIcon(android.R.drawable.stat_sys_download_done)
            setContentTitle("Dictionary updated")
            setContentText(versionText)
            setAutoCancel(true)
            setOngoing(false)
        }
    }
}
