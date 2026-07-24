package eu.kanade.tachiyomi.ui.player.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerResumeTest {
    @Test
    fun `keeps incomplete episode position`() {
        assertEquals(120_000L, 120_000L.safeResumePositionMillis(totalMillis = 600_000L))
    }

    @Test
    fun `resets positions at the end of an episode`() {
        assertEquals(0L, 599_000L.safeResumePositionMillis(totalMillis = 600_000L))
    }

    @Test
    fun `resets stored episode progress when duration is unknown`() {
        assertEquals(
            0L,
            120_000L.safeResumePositionMillis(
                totalMillis = 0L,
                resetWhenDurationUnknown = true,
            ),
        )
    }

    @Test
    fun `keeps active playback position when duration is not ready`() {
        assertEquals(120_000L, 120_000L.safeResumePositionMillis(totalMillis = 0L))
    }
}
