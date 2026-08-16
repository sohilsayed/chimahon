package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.FFprobeSession
import com.arthenica.ffmpegkit.ReturnCode
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.player.sentenceaudio.AndroidSentenceAudioInputAcquirer
import eu.kanade.tachiyomi.ui.player.sentenceaudio.FfmpegKitSentenceAudioCommandExecutor
import eu.kanade.tachiyomi.ui.player.sentenceaudio.SentenceAudioCaptureRequest
import eu.kanade.tachiyomi.ui.player.sentenceaudio.SentenceAudioCaptureService
import eu.kanade.tachiyomi.ui.player.sentenceaudio.SentenceAudioInputSnapshot
import eu.kanade.tachiyomi.ui.player.sentenceaudio.SentenceAudioMpvSnapshot
import eu.kanade.tachiyomi.ui.player.sentenceaudio.createSentenceAudioDiagnosticLogger
import eu.kanade.tachiyomi.ui.player.sentenceaudio.resolveSeekability
import eu.kanade.tachiyomi.util.storage.toFFmpegReadString
import `is`.xyz.mpv.MPVLib
import chimahon.anki.AnkiMediaRequest
import chimahon.anki.AnkiSentenceAudioPreparation
import chimahon.anki.LazyAnkiSentenceAudioProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import org.aomedia.avif.android.AvifEncoder
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal data class VideoOcrAnimatedSceneRequest(
    val startSeconds: Double,
    val endSeconds: Double,
    val input: AnimatedSceneInputSnapshot?,
)

/** Input needed to recreate an animated scene after the dictionary popup has opened. */
internal data class AnimatedSceneInputSnapshot(
    val source: String,
    val videoUrl: String,
    val headers: List<Pair<String, String>>,
)

/**
 * Captures media from the player for Anki mining: still OCR frames, sentence audio slices,
 * and animated AVIF scenes encoded through the bundled libavif encoder.
 */
