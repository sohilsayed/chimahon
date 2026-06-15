package eu.kanade.tachiyomi.ui.player.controls.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay

@Composable
fun StatsOverlay(
    modifier: Modifier = Modifier,
) {
    var stats by remember { mutableStateOf(StatsData()) }

    LaunchedEffect(Unit) {
        while (true) {
            val newStats = StatsData(
                videoCodec = MPVLib.getPropertyString("video-codec") ?: "N/A",
                audioCodec = MPVLib.getPropertyString("audio-codec") ?: "N/A",
                hwdec = MPVLib.getPropertyString("hwdec-current") ?: "none",
                videoWidth = MPVLib.getPropertyInt("video-params/w") ?: 0,
                videoHeight = MPVLib.getPropertyInt("video-params/h") ?: 0,
                fps = MPVLib.getPropertyString("estimated-vf-fps") ?: "?",
                droppedFrames = MPVLib.getPropertyInt("frame-drop-count") ?: 0,
                avsync = MPVLib.getPropertyString("avsync") ?: "?",
                cacheDuration = MPVLib.getPropertyString("demuxer-cache-duration") ?: "?",
            )
            if (newStats != stats) stats = newStats
            delay(1000)
        }
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        StatLine("Video", "${stats.videoWidth}x${stats.videoHeight} ${stats.videoCodec}")
        StatLine("HW Decode", stats.hwdec)
        StatLine("FPS", stats.fps)
        StatLine("Audio", stats.audioCodec)
        StatLine("A/V Sync", "${stats.avsync}s")
        StatLine("Dropped", "${stats.droppedFrames}")
        StatLine("Cache", "${stats.cacheDuration}s")
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = Color.White,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
}

private data class StatsData(
    val videoCodec: String = "",
    val audioCodec: String = "",
    val hwdec: String = "",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val fps: String = "",
    val droppedFrames: Int = 0,
    val avsync: String = "",
    val cacheDuration: String = "",
)
