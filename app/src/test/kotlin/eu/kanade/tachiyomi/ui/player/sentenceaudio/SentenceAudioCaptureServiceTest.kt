package eu.kanade.tachiyomi.ui.player.sentenceaudio

import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioPreparation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
class SentenceAudioCaptureServiceTest {
    @TempDir lateinit var cacheDirectory: File

    @Test
    fun `exports safe audio when the video stream is HDR`() = runTest {
        val executor = FakeExecutor(listOf(SentenceAudioCommandResult.Success(selectedAudio)))
        val result = service(executor).prepare(request())
        assertType<AnkiSentenceAudioPreparation.Ready>(result)
        assertEquals(1, executor.ffmpegCalls)
    }

    @Test
    fun `reports unavailable source when no sentence audio input was captured`() = runTest {
        val result = service(FakeExecutor()).prepare(SentenceAudioCaptureRequest(null, 1.0, 2.0))
        assertFailure(AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE, result)
    }

    @Test
    fun `uses playable input only for closed resolution failures`() = runTest {
        val executor = FakeExecutor(
            selected = listOf(SentenceAudioCommandResult.Failed, SentenceAudioCommandResult.Success(selectedAudio)),
        )
        val result = service(executor).prepare(request(playable = "https://media.example/playable.mp4"))
        assertType<AnkiSentenceAudioPreparation.Ready>(result)
        assertEquals(listOf("https://media.example/original.mp4", "https://media.example/playable.mp4"), executor.probeInputs.take(2))
    }

    @Test
    fun `falls back to original video when external audio probe fails`() = runTest {
        val executor = FakeExecutor(
            selected = listOf(SentenceAudioCommandResult.Failed, SentenceAudioCommandResult.Failed, SentenceAudioCommandResult.Success(selectedAudio)),
        )
        val result = service(executor).prepare(request(index = 8, external = true, externalValue = "https://media.example/audio.m4a", playable = "https://media.example/playable.mp4"))
        assertType<AnkiSentenceAudioPreparation.Ready>(result)
        assertEquals(listOf("https://media.example/audio.m4a", "https://media.example/audio.m4a", "https://media.example/original.mp4"), executor.probeInputs)
        assertEquals(listOf("8", "a:0", "a:0"), executor.probeSelectors)
    }

    @Test
    fun `does not retry playable input after extraction failure`() = runTest {
        val executor = FakeExecutor(
            selected = listOf(SentenceAudioCommandResult.Success(selectedAudio)),
            ffmpeg = listOf(SentenceAudioCommandResult.FfmpegFailed(SentenceAudioFfmpegFailure.SOURCE_READ)),
        )
        val result = service(executor).prepare(request(playable = "https://media.example/playable.mp4"))
        assertFailure(AnkiSentenceAudioFailure.EXTRACTION_SOURCE_READ_FAILED, result)
        assertEquals(1, executor.probeInputs.size)
        assertEquals(1, executor.ffmpegCalls)
    }

    @Test
    fun `retries unindexed extraction after stream mapping failure`() = runTest {
        val executor = FakeExecutor(
            selected = listOf(SentenceAudioCommandResult.Success(selectedAudio)),
            ffmpeg = listOf(
                SentenceAudioCommandResult.FfmpegFailed(SentenceAudioFfmpegFailure.STREAM_MAPPING),
                SentenceAudioCommandResult.Success(),
            ),
        )
        val result = service(executor).prepare(request(index = 2))
        assertType<AnkiSentenceAudioPreparation.Ready>(result)
        assertEquals(2, executor.ffmpegCalls)
        assertTrue(executor.ffmpegArguments[0].contains("0:2"))
        assertTrue(executor.ffmpegArguments[1].contains("0:a:0"))
    }

    @Test
    fun `normalizes a wrong stream index only when exactly one audio stream is readable`() = runTest {
        val executor = FakeExecutor(
            selected = listOf(SentenceAudioCommandResult.Success("codec_type=video")),
            all = listOf(SentenceAudioCommandResult.Success("[STREAM]\nindex=6\ncodec_type=audio\n[/STREAM]")),
        )
        val result = service(executor).prepare(request(index = 2))
        assertType<AnkiSentenceAudioPreparation.Ready>(result)
        assertTrue(executor.ffmpegArguments.single().contains("0:6"))
    }

