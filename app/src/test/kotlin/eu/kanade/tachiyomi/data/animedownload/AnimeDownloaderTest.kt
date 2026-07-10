package eu.kanade.tachiyomi.data.animedownload

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnimeDownloaderTest {
    @Test
    fun `ffmpeg failure shows final error instead of build banner`() {
        val message = buildFFmpegFailureMessage(
            exitCode = "1",
            failStackTrace = null,
            logs = """
                ffmpeg version n7.1 Copyright (c) 2000-2024 the FFmpeg developers
                built with Android clang version 18.0.3
                configuration: --target-os=android
                libavutil      59. 39.100 / 59. 39.100
                [https @ 0x123] HTTP error 403 Forbidden
                video.mp4: Server returned 403 Forbidden
            """.trimIndent(),
        )

        assertTrue(message.contains("HTTP error 403 Forbidden"))
        assertTrue(message.contains("Server returned 403 Forbidden"))
        assertFalse(message.contains("ffmpeg version"))
        assertFalse(message.contains("configuration:"))
    }

    @Test
    fun `ffmpeg failure falls back to exit code without logs`() {
        assertEquals(
            "FFmpeg exit code: 1",
            buildFFmpegFailureMessage(exitCode = "1", failStackTrace = null, logs = null),
        )
    }
}
