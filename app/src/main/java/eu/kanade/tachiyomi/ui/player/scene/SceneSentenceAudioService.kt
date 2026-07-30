package eu.kanade.tachiyomi.ui.player.scene

import android.content.Context
import chimahon.anki.AnkiMediaSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal fun interface SceneSentenceAudioService {
    suspend fun prepare(request: SceneCaptureRequest): AnkiMediaSource?
}

internal class FrozenSceneSentenceAudioService private constructor(
    private val cacheDirectory: File,
    private val inputAcquirer: SceneInputAcquirer,
    private val commandExecutor: SceneCommandExecutor,
) : SceneSentenceAudioService {
    constructor(context: Context) : this(
        cacheDirectory = context.cacheDir,
        inputAcquirer = AndroidSceneInputAcquirer(context),
        commandExecutor = FfmpegKitSceneCommandExecutor(),
    )

    override suspend fun prepare(request: SceneCaptureRequest): AnkiMediaSource? {
        val input = request.sentenceAudioInput ?: return null
        val range = request.resolvedTiming?.audioRange ?: return null
        return withTimeoutOrNull(AUDIO_TIMEOUT_MILLIS) {
            withContext(Dispatchers.IO) {
                if (!isAudioSafe(input)) {
                    return@withContext null
                }
                val lease = inputAcquirer.acquire(input) ?: return@withContext null
                val output = File(cacheDirectory, "chimahon_sentence_audio_${UUID.randomUUID()}.m4a")
                val inputCleanup = SceneNativeCleanup(lease::close)
                val outputCleanup = SceneNativeCleanup(output::delete)
                try {
                    output.delete()
                    val result = commandExecutor.executeFfmpeg(
                        SceneFfmpegArguments.sentenceAudio(
                            input = input,
                            acquiredInputValue = lease.ffmpegValue,
                            range = range,
                            outputFile = output.absolutePath,
                            tlsCaFile = lease.tlsCaFile,
                        ),
                    ) {
                        inputCleanup.nativeFinished()
                        outputCleanup.nativeFinished()
                    }
                    if (result !is SceneCommandResult.Success || !output.isFile || output.length() == 0L) {
                        return@withContext null
                    }
                    val bytes = output.readBytes()
                    AnkiMediaSource.Bytes(
                        data = bytes,
                        preferredBaseName = "chimahon_sentence_${bytes.sha256()}",
                        extension = "m4a",
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@withContext null
                } finally {
                    inputCleanup.release()
                    outputCleanup.release()
                }
            }
        }
    }

    private suspend fun isAudioSafe(input: SceneVideoInputSpec): Boolean {
        val lease = inputAcquirer.acquire(input) ?: return false
        val cleanup = SceneNativeCleanup(lease::close)
        return try {
            val probe = commandExecutor.executeFfprobe(
                SceneFfmpegArguments.audioProbe(input, lease.ffmpegValue, lease.tlsCaFile),
                cleanup::nativeFinished,
            )
            probe is SceneCommandResult.Success && SceneMediaProbe.inspectAudio(probe.output)
        } finally {
            cleanup.release()
        }
    }

    private fun ByteArray.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val AUDIO_TIMEOUT_MILLIS = 60_000L
    }
}
