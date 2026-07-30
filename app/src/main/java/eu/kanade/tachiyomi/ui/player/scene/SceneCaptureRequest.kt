package eu.kanade.tachiyomi.ui.player.scene

import android.graphics.Bitmap
import chimahon.anki.AnkiSentenceAudioFailure
import `is`.xyz.mpv.MPVLib
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps the captured still alive while either its popup owner or the accepted mining job uses it.
 */
internal class OwnedResource<T : Any>(
    resource: T,
    private val disposer: (T) -> Unit,
) : Closeable {
    private val lock = Any()
    private var resource: T? = resource
    private var ownerOpen = true
    private var leases = 0

    fun valueOrNull(): T? = synchronized(lock) { resource }

    fun acquireLease(): Lease? = synchronized(lock) {
        if (!ownerOpen || resource == null) return null
        leases += 1
        Lease(this)
    }

    override fun close() {
        disposeIfReady(
            synchronized(lock) {
                if (!ownerOpen) return
                ownerOpen = false
                takeResourceForDisposal()
            },
        )
    }

    private fun releaseLease() {
        disposeIfReady(
            synchronized(lock) {
                check(leases > 0) { "Owned resource lease released more than once" }
                leases -= 1
                takeResourceForDisposal()
            },
        )
    }

    private fun takeResourceForDisposal(): T? {
        if (ownerOpen || leases != 0) return null
        return resource.also { resource = null }
    }

    private fun disposeIfReady(value: T?) {
        value?.let(disposer)
    }

    class Lease internal constructor(
        private val owner: OwnedResource<*>,
    ) : Closeable {
        private val lock = Any()
        private var open = true

        override fun close() {
            synchronized(lock) {
                if (!open) return
                open = false
            }
            owner.releaseLease()
        }
    }
}

internal class OwnedBitmap private constructor(
    private val owner: OwnedResource<Bitmap>,
) : Closeable {
    constructor(bitmap: Bitmap) : this(
        OwnedResource(bitmap) {
            if (!it.isRecycled) it.recycle()
        },
    )

    fun bitmapOrNull(): Bitmap? = owner.valueOrNull()

    fun acquireLease(): OwnedResource.Lease? = owner.acquireLease()

    override fun close() = owner.close()
}

internal class SceneCaptureRequest(
    val videoInput: SceneVideoInputSpec?,
    val sentenceAudioInput: SceneVideoInputSpec?,
    val sentenceAudioFailure: AnkiSentenceAudioFailure? = null,
    val resolvedTiming: SceneResolvedTiming?,
    private val stillFallback: OwnedBitmap,
) : Closeable {
    fun fallbackBitmapOrNull(): Bitmap? = stillFallback.bitmapOrNull()

    fun acquireMiningLease(): OwnedResource.Lease? = stillFallback.acquireLease()

    override fun close() = stillFallback.close()
}

internal class CapturedOcrFrame(
    val request: SceneCaptureRequest,
) : Closeable {
    val bitmap: Bitmap?
        get() = request.fallbackBitmapOrNull()

    override fun close() = request.close()
}

internal interface SceneMpvPropertyReader {
    fun double(name: String): Double?

    fun string(name: String): String?

    fun boolean(name: String): Boolean?

    fun int(name: String): Int?
}

internal object DirectSceneMpvPropertyReader : SceneMpvPropertyReader {
    override fun double(name: String): Double? = runCatching { MPVLib.getPropertyDouble(name) }.getOrNull()

    override fun string(name: String): String? = runCatching { MPVLib.getPropertyString(name) }.getOrNull()

    override fun boolean(name: String): Boolean? = runCatching { MPVLib.getPropertyBoolean(name) }.getOrNull()

    override fun int(name: String): Int? = runCatching { MPVLib.getPropertyInt(name) }.getOrNull()
}