    @Test
    fun `refuses multiple audio streams rather than guessing`() = runTest {
        val executor = FakeExecutor(
            selected = listOf(SentenceAudioCommandResult.Success("codec_type=video")),
            all = listOf(SentenceAudioCommandResult.Success("[STREAM]\nindex=2\ncodec_type=audio\n[/STREAM]\n[STREAM]\nindex=3\ncodec_type=audio\n[/STREAM]")),
        )
        assertFailure(AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE, service(executor).prepare(request()))
        assertEquals(0, executor.ffmpegCalls)
    }

    @Test
    fun `reports protected selected audio stream without remapping`() = runTest {
        val executor = FakeExecutor(selected = listOf(SentenceAudioCommandResult.Success("codec_type=audio\nside_data_type=drm")))
        assertFailure(AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED, service(executor).prepare(request()))
        assertEquals(0, executor.allProbeCalls)
    }

    @Test
    fun `reports codec restriction and attempts frozen remote index after empty discovery`() = runTest {
        val restricted = "[STREAM]\nindex=7\ncodec_type=audio\n[/STREAM]"
        assertFailure(
            AnkiSentenceAudioFailure.AUDIO_CODEC_RESTRICTED,
            service(FakeExecutor(selected = listOf(SentenceAudioCommandResult.Success("codec_type=video")), all = listOf(SentenceAudioCommandResult.Success("")), discovery = listOf(SentenceAudioCommandResult.Success(restricted)))).prepare(request()),
        )
        val executor = FakeExecutor(selected = listOf(SentenceAudioCommandResult.Success("codec_type=video")), all = listOf(SentenceAudioCommandResult.Success("")), discovery = listOf(SentenceAudioCommandResult.Success("")))
        assertType<AnkiSentenceAudioPreparation.Ready>(service(executor).prepare(request(index = 7)))
        assertTrue(executor.ffmpegArguments.single().contains("0:7"))
    }

    @Test
    fun `attempts frozen external audio index after empty probes`() = runTest {
        val executor = FakeExecutor(
            selected = listOf(SentenceAudioCommandResult.Success("")),
            all = listOf(SentenceAudioCommandResult.Success("")),
            discovery = listOf(SentenceAudioCommandResult.Success("")),
        )

        val result = service(executor).prepare(
            request(
                index = 0,
                external = true,
                externalValue = "https://media.example/external-audio.webm",
            ),
        )

        assertType<AnkiSentenceAudioPreparation.Ready>(result)
        assertEquals(listOf("https://media.example/external-audio.webm"), executor.probeInputs.distinct())
        assertTrue(executor.ffmpegArguments.single().contains("0:0"))
    }

    @Test
    fun `reports missing output and timeout`() = runTest {
        val noOutput = FakeExecutor(selected = listOf(SentenceAudioCommandResult.Success(selectedAudio)), writeOutput = false)
        assertFailure(AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_MISSING, service(noOutput).prepare(request()))
        val timeout = FakeExecutor(selected = listOf(SentenceAudioCommandResult.Success(selectedAudio)), neverFinish = true)
        assertFailure(AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT, service(timeout, timeoutMillis = 1).prepare(request()))
    }

    @Test
    fun `cancellation defers lease close and output deletion until native work ends`() = runTest {
        val executor = BlockingExecutor()
        val acquirer = CountingAcquirer()
        val job = async { SentenceAudioCaptureService(cacheDirectory, acquirer, executor, ioDispatcher = Dispatchers.Unconfined).prepare(request()) }
        executor.started.await()
        job.cancelAndJoin()
        // The selected-track probe lease has finished; the extraction lease remains open.
        assertEquals(1, acquirer.closed)
        assertTrue(cacheDirectory.listFiles().orEmpty().any { it.name.endsWith(".m4a") })
        executor.finish()
        executor.finished.await()
        assertEquals(2, acquirer.closed)
        assertFalse(cacheDirectory.listFiles().orEmpty().any { it.name.endsWith(".m4a") })
    }

