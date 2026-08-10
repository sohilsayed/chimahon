package eu.kanade.tachiyomi.ui.player.controls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubtitleSwipeActionTest {

    @Test
    fun `swipe up replays the current subtitle`() {
        assertEquals(SubtitleSwipeAction.ReplayCurrent, resolveSubtitleSwipeAction(0f, -31f, 30f))
    }

    @Test
    fun `swipe down toggles subtitle visibility`() {
        assertEquals(SubtitleSwipeAction.ToggleVisibility, resolveSubtitleSwipeAction(0f, 31f, 30f))
    }

    @Test
    fun `horizontal swipes move between subtitle cues`() {
        assertEquals(SubtitleSwipeAction.Previous, resolveSubtitleSwipeAction(-31f, 0f, 30f))
        assertEquals(SubtitleSwipeAction.Next, resolveSubtitleSwipeAction(31f, 0f, 30f))
    }

    @Test
    fun `diagonal and short swipes have no subtitle action`() {
        assertNull(resolveSubtitleSwipeAction(30f, 30f, 30f))
        assertNull(resolveSubtitleSwipeAction(0f, 29f, 30f))
    }
}
