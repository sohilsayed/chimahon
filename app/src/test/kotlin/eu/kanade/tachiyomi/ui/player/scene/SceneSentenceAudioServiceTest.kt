package eu.kanade.tachiyomi.ui.player.scene

import android.graphics.Bitmap
import chimahon.anki.AnkiMediaSource
import io.mockk.every
import io.mockk.mockk
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
            val audio = result as? AnkiMediaSource.Bytes

            assertNotNull(audio)
            assertEquals("m4a", audio!!.extension)
            assertEquals(1, executor.ffmpegCalls)
            assertEquals(listOf("a:0"), executor.ffprobeSelectors)
        } finally {
            request.close()
        }
    }

    private fun service(executor: RecordingExecutor): FrozenSceneSentenceAudioService {
        val constructor = FrozenSceneSentenceAudioService::class.java.getDeclaredConstructor(
            File::class.java,
            SceneInputAcquirer::class.java,
            SceneCommandExecutor::class.java,
        ).apply {
            isAccessible = true
        }
        return constructor.newInstance(
            tempDirectory,
            SceneInputAcquirer { input ->
                object : SceneInputLease {
                    override val ffmpegValue = input.value
                    override val tlsCaFile = "/files/cacert.pem"

                    override fun close() = Unit
                }
            },
            executor,
        )
    }

    private fun request(): SceneCaptureRequest {
        val video = SceneVideoInputSpec(
            value = "https://media.example/video.mkv",
            kind = SceneVideoInputKind.REMOTE_HTTP,
            headers = emptyList(),
        )
        val audio = SceneVideoInputSpec(
            value = "https://media.example/audio.m4a",
            kind = SceneVideoInputKind.REMOTE_HTTP,
            headers = emptyList(),
        )
        val bitmap = mockk<Bitmap>(relaxed = true)
        every { bitmap.isRecycled } returns false
        return SceneCaptureRequest(
            videoInput = video,
            sentenceAudioInput = audio,
            resolvedTiming = SceneResolvedTiming(
                animationRange = SceneTimeRange(1.25, 4.25),
                audioRange = SceneTimeRange(1.25, 4.25),
            ),
            stillFallback = OwnedBitmap(bitmap),
        )
    }

    private class RecordingExecutor : SceneCommandExecutor {
        var ffmpegCalls = 0
        val ffprobeSelectors = mutableListOf<String>()

        override suspend fun executeFfmpeg(
            arguments: Array<String>,
            onNativeFinished: () -> Unit,
        ): SceneCommandResult {
            return try {
                ffmpegCalls++
                val output = File(arguments.last())
                output.writeBytes(byteArrayOf(1, 2, 3))
                SceneCommandResult.Success()
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
                    "a:0" -> SceneCommandResult.Success("codec_type=audio\ncodec_name=aac")
                    else -> error("Unexpected stream selector: $selector")
                }
            } finally {
                onNativeFinished()
            }
        }
    }
}
