package eu.kanade.tachiyomi.ui.player.mpv

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.ui.player.Debanding
import eu.kanade.tachiyomi.ui.player.VideoFilters
import eu.kanade.tachiyomi.ui.player.setting.AudioChannels
import eu.kanade.tachiyomi.ui.player.setting.AudioPreferences
import eu.kanade.tachiyomi.ui.player.setting.DecoderPreferences
import eu.kanade.tachiyomi.ui.player.setting.SubtitlePreferences
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

    fun initialize(
        configDir: String,
        cacheDir: String,
        decoderPreferences: DecoderPreferences? = null,
        audioPreferences: AudioPreferences? = null,
        subtitlePreferences: SubtitlePreferences? = null,
    ) {
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
        MPVLib.setOptionString("vo", if (decoderPreferences?.gpuNext()?.get() == true) "gpu-next" else "gpu")
        MPVLib.setOptionString("ao", "audiotrack")

        val hwdec = if (decoderPreferences?.tryHWDecoding()?.get() != false) "mediacodec-copy" else "no"
        MPVLib.setOptionString("hwdec", hwdec)
        MPVLib.setOptionString("hwdec-codecs", "all")
        MPVLib.setOptionString("save-position-on-quit", "no")
        MPVLib.setOptionString("keep-open", "always")
        MPVLib.setOptionString("demux-max-bytes", "32MiB")
        MPVLib.setOptionString("demux-max-back-bytes", "16MiB")
        MPVLib.setOptionString("sub-auto", "fuzzy")

        if (decoderPreferences?.useYUV420P()?.get() != false) {
            MPVLib.setOptionString("vf", "format=yuv420p")
        }

        when (decoderPreferences?.videoDebanding()?.get()) {
            Debanding.CPU -> MPVLib.setOptionString("deband", "yes")
            Debanding.GPU -> {
                MPVLib.setOptionString("deband", "yes")
                MPVLib.setOptionString("deband-grain", "0")
            }
            else -> {}
        }

        applySubtitleOptions(subtitlePreferences)
        applyAudioOptions(audioPreferences)

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

        applyVideoFilters(decoderPreferences)

        MPVLib.observeProperty(PROP_TIME_POS, MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty(PROP_DURATION, MPVLib.mpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty(PROP_PAUSE, MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty(PROP_EOF_REACHED, MPVLib.mpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty(PROP_SUB_TEXT, MPVLib.mpvFormat.MPV_FORMAT_STRING)
        MPVLib.observeProperty(PROP_TRACK_LIST, MPVLib.mpvFormat.MPV_FORMAT_NONE)
        MPVLib.observeProperty("chapter-list", MPVLib.mpvFormat.MPV_FORMAT_NONE)
    }

    private fun applySubtitleOptions(prefs: SubtitlePreferences?) {
        if (prefs == null) {
            MPVLib.setOptionString("sub-font-size", "36")
            MPVLib.setOptionString("sub-border-size", "3")
            return
        }
        MPVLib.setOptionString("sub-font-size", prefs.subtitleFontSize().get().toString())
        MPVLib.setOptionString("sub-border-size", prefs.subtitleBorderSize().get().toString())
        MPVLib.setOptionString("sub-shadow-offset", prefs.shadowOffsetSubtitles().get().toString())
        MPVLib.setOptionString("sub-bold", if (prefs.boldSubtitles().get()) "yes" else "no")
        MPVLib.setOptionString("sub-italic", if (prefs.italicSubtitles().get()) "yes" else "no")
        MPVLib.setOptionString("sub-scale", prefs.subtitleFontScale().get().toString())
        MPVLib.setOptionString("sub-pos", prefs.subtitlePos().get().toString())
        if (prefs.overrideSubsASS().get()) {
            MPVLib.setOptionString("sub-ass-override", "force")
        }
        val subDelay = prefs.subtitlesDelay().get()
        if (subDelay != 0) {
            MPVLib.setOptionString("sub-delay", (subDelay / 1000.0).toString())
        }
        val subSpeed = prefs.subtitlesSpeed().get()
        if (subSpeed != 1f) {
            MPVLib.setOptionString("sub-speed", subSpeed.toString())
        }
    }

    private fun applyAudioOptions(prefs: AudioPreferences?) {
        if (prefs == null) return
        if (prefs.enablePitchCorrection().get()) {
            MPVLib.setOptionString("audio-pitch-correction", "yes")
        } else {
            MPVLib.setOptionString("audio-pitch-correction", "no")
        }
        val channels = prefs.audioChannels().get()
        if (channels == AudioChannels.ReverseStereo) {
            MPVLib.setOptionString("af", "pan=[stereo|c0=c1|c1=c0]")
        } else {
            MPVLib.setOptionString(channels.property, channels.value)
        }
        val boostCap = prefs.volumeBoostCap().get()
        if (boostCap > 0) {
            MPVLib.setOptionString("volume-max", (100 + boostCap).toString())
        }
        val audioDelay = prefs.audioDelay().get()
        if (audioDelay != 0) {
            MPVLib.setOptionString("audio-delay", (audioDelay / 1000.0).toString())
        }
    }

    private fun applyVideoFilters(prefs: DecoderPreferences?) {
        if (prefs == null) return
        val brightness = prefs.brightnessFilter().get()
        val saturation = prefs.saturationFilter().get()
        val contrast = prefs.contrastFilter().get()
        val gamma = prefs.gammaFilter().get()
        val hue = prefs.hueFilter().get()
        if (brightness != 0) MPVLib.setPropertyInt(VideoFilters.BRIGHTNESS.mpvProperty, brightness)
        if (saturation != 0) MPVLib.setPropertyInt(VideoFilters.SATURATION.mpvProperty, saturation)
        if (contrast != 0) MPVLib.setPropertyInt(VideoFilters.CONTRAST.mpvProperty, contrast)
        if (gamma != 0) MPVLib.setPropertyInt(VideoFilters.GAMMA.mpvProperty, gamma)
        if (hue != 0) MPVLib.setPropertyInt(VideoFilters.HUE.mpvProperty, hue)
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
        lastSurfaceWidth = -1
        lastSurfaceHeight = -1
    }

    fun playFile(url: String, startPositionSec: Long = 0, headers: Map<String, String>? = null) {
        if (!isInitialized) {
            logcat(LogPriority.ERROR) { "playFile called but MPV not initialized" }
            return
        }
        logcat(LogPriority.DEBUG, tag = "Player") { "MPV loadfile: $url start=$startPositionSec" }
        applyHeaders(headers)
        if (startPositionSec > 0) {
            MPVLib.setOptionString("start", "+$startPositionSec")
        } else {
            MPVLib.setOptionString("start", "none")
        }
        MPVLib.command(arrayOf("loadfile", url))
    }

    private fun applyHeaders(headers: Map<String, String>?) {
        val ua = headers?.entries?.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value ?: ""
        val ref = headers?.entries?.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value ?: ""

        MPVLib.setOptionString("user-agent", ua)
        MPVLib.setOptionString("referrer", ref)

        val extraHeaders = headers?.filterKeys { key ->
            !key.equals("User-Agent", ignoreCase = true) && !key.equals("Referer", ignoreCase = true)
        }.orEmpty()

        if (extraHeaders.isEmpty()) {
            MPVLib.setOptionString("http-header-fields", "")
            MPVLib.setOptionString("demuxer-lavf-o", "")
        } else {
            val headerFields = extraHeaders.entries.joinToString(",") { "${it.key}: ${it.value}" }
            MPVLib.setOptionString("http-header-fields", headerFields)
            val ffmpegHeaders = extraHeaders.entries.joinToString("\r\n") { "${it.key}: ${it.value}" } + "\r\n"
            MPVLib.setOptionString("demuxer-lavf-o", "headers=$ffmpegHeaders")
        }
    }

    fun addSubtitleTrack(url: String, lang: String) {
        if (!isInitialized) return
        MPVLib.command(arrayOf("sub-add", url, "auto", lang, lang))
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
