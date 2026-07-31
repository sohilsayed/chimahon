package eu.kanade.tachiyomi.ui.player.scene

import android.graphics.Bitmap
import chimahon.anki.AnkiMediaSource
import chimahon.anki.AnkiSentenceAudioDiagnostic
import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioInputSource
import chimahon.anki.AnkiSentenceAudioPlayableFallback
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
    fun `records unavailable source when no sentence audio input was captured`() = runBlocking {
        val diagnostics = RecordingDiagnosticLogger()
        val request = request(sentenceAudioInput = null)

        try {
            service(RecordingExecutor(), diagnosticLogger = diagnostics).prepare(request)

            assertEquals(
                listOf(
                    SentenceAudioDiagnosticEvent(
                        stage = SentenceAudioDiagnosticStage.REQUEST_VALIDATION,
                        input = null,
                        fallback = SentenceAudioDiagnosticFallback.NOT_APPLICABLE,
                        failure = AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE,
                    ),
                ),
                diagnostics.events,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports no audio in the original export input after unrestricted discovery`() = runBlocking {
        val request = request()

        try {
            val result = service(
                RecordingExecutor(
                    audioProbeResults = mapOf(
                        "a:0" to SceneCommandResult.Success("codec_type=video"),
                        "a" to SceneCommandResult.Success(),
                    ),
                    unrestrictedAudioProbeResult = SceneCommandResult.Success(),
                ),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    failure = AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND,
                    diagnostic = AnkiSentenceAudioDiagnostic(
                        inputSource = AnkiSentenceAudioInputSource.ORIGINAL_VIDEO,
                    ),
                ),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `exports audio from MPV playable input when original source has no audio`() = runBlocking {
        val original = audioInput(value = "https://media.example/original.m3u8")
        val playable = audioInput(
            value = "https://media.example/playable.m3u8",
            audioStreamIndex = 1,
            origin = SceneVideoInputOrigin.PLAYABLE_VIDEO,
        )
        val request = request(
            sentenceAudioInput = original,
            sentenceAudioFallbackInput = playable,
        )
        val executor = RecordingExecutor(
            audioProbeResults = mapOf(
                "a:0" to SceneCommandResult.Success("codec_type=video"),
                "a" to SceneCommandResult.Success(),
                "1" to SceneCommandResult.Success("codec_type=audio\ncodec_name=aac"),
            ),
            unrestrictedAudioProbeResult = SceneCommandResult.Success(),
        )

        try {
            val result = service(executor).prepare(request)

            assertNotNull((result as? AnkiSentenceAudioPreparation.Ready)?.source)
            assertEquals(listOf("a:0", "a", "a", "1"), executor.ffprobeSelectors)
            assertEquals(listOf(playable.value), executor.ffmpegInputs)
            assertEquals(listOf("0:1"), executor.ffmpegAudioMaps)
        } finally {
            request.close()
        }
    }

    @Test
    fun `exports audio from MPV playable input when original FFprobe fails`() = runBlocking {
        val original = audioInput(value = "https://media.example/original.m3u8")
        val playable = audioInput(
            value = "https://media.example/playable.m3u8",
            audioStreamIndex = 1,
            origin = SceneVideoInputOrigin.PLAYABLE_VIDEO,
        )
        val request = request(
            sentenceAudioInput = original,
            sentenceAudioFallbackInput = playable,
        )
        val executor = RecordingExecutor(
            audioProbeResults = mapOf(
                "a:0" to SceneCommandResult.Failed,
                "1" to SceneCommandResult.Success("codec_type=audio\ncodec_name=aac"),
            ),
        )

        try {
            val result = service(executor).prepare(request)

            assertNotNull((result as? AnkiSentenceAudioPreparation.Ready)?.source)
            assertEquals(listOf("a:0", "1"), executor.ffprobeSelectors)
            assertEquals(listOf(playable.value), executor.ffmpegInputs)
            assertEquals(listOf("0:1"), executor.ffmpegAudioMaps)
        } finally {
            request.close()
        }
    }

    @Test
    fun `exports audio from MPV playable input when original source cannot be acquired`() = runBlocking {
        val original = audioInput(value = "https://media.example/original.m3u8")
        val playable = audioInput(
            value = "https://media.example/playable.m3u8",
            audioStreamIndex = 1,
            origin = SceneVideoInputOrigin.PLAYABLE_VIDEO,
        )
        val request = request(
            sentenceAudioInput = original,
            sentenceAudioFallbackInput = playable,
        )
        val executor = RecordingExecutor(
            audioProbeResults = mapOf(
                "1" to SceneCommandResult.Success("codec_type=audio\ncodec_name=aac"),
            ),
        )

        try {
            val result = service(
                executor = executor,
                inputAcquirer = SceneInputAcquirer { input ->
                    if (input.origin == SceneVideoInputOrigin.ORIGINAL_VIDEO) {
                        null
                    } else {
                        leaseFor(input)
                    }
                },
            ).prepare(request)

            assertNotNull((result as? AnkiSentenceAudioPreparation.Ready)?.source)
            assertEquals(listOf("1"), executor.ffprobeSelectors)
            assertEquals(listOf(playable.value), executor.ffmpegInputs)
            assertEquals(listOf("0:1"), executor.ffmpegAudioMaps)
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports MPV playable input when fallback also has no audio`() = runBlocking {
        val original = audioInput(value = "https://media.example/original.m3u8")
        val playable = audioInput(
            value = "https://media.example/playable.m3u8",
            audioStreamIndex = 1,
            origin = SceneVideoInputOrigin.PLAYABLE_VIDEO,
        )
        val request = request(
            sentenceAudioInput = original,
            sentenceAudioFallbackInput = playable,
        )

        try {
            val result = service(
                RecordingExecutor(
                    audioProbeResults = mapOf(
                        "a:0" to SceneCommandResult.Success("codec_type=video"),
                        "a" to SceneCommandResult.Success(),
                        "1" to SceneCommandResult.Success("codec_type=video"),
                    ),
                    unrestrictedAudioProbeResult = SceneCommandResult.Success(),
                ),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    failure = AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND,
                    diagnostic = AnkiSentenceAudioDiagnostic(
                        inputSource = AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO,
                    ),
                ),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports original probe failure and missing playable fallback`() = runBlocking {
        val request = request(
            sentenceAudioFallbackStatus = AnkiSentenceAudioPlayableFallback.MISSING,
        )

        try {
            val result = service(
                RecordingExecutor(audioProbeResults = mapOf("a:0" to SceneCommandResult.Failed)),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    failure = AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED,
                    diagnostic = AnkiSentenceAudioDiagnostic(
                        inputSource = AnkiSentenceAudioInputSource.ORIGINAL_VIDEO,
                        playableFallback = AnkiSentenceAudioPlayableFallback.MISSING,
                    ),
                ),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `does not retry external audio after its probe fails`() = runBlocking {
        val external = audioInput(
            audioStreamIndex = 0,
            origin = SceneVideoInputOrigin.EXTERNAL_AUDIO,
        )
        val playable = audioInput(
            value = "https://media.example/playable.m3u8",
            audioStreamIndex = 1,
            origin = SceneVideoInputOrigin.PLAYABLE_VIDEO,
        )
        val request = request(
            sentenceAudioInput = external,
            sentenceAudioFallbackInput = playable,
        )

        try {
            val result = service(
                RecordingExecutor(
                    audioProbeResults = mapOf(
                        "0" to SceneCommandResult.Failed,
                        "1" to SceneCommandResult.Success("codec_type=audio\ncodec_name=aac"),
                    ),
                ),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    failure = AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED,
                    diagnostic = AnkiSentenceAudioDiagnostic(
                        inputSource = AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO,
                    ),
                ),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `normalizes a wrong MPV stream index when exactly one audio stream is readable`() = runBlocking {
        val executor = RecordingExecutor(
            audioProbeResults = mapOf(
                "0" to SceneCommandResult.Success("index=0\ncodec_type=video"),
                "a" to SceneCommandResult.Success(
                    """
                    [STREAM]
                    index=1
                    codec_type=audio
                    codec_name=aac
                    [/STREAM]
                    """.trimIndent(),
                ),
            ),
        )
        val request = request(audioInput(audioStreamIndex = 0))

        try {
            val result = service(executor).prepare(request)

            assertNotNull((result as? AnkiSentenceAudioPreparation.Ready)?.source)
            assertEquals(listOf("0", "a"), executor.ffprobeSelectors)
            assertEquals(listOf("0:1"), executor.ffmpegAudioMaps)
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports codec restriction when discovery finds audio in MPV playable input`() = runBlocking {
        val request = request(
            audioInput(
                audioStreamIndex = 4,
                origin = SceneVideoInputOrigin.PLAYABLE_VIDEO,
            ),
        )

        try {
            val result = service(
                RecordingExecutor(
                    audioProbeResults = mapOf(
                        "4" to SceneCommandResult.Success(),
                        "a" to SceneCommandResult.Success(),
                    ),
                    unrestrictedAudioProbeResult = SceneCommandResult.Success(
                        """
                        [STREAM]
                        index=1
                        codec_type=audio
                        codec_name=unsupported_audio
                        [/STREAM]
                        """.trimIndent(),
                    ),
                ),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    failure = AnkiSentenceAudioFailure.AUDIO_CODEC_RESTRICTED,
                    diagnostic = AnkiSentenceAudioDiagnostic(
                        inputSource = AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO,
                    ),
                ),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports no audio in MPV selected external audio input`() = runBlocking {
        val request = request(
            audioInput(
                audioStreamIndex = 0,
                origin = SceneVideoInputOrigin.EXTERNAL_AUDIO,
            ),
        )

        try {
            val result = service(
                RecordingExecutor(
                    audioProbeResults = mapOf(
                        "0" to SceneCommandResult.Success("index=0\ncodec_type=video"),
                        "a" to SceneCommandResult.Success(),
                    ),
                    unrestrictedAudioProbeResult = SceneCommandResult.Success(),
                ),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    failure = AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND,
                    diagnostic = AnkiSentenceAudioDiagnostic(
                        inputSource = AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO,
                    ),
                ),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports protected selected audio stream without remapping it`() = runBlocking {
        val executor = RecordingExecutor(
            audioProbeResults = mapOf(
                "0" to SceneCommandResult.Success(
                    "index=0\ncodec_type=audio\nside_data_type=Encryption info",
                ),
            ),
        )
        val request = request(audioInput(audioStreamIndex = 0))

        try {
            val result = service(executor).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED,
                ),
                result,
            )
            assertEquals(listOf("0"), executor.ffprobeSelectors)
            assertEquals(0, executor.ffmpegCalls)
        } finally {
            request.close()
        }
    }

    @Test
    fun `refuses to replace a wrong MPV stream index when multiple audio streams exist`() = runBlocking {
        val request = request(audioInput(audioStreamIndex = 0))

        try {
            val result = service(
                RecordingExecutor(
                    audioProbeResults = mapOf(
                        "0" to SceneCommandResult.Success("index=0\ncodec_type=video"),
                        "a" to SceneCommandResult.Success(
                            """
                            [STREAM]
                            index=1
                            codec_type=audio
                            [/STREAM]
                            [STREAM]
                            index=2
                            codec_type=audio
                            [/STREAM]
                            """.trimIndent(),
                        ),
                    ),
                ),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE,
                ),
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
                RecordingExecutor(audioProbeResults = mapOf("a:0" to SceneCommandResult.Failed)),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    failure = AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED,
                    diagnostic = AnkiSentenceAudioDiagnostic(
                        inputSource = AnkiSentenceAudioInputSource.ORIGINAL_VIDEO,
                    ),
                ),
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
    fun `reports missing output when FFmpeg succeeds without creating sentence audio`() = runBlocking {
        val request = request()

        try {
            val result = service(
                RecordingExecutor(writeAudioOutput = false),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    failure = AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_MISSING,
                    diagnostic = AnkiSentenceAudioDiagnostic(
                        inputSource = AnkiSentenceAudioInputSource.ORIGINAL_VIDEO,
                    ),
                ),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports source read failure when FFmpeg cannot read MPV playable source`() = runBlocking {
        val request = request(
            audioInput(origin = SceneVideoInputOrigin.PLAYABLE_VIDEO),
        )

        try {
            val result = service(
                RecordingExecutor(
                    ffmpegResult = SceneCommandResult.FfmpegFailed(SceneFfmpegFailure.SOURCE_READ),
                ),
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(
                    failure = AnkiSentenceAudioFailure.EXTRACTION_SOURCE_READ_FAILED,
                    diagnostic = AnkiSentenceAudioDiagnostic(
                        inputSource = AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO,
                    ),
                ),
                result,
            )
        } finally {
            request.close()
        }
    }

    @Test
    fun `reports extraction timeout when FFmpeg does not finish in time`() = runBlocking {
        val diagnostics = RecordingDiagnosticLogger()
        val request = request()

        try {
            val result = service(
                RecordingExecutor(ffmpegDelayMillis = 50),
                timeoutMillis = 1,
                diagnosticLogger = diagnostics,
            ).prepare(request)

            assertEquals(
                AnkiSentenceAudioPreparation.Unavailable(AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT),
                result,
            )
            assertEquals(
                SentenceAudioDiagnosticEvent(
                    stage = SentenceAudioDiagnosticStage.AUDIO_EXTRACTION,
                    input = request.sentenceAudioInput,
                    fallback = SentenceAudioDiagnosticFallback.NOT_APPLICABLE,
                    failure = AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT,
                ),
                diagnostics.events.last(),
            )
        } finally {
            request.close()
        }
    }

    private fun service(
        executor: RecordingExecutor,
        timeoutMillis: Long = 60_000L,
        inputAcquirer: SceneInputAcquirer = SceneInputAcquirer { input -> leaseFor(input) },
        diagnosticLogger: SentenceAudioDiagnosticLogger = NoOpSentenceAudioDiagnosticLogger,
    ): FrozenSceneSentenceAudioService {
        return FrozenSceneSentenceAudioService(
            cacheDirectory = tempDirectory,
            inputAcquirer = inputAcquirer,
            commandExecutor = executor,
            timeoutMillis = timeoutMillis,
            diagnosticLogger = diagnosticLogger,
        )
    }

    private fun leaseFor(input: SceneVideoInputSpec): SceneInputLease {
        return object : SceneInputLease {
            override val ffmpegValue = input.value
            override val tlsCaFile = "/files/cacert.pem"

            override fun close() = Unit
        }
    }

    private fun request(
        sentenceAudioInput: SceneVideoInputSpec? = audioInput(),
        sentenceAudioFallbackInput: SceneVideoInputSpec? = null,
        sentenceAudioFallbackStatus: AnkiSentenceAudioPlayableFallback? = null,
    ): SceneCaptureRequest {
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
            sentenceAudioFallbackInput = sentenceAudioFallbackInput,
            sentenceAudioFallbackStatus = sentenceAudioFallbackStatus,
            resolvedTiming = SceneResolvedTiming(
                animationRange = SceneTimeRange(1.25, 4.25),
                audioRange = SceneTimeRange(1.25, 4.25),
            ),
            stillFallback = OwnedBitmap(bitmap),
        )
    }

    private fun audioInput(
        value: String = "https://media.example/audio.m4a",
        audioStreamIndex: Int? = null,
        origin: SceneVideoInputOrigin = SceneVideoInputOrigin.ORIGINAL_VIDEO,
    ): SceneVideoInputSpec {
        return SceneVideoInputSpec(
            value = value,
            kind = SceneVideoInputKind.REMOTE_HTTP,
            headers = emptyList(),
            audioStreamIndex = audioStreamIndex,
            origin = origin,
        )
    }

    private class RecordingExecutor(
        private val audioProbeResults: Map<String, SceneCommandResult> = mapOf(
            "a:0" to SceneCommandResult.Success("codec_type=audio\ncodec_name=aac"),
        ),
        private val unrestrictedAudioProbeResult: SceneCommandResult? = null,
        private val ffmpegResult: SceneCommandResult = SceneCommandResult.Success(),
        private val ffmpegDelayMillis: Long = 0,
        private val writeAudioOutput: Boolean = true,
    ) : SceneCommandExecutor {
        var ffmpegCalls = 0
        val ffprobeSelectors = mutableListOf<String>()
        val unrestrictedFfprobeSelectors = mutableListOf<String>()
        val ffmpegAudioMaps = mutableListOf<String>()
        val ffmpegInputs = mutableListOf<String>()

        override suspend fun executeFfmpeg(
            arguments: Array<String>,
            onNativeFinished: () -> Unit,
        ): SceneCommandResult {
            return try {
                ffmpegCalls++
                ffmpegInputs += arguments[arguments.indexOf("-i") + 1]
                ffmpegAudioMaps += arguments[arguments.indexOf("-map") + 1]
                if (ffmpegDelayMillis > 0) delay(ffmpegDelayMillis)
                if (ffmpegResult is SceneCommandResult.Success && writeAudioOutput) {
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
                val restrictDecoders = "-codec_whitelist" in arguments
                if (!restrictDecoders) {
                    unrestrictedFfprobeSelectors += selector
                    return unrestrictedAudioProbeResult
                        ?: error("Unexpected unrestricted stream selector: $selector")
                }
                when (selector) {
                    "v:0" -> SceneCommandResult.Success(
                        "pix_fmt=yuv420p10le\ncolor_transfer=smpte2084\nbits_per_raw_sample=10",
                    )
                    else -> audioProbeResults[selector]
                        ?: error("Unexpected stream selector: $selector")
                }
            } finally {
                onNativeFinished()
            }
        }
    }

    private class RecordingDiagnosticLogger : SentenceAudioDiagnosticLogger {
        val events = mutableListOf<SentenceAudioDiagnosticEvent>()

        override fun record(event: SentenceAudioDiagnosticEvent) {
            events += event
        }
    }
}
