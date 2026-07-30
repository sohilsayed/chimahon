package eu.kanade.tachiyomi.ui.player.scene

import android.graphics.Bitmap
import chimahon.anki.AnkiMediaSource
import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioPreparation
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SceneSentenceAudioServiceTest {
    @TempDir
    lateinit var tempDirectory: File

    @Test
    fun `exports safe audio when the video stream is HDR`() = runBlocking {
        val executor = RecordingExecutor()
        val request = request()

        try {
            val result = service(executor).prepare(request)
            val audio = (result as? AnkiSentenceAudioPreparation.Ready)?.source as? AnkiMediaSource.Bytes

            assertNotNull(audio)
            assertEquals("m4a", audio!!.extension)
            assertEquals(1, executor.ffmpegCalls)
            assertEquals(listOf("a:0"), executor.ffprobeSelectors)
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports unavailable source when no sentence audio input was captured`() = runBlocking {
        val request = request(sentenceAudioInput = null)

        try {
            val result = service(RecordingExecutor()).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports unreadable stream when FFprobe finds no audio`() = runBlocking {
        val request = request()

        try {
            val result = service(
                RecordingExecutor(audioProbeResult = SceneCommandResult.Success("codec_type=video")),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.AUDIO_STREAM_UNREADABLE),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports probe failure when FFprobe fails`() = runBlocking {
        val request = request()

        try {
            val result = service(
                RecordingExecutor(audioProbeResult = SceneCommandResult.Failed),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports extraction failure when FFmpeg fails`() = runBlocking {
        val request = request()

        try {
            val result = service(
                RecordingExecutor(ffmpegResult = SceneCommandResult.Failed),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.EXTRACTION_FAILED),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports extraction timeout when FFmpeg does not finish in time`() = runBlocking {
        val request = request()

        try {
            val result = service(
                RecordingExecutor(ffmpegDelayMillis = 50),
                timeoutMillis = 1,
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT),
                result,
            )
        } finally {
            request.close()
        }
    }

    private fun service(
        executor: RecordingExecutor,
        timeoutMillis: Long = 60_000L,
    ): FrozenSceneSentenceAudioService {
        return FrozenSceneSentenceAudioService(
            cacheDirectory = tempDirectory,
            inputAcquirer = SceneInputAcquirer { input ->
                object : SceneInputLease {
                    override val ffmpegValue = input.value
                    override val tlsCaFile = "/files/cacert.pem"

                    override fun close() = Unit
                }
            },
            commandExecutor = executor,
            timeoutMillis = timeoutMillis,
        )
    }

    private fun request(sentenceAudioInput: SceneVideoInputSpec? = audioInput()): SceneCaptureRequest {
        val video = SceneVideoInputSpec(
            value = "https://media.example/video.mkv",
            kind = SceneVideoInputKind.REMOTE_HTTP,
            headers = emptyList(),
        )
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.isRecycled } returns false
        return SceneCaptureRequest(
            videoInput = video,
            sentenceAudioInput = sentenceAudioInput,
            resolvedTiming = SceneResolvedTiming(
                animationRange = SceneTimeRange(1.25, 4.25),
                audioRange = SceneTimeRange(1.25, 4.25),
            ),
            stillFallback = OwnedBitmap(bitmap),
        )
    }

    private fun audioInput(): SceneVideoInputSpec {
        return SceneVideoInputSpec(
            value = "https://media.example/audio.m4a",
            kind = SceneVideoInputKind.REMOTE_HTTP,
            headers = emptyList(),
        )
    }

    private class RecordingExecutor(
        private val audioProbeResult: SceneCommandResult = SceneCommandResult.Success(
            "codec_type=audio\ncodec_name=aac",
        ),
        private val ffmpegResult: SceneCommandResult = SceneCommandResult.Success(),
        private val ffmpegDelayMillis: Long = 0,
    ) : SceneCommandExecutor {
        var ffmpegCalls = 0
        val ffprobeSelectors = mutableListOf<String>()

        override suspend fun executeFfmpeg(
            arguments: Array<String>,
            onNativeFinished: () -> Unit,
        ): SceneCommandResult {
            return try {
                ffmpegCalls++
                if (ffmpegDelayMillis > 0) delay(ffmpegDelayMillis)
                if (ffmpegResult is SceneCommandResult.Success) {
                    val output = File(arguments.last())
                    output.writeBytes(byteArrayOf(1, 2, 3))
                }
                ffmpegResult
            } finally {
                onNativeFinished()
            }
        }

        override suspend fun executeFfprobe(
            arguments: Array<String>,
            onNativeFinished: () -> Unit,
        ): SceneCommandResult {
            return try {
                val selector = arguments[arguments.indexOf("-select_streams") + 1]
                ffprobeSelectors += selector
                when (selector) {
                    "v:0" -> SceneCommandResult.Success(
                        "pix_fmt=yuv420p10le\ncolor_transfer=smpte2084\nbits_per_raw_sample=10",
                    )
                    "a:0" -> audioProbeResult
                    else -> error("Unexpected stream selector: $selector")
                }
            } finally {
                onNativeFinished()
            }
        }
    }
}