internal data class SceneMpvSnapshot(
    val anchorMediaSeconds: Double,
    val mediaDurationSeconds: Double?,
    val subtitleStartSeconds: Double?,
    val subtitleEndSeconds: Double?,
    val subtitleSpeed: Double,
    val subtitleDelaySeconds: Double,
    val playableValue: String?,
    val selectedVideoId: Int,
    val selectedVideoFfmpegIndex: Int,
    val selectedAudioId: Int?,
    val selectedExternalAudioValue: String?,
    val selectedAudioIsExternal: Boolean,
    val audioTrackCount: Int,
    val seekable: Boolean?,
    val selectedAudioFfmpegIndex: Int? = null,
)

/**
 * mpv offers no multi-property transaction. The request factory therefore reads this immutable
 * snapshot both before and after the still capture and accepts only unchanged player state.
 */
internal class SceneMpvSnapshotReader(
    private val properties: SceneMpvPropertyReader = DirectSceneMpvPropertyReader,
) {
    fun read(): SceneMpvSnapshot? {
        val anchor = properties.double("time-pos")
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return null
        val duration = properties.double("duration")
            ?.takeIf { it.isFinite() && it >= 0.0 }
        val speed = properties.double("sub-speed")
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return null
        val delay = properties.double("sub-delay")
            ?.takeIf(Double::isFinite)
            ?: return null
        val selectedVideo = selectedVideo() ?: return null
        val selectedAudio = selectedAudio()

        return SceneMpvSnapshot(
            anchorMediaSeconds = anchor,
            mediaDurationSeconds = duration,
            subtitleStartSeconds = properties.double("sub-start/full"),
            subtitleEndSeconds = properties.double("sub-end/full"),
            subtitleSpeed = speed,
            subtitleDelaySeconds = delay,
            playableValue = properties.string("path")?.takeIf(String::isNotBlank),
            selectedVideoId = selectedVideo.id,
            selectedVideoFfmpegIndex = selectedVideo.ffmpegIndex,
            selectedAudioId = selectedAudio.id,
            selectedExternalAudioValue = selectedAudio.externalValue,
            selectedAudioIsExternal = selectedAudio.isExternal,
            audioTrackCount = selectedAudio.trackCount,
            seekable = properties.boolean("seekable"),
            selectedAudioFfmpegIndex = selectedAudio.ffmpegIndex,
        )
    }

    private fun selectedVideo(): SelectedVideoSnapshot? {
        val trackCount = properties.int("track-list/count") ?: return null
        val index = (0 until trackCount).firstOrNull { index ->
            properties.string("track-list/$index/type") == "video" &&
                properties.boolean("track-list/$index/selected") == true
        } ?: return null
        if (properties.boolean("track-list/$index/external") == true) return null
        return SelectedVideoSnapshot(
            id = properties.int("track-list/$index/id") ?: return null,
            ffmpegIndex = properties.int("track-list/$index/ff-index")
                ?.takeIf { it >= 0 }
                ?: return null,
        )
    }

    private fun selectedAudio(): SelectedAudioSnapshot {
        val trackCount = properties.int("track-list/count")
            ?: return SelectedAudioSnapshot()
        val audioTrackCount = (0 until trackCount).count { index ->
            properties.string("track-list/$index/type") == "audio"
        }
        val selectedAudioId = properties.string("aid")?.toIntOrNull()
            ?: properties.int("aid")
            ?: return SelectedAudioSnapshot(trackCount = audioTrackCount)
        val index = (0 until trackCount).firstOrNull { index ->
            properties.string("track-list/$index/type") == "audio" &&
                properties.int("track-list/$index/id") == selectedAudioId
        } ?: return SelectedAudioSnapshot(id = selectedAudioId, trackCount = audioTrackCount)
        val externalValue = properties.string("track-list/$index/external-filename")
            ?.takeIf(String::isNotBlank)
        return SelectedAudioSnapshot(
            id = selectedAudioId,
            externalValue = externalValue,
            isExternal = properties.boolean("track-list/$index/external") == true ||
                externalValue != null,
            trackCount = audioTrackCount,
            ffmpegIndex = properties.int("track-list/$index/ff-index")
                ?.takeIf { it >= 0 },
        )
    }

    private data class SelectedAudioSnapshot(
        val id: Int? = null,
        val externalValue: String? = null,
        val isExternal: Boolean = false,
        val trackCount: Int = 0,
        val ffmpegIndex: Int? = null,
    )

    private data class SelectedVideoSnapshot(
        val id: Int,
        val ffmpegIndex: Int,
    )
}