internal class PlayerMediaCaptureService(
    private val context: Context,
    private val cachePath: String,
    private val getVideo: () -> Video?,
    private val getSource: () -> AnimeSource?,
    private val getTimeSeconds: () -> Double,
    private val getOcrPaddingSeconds: () -> Double,
    private val readMpvSnapshot: () -> SentenceAudioMpvSnapshot,
    private val readMpvVideoPath: () -> String? = { MPVLib.getPropertyString("path") },
    private val prepareSentenceAudioOverride: (suspend (SentenceAudioCaptureRequest) -> AnkiSentenceAudioPreparation)? = null,
) {

    private val sentenceAudioCaptureService by lazy {
        SentenceAudioCaptureService(
            File(cachePath),
            AndroidSentenceAudioInputAcquirer(context),
            FfmpegKitSentenceAudioCommandExecutor(),
            diagnosticLogger = createSentenceAudioDiagnosticLogger(),
        )
    }

    suspend fun captureVideoFrameForOcr(): Bitmap? {
        val file = File(cachePath, "${System.currentTimeMillis()}_mpv_ocr_frame.png")
        return runCatching {
            withUIContext {
                file.delete()
                MPVLib.command(arrayOf("screenshot-to-file", file.absolutePath, "video"))
            }
            withIOContext {
                repeat(20) {
                    if (file.exists() && file.length() > 0L) {
                        return@withIOContext BitmapFactory.decodeFile(file.absolutePath)
                    }
                    Thread.sleep(25L)
                }
                file.takeIf { it.exists() && it.length() > 0L }
                    ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            }
        }.onFailure {
            logcat(LogPriority.ERROR, it)
        }.getOrNull().also {
            file.delete()
        }
    }

    fun createSubtitleAudioMediaRequest(startSeconds: Double?, endSeconds: Double?): AnkiMediaRequest =
        createSentenceAudioMediaRequest(startSeconds, endSeconds)

    fun createVideoOcrAudioMediaRequest(): AnkiMediaRequest {
        val center = getTimeSeconds()
        val padding = getOcrPaddingSeconds()
        return createSentenceAudioMediaRequest(center - padding, center + padding)
    }

    fun createVideoOcrAnimatedSceneRequest(): VideoOcrAnimatedSceneRequest {
        val center = getTimeSeconds()
        val padding = getOcrPaddingSeconds()
        return VideoOcrAnimatedSceneRequest(
            startSeconds = center - padding,
            endSeconds = center + padding,
            input = snapshotAnimatedSceneInput(),
        )
    }

    private fun snapshotAnimatedSceneInput(): AnimatedSceneInputSnapshot? {
        val video = getVideo() ?: return null
        val source = readMpvVideoPath()
            ?.takeIf { it.isNotBlank() }
            ?: video.videoUrl
        val animeSource = getSource() as? AnimeHttpSource
        return AnimatedSceneInputSnapshot(
            source = source,
            videoUrl = video.videoUrl,
            headers = (video.headers ?: animeSource?.headers)?.toList().orEmpty(),
        )
    }

    private fun createSentenceAudioMediaRequest(startSeconds: Double?, endSeconds: Double?): AnkiMediaRequest {
        val video = getVideo()
        val mpv = readMpvSnapshot()
        val source = getSource() as? AnimeHttpSource
        val snapshot = video?.let {
            SentenceAudioInputSnapshot(
                originalVideoValue = it.videoUrl,
                playableValue = mpv.playableValue,
                headers = (it.headers ?: source?.headers)?.toList().orEmpty(),
                ffmpegStreamArgs = it.ffmpegStreamArgs.orEmpty(),
                ffmpegVideoArgs = it.ffmpegVideoArgs.orEmpty(),
                seekable = resolveSeekability(mpv.seekable, it.videoUrl),
                selectedAudioId = mpv.selectedAudioId,
                audioTrackCount = mpv.audioTrackCount,
                selectedAudioFfmpegIndex = mpv.selectedAudioFfmpegIndex,
                selectedAudioIsExternal = mpv.selectedAudioIsExternal,
                selectedExternalAudioValue = mpv.selectedExternalAudioValue,
            )
        }
        val frozen = SentenceAudioCaptureRequest(snapshot, startSeconds, endSeconds)
        return AnkiMediaRequest(
            LazyAnkiSentenceAudioProvider {
                prepareSentenceAudioOverride?.invoke(frozen) ?: sentenceAudioCaptureService.prepare(frozen)
            },
        )
    }

    /**
     * Extracts an animated AVIF scene from the current video around [startSeconds]-[endSeconds]
     * using the bundled libavif encoder. Returns null on failure so callers can fall back to a still.
     */
    suspend fun captureAnimatedVideoForAnki(startSeconds: Double?, endSeconds: Double?): ByteArray? {
        return captureAnimatedVideoForAnki(
            input = snapshotAnimatedSceneInput(),
            startSeconds = startSeconds,
            endSeconds = endSeconds,
        )
    }

    private suspend fun captureAnimatedVideoForAnki(
        input: AnimatedSceneInputSnapshot?,
        startSeconds: Double?,
        endSeconds: Double?,
    ): ByteArray? {
        val start = startSeconds ?: return null
        val end = endSeconds ?: return null
        if (end <= start) return null

        val inputSnapshot = input ?: return null
        val yuvFile = File(context.cacheDir, "chimahon_scene_${System.currentTimeMillis()}.yuv")
        return try {
            withIOContext {
                yuvFile.delete()
                val rawInput = inputSnapshot.source
                val ffmpegInput = when {
                    inputSnapshot.videoUrl.startsWith("content://") -> Uri.parse(inputSnapshot.videoUrl).toFFmpegReadString(context)
                    rawInput.startsWith("file://") -> Uri.parse(rawInput).path ?: rawInput
                    else -> rawInput
                }.replace("\"", "\\\"")

                val headerOptions = if (rawInput.startsWith("http") && inputSnapshot.headers.isNotEmpty()) {
                    inputSnapshot.headers.joinToString("", "-headers '", "'") {
                        "${it.first}: ${it.second.replace("'", "'\\''")}\r\n"
                    }
                } else {
                    ""
                }
                val duration = (end - start).coerceIn(0.25, 10.0)
                val probe = probeVideoDimensions(ffmpegInput, headerOptions)
                val (outWidth, outHeight) = probe ?: return@withIOContext null
                val frameSize = outWidth * outHeight * 3 / 2
                val command = listOf(
                    headerOptions,
                    "-ss ${start.coerceAtLeast(0.0).formatSeconds()}",
                    "-t ${duration.formatSeconds()}",
                    "-i \"$ffmpegInput\"",
                    "-an",
                    "-sn",
                    "-dn",
                    "-map 0:v:0",
                    "-vf",
                    "scale=${outWidth}:${outHeight},fps=8,setsar=1",
                    "-frames:v",
                    "80",
                    "-pix_fmt",
                    "yuv420p",
                    "-c:v",
                    "rawvideo",
                    "-f",
                    "rawvideo",
                    "\"${yuvFile.absolutePath.replace("\"", "\\\"")}\"",
                    "-y",
                )
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val session = executeFfmpegCancellable(FFmpegKitConfig.parseArguments(command))
                if (!ReturnCode.isSuccess(session.returnCode) || !yuvFile.exists() || yuvFile.length() < frameSize) {
                    session.failStackTrace?.let { logcat(LogPriority.WARN) { it } }
                    return@withIOContext null
                }
                val data = yuvFile.readBytes()
                val frameCount = data.size / frameSize
                if (frameCount < 2) return@withIOContext null
                val frames = Array(frameCount) { i ->
                    data.copyOfRange(i * frameSize, (i + 1) * frameSize)
                }
                AvifEncoder.encodeYuv420p(
                    frames,
                    outWidth,
                    outHeight,
                    8,
                    AvifEncoder.REPETITION_COUNT_NONE,
                    35,
                    AvifEncoder.SPEED_FASTEST,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to capture animated scene" }
            null
        } finally {
            yuvFile.delete()
        }
    }

    suspend fun captureVideoOcrAnimatedForAnki(request: VideoOcrAnimatedSceneRequest): ByteArray? {
        return captureAnimatedVideoForAnki(
            input = request.input,
            startSeconds = request.startSeconds,
            endSeconds = request.endSeconds,
        )
    }

    private suspend fun executeFfmpegCancellable(arguments: Array<String>): FFmpegSession =
        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(arguments) { completedSession ->
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(completedSession))
                }
            }
            continuation.invokeOnCancellation { session.cancel() }
        }

    private suspend fun probeVideoDimensions(input: String, headerOptions: String): Pair<Int, Int>? =
        suspendCancellableCoroutine<FFprobeSession?> { continuation ->
            val command = FFmpegKitConfig.parseArguments(
                "$headerOptions -v error -select_streams v:0 -show_entries stream=width,height " +
                    "-of csv=p=0:s=x \"$input\"",
            )
            val session = FFprobeKit.executeWithArgumentsAsync(command) {
                if (ReturnCode.isSuccess(it.returnCode)) {
                    continuation.resumeWith(Result.success(it))
                } else {
                    continuation.resumeWith(Result.success(null))
                }
            }
            continuation.invokeOnCancellation { session.cancel() }
        }.let { session ->
            val dims = session?.allLogsAsString?.trim() ?: return null
            dims.split("x").mapNotNull { it.toIntOrNull() }.take(2).let { list ->
                if (list.size == 2) scaledSceneDimensions(list[0], list[1]) else null
            }
        }

    private fun scaledSceneDimensions(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        val scale = min(1.0, MAX_SCENE_DIMENSION.toDouble() / max(sourceWidth, sourceHeight))
        val width = (sourceWidth * scale / SCENE_DIMENSION_ALIGNMENT).toInt() * SCENE_DIMENSION_ALIGNMENT
        val height = (sourceHeight * scale / SCENE_DIMENSION_ALIGNMENT).toInt() * SCENE_DIMENSION_ALIGNMENT
        return (width.coerceAtLeast(SCENE_DIMENSION_ALIGNMENT)) to (height.coerceAtLeast(SCENE_DIMENSION_ALIGNMENT))
    }

    private fun Double.formatSeconds(): String {
        return String.format(Locale.US, "%.3f", this)
    }

    private companion object {
        const val MAX_SCENE_DIMENSION = 640
        const val SCENE_DIMENSION_ALIGNMENT = 16
    }
}