    private fun service(executor: FakeExecutor, timeoutMillis: Long = 60_000) = SentenceAudioCaptureService(cacheDirectory, CountingAcquirer(), executor, timeoutMillis, ioDispatcher = Dispatchers.Unconfined)
    private fun request(playable: String? = "https://media.example/original.mp4", index: Int? = 0, external: Boolean = false, externalValue: String? = null) = SentenceAudioCaptureRequest(
        SentenceAudioInputSnapshot("https://media.example/original.mp4", playable, emptyList(), emptyList(), emptyList(), true, 7, 1, index, external, externalValue), 1.0, 2.0,
    )
    private fun assertFailure(expected: AnkiSentenceAudioFailure, actual: AnkiSentenceAudioPreparation) = assertEquals(expected, assertType<AnkiSentenceAudioPreparation.Unavailable>(actual).failure)
    private inline fun <reified T> assertType(value: Any): T {
        assertTrue(value is T)
        return value as T
    }

    private class CountingAcquirer : SentenceAudioInputAcquirer {
        var closed = 0
        override suspend fun acquire(input: SentenceAudioInputSpec) = object : SentenceAudioInputLease {
            override val ffmpegValue = input.value
            override val tlsCaFile: String? = "/tmp/cacert.pem"
            override fun close() { closed++ }
        }
    }
    private open class FakeExecutor(
        selected: List<SentenceAudioCommandResult> = listOf(SentenceAudioCommandResult.Success(selectedAudio)),
        private val all: List<SentenceAudioCommandResult> = listOf(SentenceAudioCommandResult.Success("")),
        private val discovery: List<SentenceAudioCommandResult> = listOf(SentenceAudioCommandResult.Success("")),
        ffmpeg: List<SentenceAudioCommandResult> = listOf(SentenceAudioCommandResult.Success()),
        private val writeOutput: Boolean = true,
        private val neverFinish: Boolean = false,
    ) : SentenceAudioCommandExecutor {
        private val selectedQueue = ArrayDeque(selected)
        private val allQueue = ArrayDeque(all)
        private val discoveryQueue = ArrayDeque(discovery)
        private val ffmpegQueue = ArrayDeque(ffmpeg)
        val probeInputs = mutableListOf<String>(); val probeSelectors = mutableListOf<String>(); val ffmpegArguments = mutableListOf<List<String>>(); var ffmpegCalls = 0; var allProbeCalls = 0
        override suspend fun executeFfprobe(arguments: Array<String>, onNativeFinished: () -> Unit): SentenceAudioCommandResult {
            probeInputs += arguments.last(); val selector = arguments[arguments.indexOf("-select_streams") + 1]; probeSelectors += selector
            val result = when (selector) { "a" -> if ("-codec_whitelist" in arguments) { allProbeCalls++; allQueue.removeFirstOrNull() ?: SentenceAudioCommandResult.Failed } else discoveryQueue.removeFirstOrNull() ?: SentenceAudioCommandResult.Failed; else -> selectedQueue.removeFirstOrNull() ?: SentenceAudioCommandResult.Failed }
            onNativeFinished(); return result
        }
        override suspend fun executeFfmpeg(arguments: Array<String>, onNativeFinished: () -> Unit): SentenceAudioCommandResult {
            ffmpegCalls++; ffmpegArguments += arguments.toList()
            if (neverFinish) return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { onNativeFinished() }
            }
            val result = ffmpegQueue.removeFirstOrNull() ?: SentenceAudioCommandResult.Failed
            if (result is SentenceAudioCommandResult.Success && writeOutput) File(arguments.last()).writeBytes(byteArrayOf(1, 2, 3))
            onNativeFinished(); return result
        }
    }
    private class BlockingExecutor : SentenceAudioCommandExecutor {
        val started = kotlinx.coroutines.CompletableDeferred<Unit>(); val finished = kotlinx.coroutines.CompletableDeferred<Unit>(); private val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        fun finish() = release.complete(Unit)
        override suspend fun executeFfprobe(arguments: Array<String>, onNativeFinished: () -> Unit) = SentenceAudioCommandResult.Success(selectedAudio).also { onNativeFinished() }
        override suspend fun executeFfmpeg(arguments: Array<String>, onNativeFinished: () -> Unit): SentenceAudioCommandResult =
            suspendCancellableCoroutine { continuation ->
                File(arguments.last()).writeBytes(byteArrayOf(1, 2, 3))
                started.complete(Unit)
                release.invokeOnCompletion {
                    onNativeFinished()
                    finished.complete(Unit)
                    if (continuation.isActive) continuation.resume(SentenceAudioCommandResult.Success())
                }
            }
    }
    private companion object { const val selectedAudio = "codec_type=audio\ncodec_name=aac" }
}
