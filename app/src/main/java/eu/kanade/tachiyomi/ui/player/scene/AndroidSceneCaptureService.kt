package eu.kanade.tachiyomi.ui.player.scene

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.webkit.MimeTypeMap
import chimahon.anki.AnkiMediaNaming
import chimahon.anki.AnkiScreenshotPreparation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal fun interface SceneCaptureService {
    suspend fun prepare(request: SceneCaptureRequest): AnkiScreenshotPreparation
}

internal class AndroidSceneCaptureService private constructor(
    private val sceneDirectory: File,
    private val inputAcquirer: SceneInputAcquirer,
    private val commandExecutor: SceneCommandExecutor,
    private val validate: (File) -> AnimatedAvifInfo?,
    private val av1EncoderName: () -> String?,
) : SceneCaptureService {
    constructor(context: Context) : this(
        sceneDirectory = File(context.cacheDir, SCENE_CACHE_DIRECTORY),
        inputAcquirer = AndroidSceneInputAcquirer(context),
        commandExecutor = FfmpegKitSceneCommandExecutor(),
        validate = AnimatedAvifValidator::validate,
        av1EncoderName = ::platformAv1EncoderName,
    )

    override suspend fun prepare(request: SceneCaptureRequest): AnkiScreenshotPreparation {
        val input = request.videoInput ?: return AnkiScreenshotPreparation.Failed(stillFallback = null)
        val range = request.resolvedTiming?.animationRange
            ?: return AnkiScreenshotPreparation.Failed(stillFallback = null)
        val encoderName = av1EncoderName()
        if (encoderName.isNullOrBlank()) {
            return AnkiScreenshotPreparation.Failed(stillFallback = null)
        }

        return withContext(Dispatchers.IO) {
            if (!isSafe(input)) {
                return@withContext AnkiScreenshotPreparation.Failed(stillFallback = null)
            }
            val lease = inputAcquirer.acquire(input)
                ?: return@withContext AnkiScreenshotPreparation.Failed(stillFallback = null)
            sceneDirectory.mkdirs()
            val output = File(sceneDirectory, "${UUID.randomUUID()}.avif")
            val inputCleanup = SceneNativeCleanup(lease::close)
            val outputCleanup = SceneNativeCleanup(output::delete)
            var transferred = false
            try {
                val result = commandExecutor.executeFfmpeg(
                    SceneFfmpegArguments.animatedAvif(
                        input = input,
                        acquiredInputValue = lease.ffmpegValue,
                        range = range,
                        outputFile = output.absolutePath,
                        encoderName = encoderName,
                        tlsCaFile = lease.tlsCaFile,
                    ),
                ) {
                    inputCleanup.nativeFinished()
                    outputCleanup.nativeFinished()
                }
                when (result) {
                    SceneCommandResult.Failed,
                    is SceneCommandResult.FfmpegFailed -> {
                        return@withContext AnkiScreenshotPreparation.Failed(stillFallback = null)
                    }
                    is SceneCommandResult.Success -> Unit
                }
                val info = validate(output)
                    ?.takeIf {
                        it.width in 1..MAX_OUTPUT_DIMENSION &&
                            it.height in 1..MAX_OUTPUT_DIMENSION &&
                            it.frameCount in 2..SceneFfmpegArguments.MAX_FRAME_COUNT &&
                            it.totalDurationMillis > 0L
                    }
                    ?: return@withContext AnkiScreenshotPreparation.Failed(stillFallback = null)
                val animation = AnkiMediaNaming.sceneFileSource(output)
                transferred = true
                AnkiScreenshotPreparation.Animated(
                    animation = animation,
                    stillFallback = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AnkiScreenshotPreparation.Failed(stillFallback = null)
            } finally {
                inputCleanup.release()
                if (!transferred) outputCleanup.release()
            }
        }
    }

    private suspend fun isSafe(input: SceneVideoInputSpec): Boolean {
        val lease = inputAcquirer.acquire(input) ?: return false
        val cleanup = SceneNativeCleanup(lease::close)
        return try {
            val result = commandExecutor.executeFfprobe(
                SceneFfmpegArguments.videoProbe(input, lease.ffmpegValue, lease.tlsCaFile),
                cleanup::nativeFinished,
            )
            result is SceneCommandResult.Success && SceneMediaProbe.inspect(result.output)
        } finally {
            cleanup.release()
        }
    }

    internal companion object {
        private const val SCENE_CACHE_DIRECTORY = "chimahon_scene_capture"
        private const val MAX_OUTPUT_DIMENSION = 640

        fun forTests(
            sceneDirectory: File,
            inputAcquirer: SceneInputAcquirer,
            commandExecutor: SceneCommandExecutor,
            validate: (File) -> AnimatedAvifInfo?,
            av1EncoderName: () -> String? = { TEST_AV1_ENCODER_NAME },
        ): AndroidSceneCaptureService {
            return AndroidSceneCaptureService(
                sceneDirectory = sceneDirectory,
                inputAcquirer = inputAcquirer,
                commandExecutor = commandExecutor,
                validate = validate,
                av1EncoderName = av1EncoderName,
            )
        }

        private fun platformAv1EncoderName(): String? {
            val mimeTypes = MimeTypeMap.getSingleton()
            val hasMimeMapping = mimeTypes.getMimeTypeFromExtension("avif")
                ?.equals("image/avif", ignoreCase = true) == true &&
                mimeTypes.getExtensionFromMimeType("image/avif")
                    ?.equals("avif", ignoreCase = true) == true
            if (!hasMimeMapping) return null

            return runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                    .asSequence()
                    .filter(MediaCodecInfo::isEncoder)
                    .filter { info ->
                        info.supportedTypes.any { it.equals(AV1_MIME_TYPE, ignoreCase = true) }
                    }
                    .firstOrNull { info ->
                        runCatching {
                            val capabilities = info.getCapabilitiesForType(AV1_MIME_TYPE)
                            val encoder = capabilities.encoderCapabilities ?: return@runCatching false
                            val video = capabilities.videoCapabilities ?: return@runCatching false
                            val supportsYuv420Planar = capabilities.colorFormats.contains(
                                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                            )
                            supportsYuv420Planar &&
                                encoder.isBitrateModeSupported(
                                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ,
                                ) &&
                                encoder.qualityRange.contains(MEDIACODEC_QUALITY) &&
                                video.areSizeAndRateSupported(
                                    MAX_OUTPUT_DIMENSION,
                                    MAX_OUTPUT_DIMENSION,
                                    SceneFfmpegArguments.FRAME_RATE,
                                )
                        }.getOrDefault(false)
                    }
                    ?.name
            }.getOrNull()
        }

        internal const val TEST_AV1_ENCODER_NAME = "test.av1.encoder"
        private const val AV1_MIME_TYPE = "video/av01"
        private const val MEDIACODEC_QUALITY = 35
    }
}
