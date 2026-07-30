package eu.kanade.tachiyomi.ui.player.controls

import chimahon.anki.AnkiProfile
import chimahon.dictionary.DictionaryProfileResolver

internal enum class SubtitleLookupTapAction {
    Dismiss,
    Open,
    Replace,
}

internal data class SubtitleLookupTapTransition(
    val action: SubtitleLookupTapAction,
    val cancelActiveCapture: Boolean,
    val pausePlayer: Boolean,
)

internal enum class SubtitleLookupCaptureResultAction {
    Apply,
    Release,
}

internal data class DictionaryProfileResolutionKey(
    val animeId: Long?,
    val sourceId: Long?,
    val sourceLang: String?,
)

internal fun subtitleLookupTapTransition(
    matchesCurrentTap: Boolean,
    hasOpenLookup: Boolean,
    hasActiveCapture: Boolean,
): SubtitleLookupTapTransition {
    return when {
        matchesCurrentTap -> SubtitleLookupTapTransition(
            action = SubtitleLookupTapAction.Dismiss,
            cancelActiveCapture = false,
            pausePlayer = false,
        )
        hasOpenLookup || hasActiveCapture -> SubtitleLookupTapTransition(
            action = SubtitleLookupTapAction.Replace,
            cancelActiveCapture = hasActiveCapture,
            pausePlayer = false,
        )
        else -> SubtitleLookupTapTransition(
            action = SubtitleLookupTapAction.Open,
            cancelActiveCapture = false,
            pausePlayer = true,
        )
    }
}

internal fun subtitleLookupPauseStateAfterTap(
    wasPlayerAlreadyPaused: Boolean,
    playerWasPausedAtTap: Boolean,
    pausePlayer: Boolean,
): Boolean {
    return if (pausePlayer) playerWasPausedAtTap else wasPlayerAlreadyPaused
}

internal fun subtitleLookupCaptureResultAction(
    captureIsActive: Boolean,
    captureGeneration: Int,
    currentGeneration: Int,
    hasOpenLookup: Boolean,
): SubtitleLookupCaptureResultAction {
    return if (captureIsActive && captureGeneration == currentGeneration && hasOpenLookup) {
        SubtitleLookupCaptureResultAction.Apply
    } else {
        SubtitleLookupCaptureResultAction.Release
    }
}

internal fun DictionaryProfileResolver.resolveForPlayer(
    key: DictionaryProfileResolutionKey,
): AnkiProfile {
    return resolve(
        mangaId = key.animeId ?: 0L,
        sourceId = key.sourceId ?: 0L,
        sourceLang = key.sourceLang.orEmpty(),
    )
}
