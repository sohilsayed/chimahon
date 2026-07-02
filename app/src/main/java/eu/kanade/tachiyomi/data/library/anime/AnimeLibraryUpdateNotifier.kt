package eu.kanade.tachiyomi.data.library.anime

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.download.Downloader
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.DelicateCoroutinesApi
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.math.RoundingMode
import java.text.NumberFormat

@OptIn(DelicateCoroutinesApi::class)
class AnimeLibraryUpdateNotifier(
    private val context: Context,

    private val securityPreferences: SecurityPreferences = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) {

    private val percentFormatter = NumberFormat.getPercentInstance().apply {
        roundingMode = RoundingMode.DOWN
        maximumFractionDigits = 0
    }

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    private val cancelIntent by lazy {
        NotificationReceiver.cancelAnimeLibraryUpdatePendingBroadcast(context)
    }

    val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.app_name))
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setLargeIcon(notificationBitmap)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel),
                cancelIntent,
            )
        }
    }

    fun showProgressNotification(anime: List<Anime>, current: Int, total: Int) {
        progressNotificationBuilder
            .setContentTitle(
                context.stringResource(
                    MR.strings.notification_updating_progress,
                    percentFormatter.format(current.toFloat() / total),
                ),
            )

        if (!securityPreferences.hideNotificationContent().get()) {
            val updatingText = anime.joinToString("\n") { it.title.chop(40) }
            progressNotificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(updatingText))
        }

        context.notify(
            Notifications.ID_LIBRARY_PROGRESS,
            progressNotificationBuilder
                .setProgress(total, current, false)
                .build(),
        )
    }

    fun showQueueSizeWarningNotificationIfNeeded(animeToUpdate: List<LibraryAnime>) {
        val maxUpdatesFromSource = animeToUpdate
            .groupBy { it.anime.source }
            .filterKeys { sourceManager.get(it) !is UnmeteredSource }
            .maxOfOrNull { it.value.size } ?: 0

        if (maxUpdatesFromSource <= ANIME_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
            return
        }

        context.notify(
            Notifications.ID_LIBRARY_SIZE_WARNING,
            Notifications.CHANNEL_LIBRARY_PROGRESS,
        ) {
            setContentTitle(context.stringResource(MR.strings.label_warning))
            setStyle(
                NotificationCompat.BigTextStyle().bigText(context.stringResource(MR.strings.notification_size_warning)),
            )
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setTimeoutAfter(Downloader.WARNING_NOTIF_TIMEOUT_MS)
            setContentIntent(NotificationHandler.openUrl(context, HELP_WARNING_URL))
        }
    }

    fun showUpdateErrorNotification(failed: Int, uri: Uri) {
        if (failed == 0) {
            return
        }

        context.notify(
            Notifications.ID_LIBRARY_ERROR,
            Notifications.CHANNEL_LIBRARY_ERROR,
        ) {
            setContentTitle(context.stringResource(MR.strings.notification_update_error, failed))
            setContentText(context.stringResource(MR.strings.action_show_errors))
            setSmallIcon(R.drawable.ic_chimahon)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setContentIntent(NotificationReceiver.openErrorLogPendingActivity(context, uri))
        }
    }

    fun showUpdateNotifications(updates: List<Pair<Anime, Array<Episode>>>) {
        context.notify(
            Notifications.ID_NEW_EPISODES,
            Notifications.CHANNEL_NEW_EPISODES,
        ) {
            setContentTitle(context.stringResource(MR.strings.notification_new_episodes))
            if (updates.size == 1 && !securityPreferences.hideNotificationContent().get()) {
                setContentText(updates.first().first.title.chop(NOTIF_TITLE_MAX_LEN))
            } else {
                setContentText(
                    context.pluralStringResource(
                        MR.plurals.notification_new_episodes_summary,
                        updates.size,
                        updates.size,
                    ),
                )

                if (!securityPreferences.hideNotificationContent().get()) {
                    setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            updates.joinToString("\n") { it.first.title.chop(NOTIF_TITLE_MAX_LEN) },
                        ),
                    )
                }
            }

            setSmallIcon(R.drawable.ic_chimahon)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))
            setLargeIcon(notificationBitmap)

            setGroup(Notifications.GROUP_NEW_EPISODES)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            setGroupSummary(true)
            priority = NotificationCompat.PRIORITY_HIGH

            setContentIntent(getNotificationIntent())
            setAutoCancel(true)
        }

        if (!securityPreferences.hideNotificationContent().get()) {
            launchUI {
                context.notify(
                    updates.map { (anime, episodes) ->
                        NotificationManagerCompat.NotificationWithIdAndTag(
                            anime.id.hashCode(),
                            createNewEpisodesNotification(anime, episodes),
                        )
                    },
                )
            }
        }
    }

    private suspend fun createNewEpisodesNotification(anime: Anime, episodes: Array<Episode>): Notification {
        val icon = getAnimeIcon(anime)
        return context.notificationBuilder(Notifications.CHANNEL_NEW_EPISODES) {
            setContentTitle(anime.title)

            val description = getNewEpisodesDescription(episodes)
            setContentText(description)
            setStyle(NotificationCompat.BigTextStyle().bigText(description))

            setSmallIcon(R.drawable.ic_chimahon)
            setColor(ContextCompat.getColor(context, R.color.ic_launcher))

            if (icon != null) {
                setLargeIcon(icon)
            }

            setGroup(Notifications.GROUP_NEW_EPISODES)
            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            priority = NotificationCompat.PRIORITY_HIGH

            setContentIntent(NotificationReceiver.openEpisodePendingActivity(context, anime, episodes.first()))
            setAutoCancel(true)

            addAction(
                R.drawable.ic_done_24dp,
                context.stringResource(MR.strings.action_mark_as_seen),
                NotificationReceiver.markAsSeenPendingBroadcast(
                    context,
                    anime,
                    episodes,
                    Notifications.ID_NEW_EPISODES,
                ),
            )
            addAction(
                R.drawable.ic_book_24dp,
                context.stringResource(MR.strings.action_view_episodes),
                NotificationReceiver.openEpisodePendingActivity(
                    context,
                    anime,
                    Notifications.ID_NEW_EPISODES,
                ),
            )
            if (episodes.size <= ANIME_PER_SOURCE_QUEUE_WARNING_THRESHOLD) {
                addAction(
                    android.R.drawable.stat_sys_download_done,
                    context.stringResource(MR.strings.action_download),
                    NotificationReceiver.downloadEpisodesPendingBroadcast(
                        context,
                        anime,
                        episodes,
                        Notifications.ID_NEW_EPISODES,
                    ),
                )
            }
        }.build()
    }

    fun cancelProgressNotification() {
        context.cancelNotification(Notifications.ID_LIBRARY_PROGRESS)
    }

    private suspend fun getAnimeIcon(anime: Anime): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(anime)
            .build()
        val drawable = context.imageLoader.execute(request).image?.asDrawable(context.resources)
        return drawable?.getBitmapOrNull()
    }

    private fun getNewEpisodesDescription(episodes: Array<Episode>): String {
        val displayableEpisodeNumbers = episodes
            .filter { it.isRecognizedNumber }
            .sortedBy { it.episodeNumber }
            .map { formatEpisodeNumber(it.episodeNumber) }
            .toSet()

        return when (displayableEpisodeNumbers.size) {
            0 -> {
                context.pluralStringResource(
                    MR.plurals.notification_episodes_generic,
                    episodes.size,
                    episodes.size,
                )
            }
            1 -> {
                val remaining = episodes.size - displayableEpisodeNumbers.size
                if (remaining == 0) {
                    context.stringResource(
                        MR.strings.notification_episodes_single,
                        displayableEpisodeNumbers.first(),
                    )
                } else {
                    context.stringResource(
                        MR.strings.notification_episodes_single_and_more,
                        displayableEpisodeNumbers.first(),
                        remaining,
                    )
                }
            }
            else -> {
                val shouldTruncate = displayableEpisodeNumbers.size > NOTIF_MAX_EPISODES
                if (shouldTruncate) {
                    val remaining = displayableEpisodeNumbers.size - NOTIF_MAX_EPISODES
                    val joinedEpisodeNumbers = displayableEpisodeNumbers
                        .take(NOTIF_MAX_EPISODES)
                        .joinToString(", ")
                    context.pluralStringResource(
                        MR.plurals.notification_episodes_multiple_and_more,
                        remaining,
                        joinedEpisodeNumbers,
                        remaining,
                    )
                } else {
                    context.stringResource(
                        MR.strings.notification_episodes_multiple,
                        displayableEpisodeNumbers.joinToString(", "),
                    )
                }
            }
        }
    }

    private fun getNotificationIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Constants.SHORTCUT_UPDATES
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val HELP_WARNING_URL = "https://komikku-app.github.io/docs/faq/library#why-am-i-warned-about-large-bulk-updates-and-downloads"
    }
}

private const val NOTIF_MAX_EPISODES = 5
private const val NOTIF_TITLE_MAX_LEN = 45
private const val ANIME_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60
