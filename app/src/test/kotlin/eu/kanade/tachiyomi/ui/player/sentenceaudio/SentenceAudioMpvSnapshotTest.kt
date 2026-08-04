package eu.kanade.tachiyomi.ui.player.sentenceaudio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SentenceAudioMpvSnapshotTest {
    @Test
    fun `reads aid when MPV exposes it as a string`() {
        val snapshot = reader(audioTrack(id = 7, ffIndex = 3), strings = mapOf("aid" to "7")).read()
        assertEquals(7, snapshot.selectedAudioId)
        assertEquals(3, snapshot.selectedAudioFfmpegIndex)
    }

    @Test
    fun `reads aid when MPV exposes it as an integer`() {
        val snapshot = reader(audioTrack(id = 8, ffIndex = 4), ints = mapOf("aid" to 8)).read()
        assertEquals(8, snapshot.selectedAudioId)
        assertEquals(4, snapshot.selectedAudioFfmpegIndex)
    }

    @Test
    fun `counts all audio tracks even when aid is missing`() {
        val snapshot = reader(audioTrack(id = 1), audioTrack(id = 2), strings = emptyMap()).read()
        assertNull(snapshot.selectedAudioId)
        assertEquals(2, snapshot.audioTrackCount)
    }

    @Test
    fun `external filename marks selected audio external even without external flag`() {
        val snapshot = reader(audioTrack(id = 3, externalFile = "file:///audio.m4a"), strings = mapOf("aid" to "3")).read()
        assertTrue(snapshot.selectedAudioIsExternal)
        assertEquals("file:///audio.m4a", snapshot.selectedExternalAudioValue)
    }

    @Test
    fun `missing or negative selected FFmpeg index is null`() {
        val missing = reader(audioTrack(id = 4), strings = mapOf("aid" to "4")).read()
        val negative = reader(audioTrack(id = 5, ffIndex = -1), strings = mapOf("aid" to "5")).read()
        assertNull(missing.selectedAudioFfmpegIndex)
        assertNull(negative.selectedAudioFfmpegIndex)
        assertFalse(negative.selectedAudioIsExternal)
    }

    private fun reader(vararg tracks: Track, strings: Map<String, String> = emptyMap(), ints: Map<String, Int> = emptyMap()): SentenceAudioMpvSnapshotReader {
        val values = FakeSentenceAudioMpvPropertyReader(strings = strings.toMutableMap(), ints = ints.toMutableMap())
        values.strings["path"] = "/video.mkv"
        values.ints["track-list/count"] = tracks.size
        tracks.forEachIndexed { index, track ->
            values.strings["track-list/$index/type"] = "audio"
            values.ints["track-list/$index/id"] = track.id
            track.ffIndex?.let { values.ints["track-list/$index/ff-index"] = it }
            track.externalFile?.let { values.strings["track-list/$index/external-filename"] = it }
            if (track.external) values.booleans["track-list/$index/external"] = true
        }
        return SentenceAudioMpvSnapshotReader(values)
    }

    private fun audioTrack(id: Int, ffIndex: Int? = null, external: Boolean = false, externalFile: String? = null) = Track(id, ffIndex, external, externalFile)
    private data class Track(val id: Int, val ffIndex: Int?, val external: Boolean, val externalFile: String?)
}

private class FakeSentenceAudioMpvPropertyReader(
    val strings: MutableMap<String, String> = mutableMapOf(),
    val ints: MutableMap<String, Int> = mutableMapOf(),
    val booleans: MutableMap<String, Boolean> = mutableMapOf(),
) : SentenceAudioMpvPropertyReader {
    override fun string(name: String): String? = strings[name]
    override fun int(name: String): Int? = ints[name]
    override fun boolean(name: String): Boolean? = booleans[name]
}
