package com.canopus.chimareader.ui.reader

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.canopus.chimareader.data.BookStorage
import com.canopus.chimareader.data.FileNames
import com.canopus.chimareader.data.SasayakiMatch
import com.canopus.chimareader.data.SasayakiMatchData
import com.canopus.chimareader.data.SasayakiPlaybackData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class CueTimeline(matchData: SasayakiMatchData?) {
    private val cues: List<SasayakiMatch> = matchData?.matches ?: emptyList()

    fun nextCue(afterTime: Double): Double? {
        var index = findCue(afterTime)
        if (index < cues.size && cues[index].startTime == afterTime) {
            index++
        }
        return if (index < cues.size) cues[index].startTime else null
    }

    fun prevCue(beforeTime: Double): Double? {
        val index = findCue(beforeTime)
        return if (index > 0) cues[index - 1].startTime else null
    }

    fun cueAt(time: Double): SasayakiMatch? {
        val index = findCue(time)
        if (index < cues.size && abs(cues[index].startTime - time) <= 0.01) {
            return cues[index]
        }
        if (index == 0) return null
        val cue = cues[index - 1]
        return if (time <= cue.endTime) cue else null
    }

    private fun findCue(time: Double): Int {
        var low = 0
        var high = cues.size
        while (low < high) {
            val mid = (low + high) / 2
            if (cues[mid].startTime < time) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low
    }
}

class SasayakiPlayer(
    private val context: Context,
    private val rootDir: File,
    private val bridge: WebViewBridge,
    private val loadChapter: (Int) -> Unit,
    private val getCurrentIndex: () -> Int,
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var player: ExoPlayer? = null

    var matchData: SasayakiMatchData? = null
        private set
    var timeline = CueTimeline(null)
        private set

    var playback = SasayakiPlaybackData(lastPosition = 0.0)
    var isPlaying = false
    var duration: Double = 0.0
    var currentTime: Double = 0.0

    var delay: Double = 0.0
        set(value) {
            field = value
            savePlayback()
            updateCue(currentTime)
        }

    var currentCue: SasayakiMatch? = null
    var pendingCue: SasayakiMatch? = null
    var chapterTransition = false
    private var shouldResume = false
    private var stopPlaybackTime: Double? = null

    var hasPlayedOnce = false
    var autoScroll = true // Mapped from Settings ideally

    private var progressTrackerJob: Job? = null

    val hasAudio: Boolean get() = player != null
    val hasMatch: Boolean get() = matchData != null

    init {
        matchData = BookStorage.loadSasayakiMatchData(rootDir)
        if (matchData != null) {
            timeline = CueTimeline(matchData)
            playback = BookStorage.loadSasayakiPlaybackData(rootDir) ?: SasayakiPlaybackData(lastPosition = 0.0)
            currentTime = playback.lastPosition
            delay = playback.delay
            restoreAudioIfNeeded()
        }
    }

    private fun restoreAudioIfNeeded() {
        val uriStr = playback.audioBookmark ?: return
        if (uriStr.isNotEmpty()) {
            val file = File(uriStr)
            if (file.exists()) {
                setupPlayer(file)
            }
        }
    }

    fun importAudio(file: File) {
        teardown()
        playback.audioBookmark = file.absolutePath
        savePlayback()
        setupPlayer(file)
    }

    private fun setupPlayer(file: File) {
        player = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(file.toURI().toString())
            setMediaItem(mediaItem)
            prepare()
            seekTo((currentTime * 1000).toLong())

            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    this@SasayakiPlayer.isPlaying = isPlayingNow
                    if (isPlayingNow) {
                        hasPlayedOnce = true
                        startProgressTracker()
                    } else {
                        stopProgressTracker()
                        savePlayback()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        this@SasayakiPlayer.isPlaying = false
                        stopPlaybackTime = null
                    }
                }
            })
        }
    }

    fun togglePlayback() {
        val exoPlayer = player ?: return
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = scope.launch {
            while (true) {
                player?.let { p ->
                    val sec = p.currentPosition / 1000.0
                    tick(sec)
                }
                delay(100) // Poll every 100ms
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackerJob?.cancel()
    }

    private fun tick(seconds: Double) {
        currentTime = seconds
        player?.let {
            if (it.duration > 0) duration = it.duration / 1000.0
        }

        stopPlaybackTime?.let { stopTime ->
            if (seconds >= stopTime) {
                player?.pause()
                stopPlaybackTime = null
            }
        }

        playback.lastPosition = seconds
        updateCue(seconds)
    }

    private fun updateCue(time: Double) {
        if (!hasAudio || !hasMatch || chapterTransition) return

        val lookupTime = time - delay
        val cue = timeline.cueAt(lookupTime)

        if (cue == null) {
            clearDisplayedCue()
            return
        }

        if (cue.id == currentCue?.id) return

        val currentIndex = getCurrentIndex()
        if (cue.chapterIndex == currentIndex) {
            displayCue(cue, reveal = autoScroll && hasPlayedOnce)
        } else if (autoScroll && hasPlayedOnce) {
            currentCue = cue
            pendingCue = cue
            loadChapter(cue.chapterIndex)
        } else {
            clearDisplayedCue()
        }
    }

    fun handleRestoreCompleted(currentIndex: Int) {
        if (!hasMatch || !chapterTransition) return

        val cue: SasayakiMatch? = when {
            pendingCue?.chapterIndex == currentIndex -> pendingCue
            timeline.cueAt(currentTime - delay)?.chapterIndex == currentIndex -> timeline.cueAt(currentTime - delay)
            else -> null
        }

        val resume = shouldResume
        chapterTransition = false
        shouldResume = false
        pendingCue = null

        if (cue != null) {
            displayCue(cue, reveal = autoScroll && hasPlayedOnce)
        } else {
            clearDisplayedCue()
        }

        if (resume) {
            player?.play()
        }
    }

    fun prepareTransition() {
        shouldResume = player?.isPlaying == true
        chapterTransition = true
        stopPlaybackTime = null
        clearDisplayedCue()
        player?.pause()
    }

    private fun displayCue(cue: SasayakiMatch, reveal: Boolean) {
        currentCue = cue
        bridge.highlightSasayakiCue(cue.id, reveal)
    }

    private fun clearDisplayedCue() {
        if (currentCue == null) return
        currentCue = null
        bridge.clearSasayakiCue()
    }

    private fun savePlayback() {
        BookStorage.save(playback, rootDir, FileNames.sasayakiPlayback)
    }

    fun teardown() {
        stopProgressTracker()
        player?.release()
        player = null
        isPlaying = false
        stopPlaybackTime = null
    }

    fun nextCue() {
        val next = timeline.nextCue(currentCue?.startTime ?: (currentTime - delay))
        if (next != null) {
            seek(next + delay)
        }
    }

    fun prevCue() {
        val prev = timeline.prevCue(currentCue?.startTime ?: maxOf(0.0, currentTime - delay)) ?: 0.0
        seek(prev + delay)
    }

    private fun seek(seconds: Double) {
        player?.seekTo((seconds * 1000).toLong())
        tick(seconds)
    }
}
