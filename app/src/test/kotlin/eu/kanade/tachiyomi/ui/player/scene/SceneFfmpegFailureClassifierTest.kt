package eu.kanade.tachiyomi.ui.player.scene

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SceneFfmpegFailureClassifierTest {
    @Test
    fun `classifies an HTTP failure while reading the video source`() {
        assertEquals(
            SceneFfmpegFailure.SOURCE_READ,
            classifySceneFfmpegFailure(
                failStackTrace = null,
                logs = "[https @ 0x1] HTTP error 403 Forbidden",
            ),
        )
    }

    @Test
    fun `classifies a missing mapped stream`() {
        assertEquals(
            SceneFfmpegFailure.STREAM_MAPPING,
            classifySceneFfmpegFailure(
                failStackTrace = null,
                logs = "Stream map '0:4' matches no streams.",
            ),
        )
    }

    @Test
    fun `classifies an input seek failure`() {
        assertEquals(
            SceneFfmpegFailure.SEEK,
            classifySceneFfmpegFailure(
                failStackTrace = "Could not seek to position 12.500",
                logs = null,
            ),
        )
    }

    @Test
    fun `classifies a temporary output write failure`() {
        assertEquals(
            SceneFfmpegFailure.OUTPUT_WRITE,
            classifySceneFfmpegFailure(
                failStackTrace = null,
                logs = "Could not write header for output file #0: Invalid argument",
            ),
        )
    }

    @Test
    fun `keeps unrecognised FFmpeg output generic`() {
        assertEquals(
            SceneFfmpegFailure.UNKNOWN,
            classifySceneFfmpegFailure(
                failStackTrace = null,
                logs = "Conversion failed.",
            ),
        )
    }
}