internal class SceneCaptureRequestFactory(
    private val mpvSnapshotReader: SceneMpvSnapshotReader = SceneMpvSnapshotReader(),
) {
    suspend fun captureSubtitle(
        videoSnapshot: (SceneMpvSnapshot) -> SceneCaptureInputSnapshot?,
        parsedSubtitleCandidates: List<SceneRangeCandidate>,
        captureFallback: suspend () -> Bitmap?,
    ): SceneCaptureRequest? {
        return capture(videoSnapshot, captureFallback) { mpv ->
            val timingSnapshot = mpv.toTimingSnapshot()
            val mpvRange = SceneRangeEndpointPair(
                startSeconds = mpv.subtitleStartSeconds,
                endSeconds = mpv.subtitleEndSeconds,
                clockDomain = SceneClockDomain.SUBTITLE,
                provenance = SceneRangeProvenance.MPV_SUBTITLE_PROPERTIES,
            )
            val parsed = parsedSubtitleCandidates.filter {
                it.clockDomain == SceneClockDomain.SUBTITLE &&
                    it.provenance == SceneRangeProvenance.PARSED_SUBTITLE_CUE
            }
            SceneTimingResolver.resolve(
                snapshot = timingSnapshot,
                mpvSubtitleRange = mpvRange,
                parsedSubtitleRanges = parsed,
                playbackFallback = playbackFallbackFor(mpv),
            )
        }
    }

    suspend fun captureOcr(
        videoSnapshot: (SceneMpvSnapshot) -> SceneCaptureInputSnapshot?,
        paddingSeconds: Double,
        captureFallback: suspend () -> Bitmap?,
    ): CapturedOcrFrame? {
        val request = capture(videoSnapshot, captureFallback) { mpv ->
            val timingSnapshot = mpv.toTimingSnapshot()
            val range = SceneTimingResolver.ocrRange(
                anchorSeconds = mpv.anchorMediaSeconds,
                paddingSeconds = paddingSeconds,
                mediaDurationSeconds = mpv.mediaDurationSeconds,
            ) ?: return@capture null
            SceneTimingResolver.resolve(
                snapshot = timingSnapshot,
                mpvSubtitleRange = null,
                parsedSubtitleRanges = emptyList(),
                playbackFallback = range,
            )
        } ?: return null
        return CapturedOcrFrame(request)
    }

    private suspend fun capture(
        videoSnapshot: (SceneMpvSnapshot) -> SceneCaptureInputSnapshot?,
        captureFallback: suspend () -> Bitmap?,
        resolveTiming: (SceneMpvSnapshot) -> SceneResolvedTiming?,
    ): SceneCaptureRequest? {
        val beforeMpv = mpvSnapshotReader.read() ?: return null
        val beforeVideo = videoSnapshot(beforeMpv) ?: return null
        val fallback = captureFallback() ?: return null
        var transferred = false
        try {
            val afterMpv = mpvSnapshotReader.read() ?: return null
            val afterVideo = videoSnapshot(afterMpv) ?: return null
            if (!sameCaptureState(beforeMpv, afterMpv) || beforeVideo != afterVideo) return null

            val videoInput = SceneVideoInputResolver.resolve(beforeVideo.video)
            val sentenceAudio = when {
                beforeVideo.sentenceAudio != null -> {
                    val input = SceneVideoInputResolver.resolve(beforeVideo.sentenceAudio)
                    SentenceAudioInputResolution(
                        input = input,
                        failure = AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE.takeIf { input == null },
                    )
                }
                beforeMpv.selectedAudioId != null &&
                    beforeMpv.selectedAudioFfmpegIndex == null &&
                    beforeMpv.audioTrackCount != 1 -> {
                    SentenceAudioInputResolution(
                        input = null,
                        failure = AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE,
                    )
                }
                else -> SentenceAudioInputResolution(
                    input = videoInput,
                    failure = AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE.takeIf { videoInput == null },
                )
            }
            val request = SceneCaptureRequest(
                videoInput = videoInput,
                sentenceAudioInput = sentenceAudio.input,
                sentenceAudioFailure = sentenceAudio.failure,
                resolvedTiming = resolveTiming(beforeMpv),
                stillFallback = OwnedBitmap(fallback),
            )
            transferred = true
            return request
        } finally {
            if (!transferred && !fallback.isRecycled) fallback.recycle()
        }
    }

    private fun sameCaptureState(before: SceneMpvSnapshot, after: SceneMpvSnapshot): Boolean {
        return closeEnough(before.anchorMediaSeconds, after.anchorMediaSeconds, SCENE_ANCHOR_TOLERANCE_SECONDS) &&
            nullableDoubleEquals(before.mediaDurationSeconds, after.mediaDurationSeconds) &&
            nullableDoubleEquals(before.subtitleStartSeconds, after.subtitleStartSeconds) &&
            nullableDoubleEquals(before.subtitleEndSeconds, after.subtitleEndSeconds) &&
            nullableDoubleEquals(before.subtitleSpeed, after.subtitleSpeed) &&
            nullableDoubleEquals(before.subtitleDelaySeconds, after.subtitleDelaySeconds) &&
            before.playableValue == after.playableValue &&
            before.selectedVideoId == after.selectedVideoId &&
            before.selectedVideoFfmpegIndex == after.selectedVideoFfmpegIndex &&
            before.selectedAudioId == after.selectedAudioId &&
            before.selectedExternalAudioValue == after.selectedExternalAudioValue &&
            before.selectedAudioIsExternal == after.selectedAudioIsExternal &&
            before.audioTrackCount == after.audioTrackCount &&
            before.seekable == after.seekable &&
            before.selectedAudioFfmpegIndex == after.selectedAudioFfmpegIndex
    }

    private data class SentenceAudioInputResolution(
        val input: SceneVideoInputSpec?,
        val failure: AnkiSentenceAudioFailure?,
    )

    private fun nullableDoubleEquals(first: Double?, second: Double?): Boolean {
        if (first == null || second == null) return first == second
        return closeEnough(first, second, DOUBLE_SNAPSHOT_TOLERANCE_SECONDS)
    }

    private fun closeEnough(first: Double, second: Double, tolerance: Double): Boolean {
        if (!first.isFinite() || !second.isFinite()) return first.toBits() == second.toBits()
        return abs(first - second) <= tolerance
    }

    private fun SceneMpvSnapshot.toTimingSnapshot(): SceneTimingSnapshot {
        return SceneTimingSnapshot(
            anchorSeconds = anchorMediaSeconds,
            subtitleSpeed = subtitleSpeed,
            subtitleDelaySeconds = subtitleDelaySeconds,
            mediaDurationSeconds = mediaDurationSeconds,
        )
    }

    private fun playbackFallbackFor(snapshot: SceneMpvSnapshot): SceneRangeCandidate {
        val duration = snapshot.mediaDurationSeconds
        val range = if (duration == null) {
            val start = max(0.0, snapshot.anchorMediaSeconds - DEFAULT_PLAYBACK_FALLBACK_SECONDS / 2.0)
            start to max(snapshot.anchorMediaSeconds, start + DEFAULT_PLAYBACK_FALLBACK_SECONDS)
        } else {
            val targetDuration = min(DEFAULT_PLAYBACK_FALLBACK_SECONDS, duration)
            val start = (snapshot.anchorMediaSeconds - targetDuration / 2.0)
                .coerceIn(0.0, max(0.0, duration - targetDuration))
            start to (start + targetDuration)
        }
        return SceneRangeCandidate(
            startSeconds = range.first,
            endSeconds = range.second,
            clockDomain = SceneClockDomain.MEDIA,
            provenance = SceneRangeProvenance.PLAYBACK_POSITION,
        )
    }

    private companion object {
        const val DEFAULT_PLAYBACK_FALLBACK_SECONDS = 1.0
        const val DOUBLE_SNAPSHOT_TOLERANCE_SECONDS = 0.000_001
    }
}

internal data class SceneCaptureInputSnapshot(
    val video: SceneVideoInputSnapshot,
    val sentenceAudio: SceneVideoInputSnapshot?,
)
