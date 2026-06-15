package eu.kanade.tachiyomi.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.ui.player.setting.PlayerPreferences
import kotlinx.coroutines.DelicateCoroutinesApi
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.animesource.service.AnimeSourceManager
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File

class ExternalIntents {

    lateinit var anime: Anime
    lateinit var episode: Episode

    var animeId: Long? = null
    var episodeId: Long? = null

    suspend fun getExternalIntent(
        context: Context,
        animeId: Long,
        episodeId: Long,
        videoUrl: String,
        video: Video?,
    ): Intent? {
        if (!initAnime(animeId, episodeId)) return null

        val uri = videoUrl.toUri()
        val pkgName = playerPreferences.externalPlayerPreference().get()

        return if (pkgName.isEmpty()) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndTypeAndNormalize(uri, getMime(uri))
                addExtrasAndFlags(false, this)
                addVideoHeaders(false, video, this)
            }
        } else {
            getIntentForPackage(pkgName, context, uri, video)
        }
    }

    private suspend fun initAnime(animeId: Long, episodeId: Long): Boolean {
        anime = getAnime.await(animeId) ?: return false
        episode = getEpisodesByAnimeId.await(anime.id).find { it.id == episodeId } ?: return false
        this.animeId = animeId
        this.episodeId = episodeId
        return true
    }

    private fun getLastSecondSeen(): Long {
        val preserveWatchPos = playerPreferences.preserveWatchingPosition().get()
        val isEpisodeWatched = episode.lastSecondSeen == episode.totalSeconds
        return if (episode.seen && (!preserveWatchPos || (preserveWatchPos && isEpisodeWatched))) {
            1L
        } else {
            episode.lastSecondSeen
        }
    }

    private fun getIntentForPackage(pkgName: String, context: Context, uri: Uri, video: Video?): Intent {
        return when (pkgName) {
            WEB_VIDEO_CASTER -> webVideoCasterIntent(pkgName, context, uri, video)
            else -> standardIntentForPackage(pkgName, context, uri, video)
        }
    }

    private fun webVideoCasterIntent(pkgName: String, context: Context, uri: Uri, video: Video?): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            if (isPackageInstalled(pkgName, context.packageManager)) setPackage(WEB_VIDEO_CASTER)
            addExtrasAndFlags(true, this)

            val headers = Bundle()
            video?.headers?.forEach {
                headers.putString(it.first, it.second)
            }

            video?.subtitleTracks?.firstOrNull()?.let {
                putExtra("subtitle", it.url)
            }

            putExtra("android.media.intent.extra.HTTP_HEADERS", headers)
            putExtra("secure_uri", true)
        }
    }

    private fun standardIntentForPackage(pkgName: String, context: Context, uri: Uri, video: Video?): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            if (isPackageInstalled(pkgName, context.packageManager)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && pkgName.contains("vlc")) {
                    setPackage(pkgName)
                } else {
                    component = getComponent(pkgName)
                }
            }
            setDataAndType(uri, "video/*")
            addExtrasAndFlags(true, this)
            addVideoHeaders(true, video, this)

            video?.let { v ->
                putExtra("subs", v.subtitleTracks.map { Uri.parse(it.url) }.toTypedArray())
                putExtra("subs.name", v.subtitleTracks.map { it.lang }.toTypedArray())
                v.subtitleTracks.firstOrNull()?.let { sub ->
                    putExtra("subs.enable", arrayOf(Uri.parse(sub.url)))
                    putExtra("subtitles_location", sub.url)
                }
            }
        }
    }

    private fun addExtrasAndFlags(isSupportedPlayer: Boolean, intent: Intent): Intent {
        return intent.apply {
            putExtra("title", anime.title + " - " + episode.name)
            putExtra("position", getLastSecondSeen().toInt())
            putExtra("return_result", true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (isSupportedPlayer) putExtra("secure_uri", true)
        }
    }

    private fun addVideoHeaders(isSupportedPlayer: Boolean, video: Video?, intent: Intent): Intent {
        return intent.apply {
            val headers = video?.headers ?: return@apply
            var headersArray = arrayOf<String>()
            for (header in headers) {
                headersArray += arrayOf(header.first, header.second)
            }
            putExtra("headers", headersArray)
            if (!isSupportedPlayer) {
                val headersString = headersArray.toList()
                    .chunked(2) { "${it[0]}: ${it.getOrElse(1) { "" }}" }
                    .joinToString("\r\n")
                putExtra("http-header-fields", headersString)
            }
        }
    }

    private fun getMime(uri: Uri): String {
        return when (uri.path?.substringAfterLast(".")) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "m3u8" -> "application/x-mpegURL"
            else -> "video/any"
        }
    }

    private fun getComponent(packageName: String): ComponentName? {
        return when (packageName) {
            MPV_PLAYER -> ComponentName(packageName, "$packageName.MPVActivity")
            MX_PLAYER, MX_PLAYER_FREE, MX_PLAYER_PRO -> ComponentName(
                packageName,
                "$packageName.ActivityScreen",
            )
            VLC_PLAYER -> ComponentName(packageName, "$packageName.gui.video.VideoPlayerActivity")
            MPV_KT, MPV_KT_PREVIEW -> ComponentName(packageName, "live.mehiz.mpvkt.ui.player.PlayerActivity")
            MPV_REMOTE -> ComponentName(packageName, "$packageName.MainActivity")
            JUST_PLAYER -> ComponentName(packageName, "$packageName.PlayerActivity")
            NEXT_PLAYER -> ComponentName(packageName, "$packageName.feature.player.PlayerActivity")
            X_PLAYER -> ComponentName(packageName, "com.inshot.xplayer.activities.PlayerActivity")
            else -> null
        }
    }

    private fun isPackageInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("DEPRECATION")
    fun onActivityResult(intent: Intent?) {
        val data = intent ?: return
        if (animeId == null || episodeId == null) return

        val currentPosition: Long
        val duration: Long
        val cause = data.getStringExtra("end_by") ?: ""

        if (cause.isNotEmpty()) {
            val positionExtra = data.extras?.get("position")
            currentPosition = if (positionExtra is Int) {
                positionExtra.toLong()
            } else {
                positionExtra as? Long ?: 0L
            }
            val durationExtra = data.extras?.get("duration")
            duration = if (durationExtra is Int) {
                durationExtra.toLong()
            } else {
                durationExtra as? Long ?: 0L
            }
        } else {
            if (data.extras?.get("extra_position") != null) {
                currentPosition = data.getLongExtra("extra_position", 0L)
                duration = data.getLongExtra("extra_duration", 0L)
            } else {
                currentPosition = data.getIntExtra("position", 0).toLong()
                duration = data.getIntExtra("duration", 0).toLong()
            }
        }

        launchIO {
            if (cause == "playback_completion" || (currentPosition == duration && duration == 0L)) {
                saveEpisodeProgress(episode.totalSeconds, episode.totalSeconds)
            } else {
                saveEpisodeProgress(currentPosition, duration)
            }
        }
    }

    private suspend fun saveEpisodeProgress(lastSecondSeen: Long, totalSeconds: Long) {
        if (totalSeconds > 0L) {
            val progress = playerPreferences.progressPreference().get()
            val seen = if (!episode.seen) lastSecondSeen >= totalSeconds * progress else true
            updateEpisode.await(
                EpisodeUpdate(
                    id = episode.id,
                    seen = seen,
                    lastSecondSeen = lastSecondSeen,
                    totalSeconds = totalSeconds,
                ),
            )
        }
    }

    private val updateEpisode: UpdateEpisode = Injekt.get()
    private val getAnime: GetAnime = Injekt.get()
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get()
    private val playerPreferences: PlayerPreferences = Injekt.get()

    companion object {
        val externalIntents: ExternalIntents by injectLazy()

        suspend fun newIntent(context: Context, animeId: Long, episodeId: Long, videoUrl: String, video: Video?): Intent? {
            return externalIntents.getExternalIntent(context, animeId, episodeId, videoUrl, video)
        }
    }
}

const val MPV_PLAYER = "is.xyz.mpv"
const val MX_PLAYER = "com.mxtech.videoplayer"
const val MX_PLAYER_FREE = "com.mxtech.videoplayer.ad"
const val MX_PLAYER_PRO = "com.mxtech.videoplayer.pro"
const val VLC_PLAYER = "org.videolan.vlc"
const val MPV_KT = "live.mehiz.mpvkt"
const val MPV_KT_PREVIEW = "live.mehiz.mpvkt.preview"
const val MPV_REMOTE = "com.husudosu.mpvremote"
const val JUST_PLAYER = "com.brouken.player"
const val NEXT_PLAYER = "dev.anilbeesetti.nextplayer"
const val X_PLAYER = "video.player.videoplayer"
const val WEB_VIDEO_CASTER = "com.instantbits.cast.webvideo"
