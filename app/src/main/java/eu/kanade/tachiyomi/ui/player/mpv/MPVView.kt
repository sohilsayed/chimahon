package eu.kanade.tachiyomi.ui.player.mpv

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import `is`.xyz.mpv.MPVLib
import java.io.File
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class MPVView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var isInitialized = false
    private var lastSurfaceWidth = -1
    private var lastSurfaceHeight = -1

    init {
        holder.addCallback(this)
    }

    fun initialize(configDir: String, cacheDir: String) {
        if (isInitialized) return

        File(configDir).mkdirs()
        File(cacheDir).mkdirs()
        copyAssets(context, configDir)

        MPVLib.create(context, if (isDebugBuildType) "v" else "warn")
        MPVLib.addLogObserver(logObserver)

        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", configDir)
        MPVLib.setOptionString("gpu-shader-cache-dir", cacheDir)
        MPVLib.setOptionString("icc-cache-dir", cacheDir)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("ao", "audiotrack")
        MPVLib.setOptionString("hwdec", "mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs", "all")
        MPVLib.setOptionString("save-position-on-quit", "no")
        MPVLib.setOptionString("keep-open", "always")
        MPVLib.setOptionString("demux-max-bytes", "32MiB")
        MPVLib.setOptionString("demux-max-back-bytes", "16MiB")
        MPVLib.setOptionString("sub-auto", "fuzzy")
        MPVLib.setOptionString("sub-font-size", "36")
        MPVLib.setOptionString("sub-border-size", "3")

        try {
            val display = ContextCompat.getDisplayOrDefault(context)
            val fps = display.mode.refreshRate
            MPVLib.setOptionString("display-fps-override", fps.toString())
        } catch (_: Exception) {}

        MPVLib.init()
        isInitialized = true

        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "once")
        MPVLib.setPropertyString("sub-visibility", "no")

        MPVLib.observeProperty(PROP_TIME_POS, MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty(PROP_DURATION, MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty(PROP_PAUSE, MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty(PROP_EOF_REACHED, MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty(PROP_SUB_TEXT, MPVLib.mpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty(PROP_TRACK_LIST, MPVLib.mpvFormat.MPV_FORMAT_NONE)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        logcat(LogPriority.DEBUG, tag = "Player") { "surfaceCreated, isInitialized=$isInitialized" }
        if (isInitialized) {
            MPVLib.attachSurface(holder.surface)
            MPVLib.setOptionString("force-window", "yes")
            logcat(LogPriority.DEBUG, tag = "Player") { "Surface attached" }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (width == lastSurfaceWidth && height == lastSurfaceHeight) return
        lastSurfaceWidth = width
        lastSurfaceHeight = height
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
    }

    fun playFile(url: String, startPositionSec: Long = 0) {
        if (!isInitialized) {
            logcat(LogPriority.ERROR) { "playFile called but MPV not initialized" }
            return
        }
        logcat(LogPriority.DEBUG, tag = "Player") { "MPV loadfile: $url start=$startPositionSec" }
        if (startPositionSec > 0) {
            MPVLib.setOptionString("start", "+$startPositionSec")
        } else {
            MPVLib.setOptionString("start", "none")
        }
        MPVLib.command(arrayOf("loadfile", url))
    }

    val timePos: Int?
        get() = MPVLib.getPropertyInt(PROP_TIME_POS)

    val duration: Int?
        get() = MPVLib.getPropertyInt(PROP_DURATION)

    val paused: Boolean
        get() = MPVLib.getPropertyBoolean(PROP_PAUSE) ?: false

    fun cyclePause() {
        MPVLib.command(arrayOf("cycle", "pause"))
    }

    fun seekTo(positionSec: Int) {
        if (!isInitialized) return
        MPVLib.command(arrayOf("seek", positionSec.toString(), "absolute"))
    }

    fun seekRelative(deltaSec: Int) {
        if (!isInitialized) return
        MPVLib.command(arrayOf("seek", deltaSec.toString(), "relative"))
    }

    fun pause() {
        if (!isInitialized) return
        MPVLib.setPropertyBoolean(PROP_PAUSE, true)
    }

    fun resume() {
        if (!isInitialized) return
        MPVLib.setPropertyBoolean(PROP_PAUSE, false)
    }

    var sid: Int
        get() = MPVLib.getPropertyString("sid")?.toIntOrNull() ?: -1
        set(value) {
            if (value == -1) MPVLib.setPropertyString("sid", "no")
            else MPVLib.setPropertyInt("sid", value)
        }

    var aid: Int
        get() = MPVLib.getPropertyString("aid")?.toIntOrNull() ?: -1
        set(value) {
            if (value == -1) MPVLib.setPropertyString("aid", "no")
            else MPVLib.setPropertyInt("aid", value)
        }

    data class Track(val id: Int, val name: String, val language: String?)

    fun loadTracks(): Pair<List<Track>, List<Track>> {
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        val subs = mutableListOf<Track>()
        val audio = mutableListOf<Track>()
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val title = MPVLib.getPropertyString("track-list/$i/title") ?: ""
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
            val displayName = buildString {
                append("#$id")
                if (title.isNotBlank()) append(" $title")
                if (!lang.isNullOrBlank()) append(" ($lang)")
            }
            val track = Track(id, displayName, lang)
            when (type) {
                "sub" -> subs.add(track)
                "audio" -> audio.add(track)
            }
        }
        return subs to audio
    }

    fun grabThumbnail(width: Int): Bitmap? {
        if (!isInitialized) return null
        return try {
            MPVLib.grabThumbnail(width)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to grab thumbnail" }
            null
        }
    }

    fun destroy() {
        if (!isInitialized) return
        isInitialized = false
        try {
            MPVLib.removeLogObserver(logObserver)
            MPVLib.destroy()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Error destroying MPV" }
        }
    }

    companion object {
        const val PROP_TIME_POS = "time-pos"
        const val PROP_DURATION = "duration"
        const val PROP_PAUSE = "pause"
        const val PROP_EOF_REACHED = "eof-reached"
        const val PROP_SUB_TEXT = "sub-text"
        const val PROP_TRACK_LIST = "track-list"

        private val logObserver = MPVLib.LogObserver { prefix, level, text ->
            logcat(LogPriority.DEBUG, tag = "mpv") { "[$prefix] $text" }
        }

        private fun copyAssets(context: Context, configDir: String) {
            val assetFiles = arrayOf("subfont.ttf", "cacert.pem")
            for (fileName in assetFiles) {
                try {
                    val outFile = File(configDir, fileName)
                    if (outFile.exists()) continue
                    context.assets.open(fileName, AssetManager.ACCESS_STREAMING)
                        .use { input -> outFile.outputStream().use { output -> input.copyTo(output) } }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN) { "Failed to copy asset $fileName: ${e.message}" }
                }
            }
        }
    }
}
