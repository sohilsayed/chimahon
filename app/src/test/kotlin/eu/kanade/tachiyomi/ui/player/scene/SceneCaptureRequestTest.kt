package eu.kanade.tachiyomi.ui.player.scene

import android.graphics.Bitmap
import chimahon.anki.AnkiSentenceAudioFailure
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SceneCaptureRequestTest {
    @Test
    fun `unchanged double snapshot creates request`() = runTest {
        val bitmap = mockBitmap()
        val factory = SceneCaptureRequestFactory(
            SceneMpvSnapshotReader(SequencePropertyReader(listOf(snapshot(), snapshot()))),
        )

        val request = factory.captureSubtitle(
            videoSnapshot = { inputSnapshot() },
            parsedSubtitleCandidates = emptyList(),
            captureFallback = { bitmap },
        )

        assertNotNull(request)
        request!!.close()
        verify(exactly = 1) { bitmap.recycle() }
    }

    @Test
    fun `changed player path rejects request and recycles still`() = runTest {
        val bitmap = mockBitmap()
        val factory = SceneCaptureRequestFactory(
            SceneMpvSnapshotReader(
                SequencePropertyReader(
                    listOf(
                        snapshot(path = "https://media.example/one.mp4"),
                        snapshot(path = "https://media.example/two.mp4"),
                    ),
                ),
            ),
        )

        val request = factory.captureSubtitle(
            videoSnapshot = { inputSnapshot() },
            parsedSubtitleCandidates = emptyList(),
            captureFallback = { bitmap },
        )

        assertNull(request)
        verify(exactly = 1) { bitmap.recycle() }
    }

    @Test
    fun `changed frozen video state rejects request`() = runTest {
        val bitmap = mockBitmap()
        val factory = SceneCaptureRequestFactory(
            SceneMpvSnapshotReader(SequencePropertyReader(listOf(snapshot(), snapshot()))),
        )
        var reads = 0

        val request = factory.captureSubtitle(
            videoSnapshot = {
                inputSnapshot(
                    headers = listOf("User-Agent" to if (reads++ == 0) "before" else "after"),
                )
            },
            parsedSubtitleCandidates = emptyList(),
            captureFallback = { bitmap },
        )

        assertNull(request)
        verify(exactly = 1) { bitmap.recycle() }
    }

    @Test
    fun `selected audio without a frozen ffmpeg index is omitted`() = runTest {
        val bitmap = mockBitmap()
        val factory = SceneCaptureRequestFactory(
            SceneMpvSnapshotReader(
                SequencePropertyReader(
                    listOf(
                        snapshot(selectedAudioId = 2),
                        snapshot(selectedAudioId = 2),
                    ),
                ),
            ),
        )

        val request = factory.captureSubtitle(
            videoSnapshot = { inputSnapshot() },
            parsedSubtitleCandidates = emptyList(),
            captureFallback = { bitmap },
        )

        assertNotNull(request)
        assertNull(request!!.sentenceAudioInput)
        assertEquals(
            AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE,
            request.sentenceAudioFailure,
        )
        request.close()
    }

    @Test
    fun `single selected audio without a frozen ffmpeg index uses the only audio stream`() = runTest {
        val bitmap = mockBitmap()
        val factory = SceneCaptureRequestFactory(
            SceneMpvSnapshotReader(
                SequencePropertyReader(
                    listOf(
                        snapshotWithSingleAudioWithoutFfmpegIndex(),
                        snapshotWithSingleAudioWithoutFfmpegIndex(),
                    ),
                ),
            ),
        )

        val request = factory.captureSubtitle(
            videoSnapshot = { inputSnapshot() },
            parsedSubtitleCandidates = emptyList(),
            captureFallback = { bitmap },
        )

        assertNotNull(request)
        assertNotNull(request!!.sentenceAudioInput)
        request.close()
    }

    @Test
    fun `original sentence audio input keeps a different MPV playable source as fallback`() = runTest {
        val bitmap = mockBitmap()
        val factory = SceneCaptureRequestFactory(
            SceneMpvSnapshotReader(SequencePropertyReader(listOf(snapshot(), snapshot()))),
        )

        val request = factory.captureSubtitle(
            videoSnapshot = {
                inputSnapshot(
                    originalVideoValue = "https://media.example/original.m3u8",
                    playableValue = "https://media.example/playable.m3u8",
                )
            },
            parsedSubtitleCandidates = emptyList(),
            captureFallback = { bitmap },
        )

        assertNotNull(request)
        val capturedRequest = requireNotNull(request)
        val originalInput = requireNotNull(capturedRequest.sentenceAudioInput)
        val playableInput = requireNotNull(capturedRequest.sentenceAudioFallbackInput)
        assertEquals("https://media.example/original.m3u8", originalInput.value)
        assertEquals(SceneVideoInputOrigin.ORIGINAL_VIDEO, originalInput.origin)
        assertEquals("https://media.example/playable.m3u8", playableInput.value)
        assertEquals(SceneVideoInputOrigin.PLAYABLE_VIDEO, playableInput.origin)
        capturedRequest.close()
    }

    private fun mockBitmap(): Bitmap {
        return mockk<Bitmap>(relaxed = true).also {
            every { it.isRecycled } returns false
        }
    }

    private fun inputSnapshot(
        originalVideoValue: String = "https://media.example/video.mp4",
        playableValue: String = originalVideoValue,
        headers: List<Pair<String, String>> = emptyList(),
    ): SceneCaptureInputSnapshot {
        val video = SceneVideoInputSnapshot(
            originalVideoValue = originalVideoValue,
            playableValue = playableValue,
            headers = headers,
            ffmpegStreamArgs = emptyList(),
            ffmpegVideoArgs = emptyList(),
            seekable = true,
        )
        return SceneCaptureInputSnapshot(video = video, sentenceAudio = null)
    }

    private fun snapshot(
        path: String = "https://media.example/video.mp4",
        selectedAudioId: Int? = null,
    ): SnapshotValues {
        return SnapshotValues(
            doubles = mapOf(
                "time-pos" to 5.0,
                "duration" to 60.0,
                "sub-start/full" to 4.0,
                "sub-end/full" to 6.0,
                "sub-speed" to 1.0,
                "sub-delay" to 0.0,
            ),
            strings = buildMap {
                put("path", path)
                put("track-list/0/type", "video")
                selectedAudioId?.let { put("aid", it.toString()) }
            },
            booleans = mapOf(
                "track-list/0/selected" to true,
                "track-list/0/external" to false,
                "seekable" to true,
            ),
            ints = mapOf(
                "track-list/count" to 1,
                "track-list/0/id" to 1,
                "track-list/0/ff-index" to 0,
            ),
        )
    }

    private fun snapshotWithSingleAudioWithoutFfmpegIndex(): SnapshotValues {
        return SnapshotValues(
            doubles = mapOf(
                "time-pos" to 5.0,
                "duration" to 60.0,
                "sub-start/full" to 4.0,
                "sub-end/full" to 6.0,
                "sub-speed" to 1.0,
                "sub-delay" to 0.0,
            ),
            strings = mapOf(
                "path" to "https://media.example/video.mp4",
                "aid" to "2",
                "track-list/0/type" to "video",
                "track-list/1/type" to "audio",
            ),
            booleans = mapOf(
                "track-list/0/selected" to true,
                "track-list/0/external" to false,
                "track-list/1/external" to false,
                "seekable" to true,
            ),
            ints = mapOf(
                "track-list/count" to 2,
                "track-list/0/id" to 1,
                "track-list/0/ff-index" to 0,
                "track-list/1/id" to 2,
            ),
        )
    }

    private data class SnapshotValues(
        val doubles: Map<String, Double>,
        val strings: Map<String, String>,
        val booleans: Map<String, Boolean>,
        val ints: Map<String, Int>,
    )

    private class SequencePropertyReader(
        private val snapshots: List<SnapshotValues>,
    ) : SceneMpvPropertyReader {
        private var index = 0
        private val current: SnapshotValues
            get() = snapshots[index.coerceAtMost(snapshots.lastIndex)]

        override fun double(name: String): Double? = current.doubles[name]

        override fun string(name: String): String? = current.strings[name]

        override fun boolean(name: String): Boolean? {
            return current.booleans[name].also {
                if (name == "seekable") index++
            }
        }

        override fun int(name: String): Int? = current.ints[name]
    }
}
