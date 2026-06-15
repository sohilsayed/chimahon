package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class CastManager(private val context: Context) {

    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var currentSession: CastSession? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            currentSession = session
            logcat(LogPriority.DEBUG, tag = "Cast") { "Cast session started" }
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            logcat(LogPriority.ERROR, tag = "Cast") { "Cast session start failed: $error" }
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {
            currentSession = null
            logcat(LogPriority.DEBUG, tag = "Cast") { "Cast session ended" }
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            currentSession = session
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    fun initialize() {
        try {
            castContext = CastContext.getSharedInstance(context)
            sessionManager = castContext?.sessionManager
            sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
            currentSession = sessionManager?.currentCastSession
        } catch (e: Exception) {
            logcat(LogPriority.WARN, tag = "Cast") { "Cast not available: ${e.message}" }
        }
    }

    fun release() {
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        currentSession = null
    }

    val isConnected: Boolean
        get() = currentSession?.isConnected == true

    fun loadMedia(
        url: String,
        title: String,
        episodeName: String,
        thumbnailUrl: String? = null,
        startPositionMs: Long = 0,
    ) {
        val session = currentSession ?: return
        val remoteClient = session.remoteMediaClient ?: return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            putString(MediaMetadata.KEY_SUBTITLE, episodeName)
            if (!thumbnailUrl.isNullOrBlank()) {
                addImage(WebImage(Uri.parse(thumbnailUrl)))
            }
        }

        val mediaInfo = MediaInfo.Builder(url)
            .setMetadata(metadata)
            .build()

        val requestData = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(startPositionMs)
            .build()

        remoteClient.load(requestData)
    }

    fun pause() {
        currentSession?.remoteMediaClient?.pause()
    }

    fun play() {
        currentSession?.remoteMediaClient?.play()
    }

    fun seek(positionMs: Long) {
        currentSession?.remoteMediaClient?.seek(positionMs)
    }

    fun stop() {
        currentSession?.remoteMediaClient?.stop()
    }
}
