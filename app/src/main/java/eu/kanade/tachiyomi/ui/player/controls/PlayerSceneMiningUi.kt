package eu.kanade.tachiyomi.ui.player.controls

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import chimahon.anki.AnkiMediaWarning
import chimahon.anki.AnkiSentenceAudioFailure
import eu.kanade.tachiyomi.ui.player.scene.PlayerSceneMiningProgress
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun PlayerSceneMiningProgressDialog(
    progress: PlayerSceneMiningProgress,
    onCancel: () -> Unit,
) {
    if (!progress.isBusy) return
    val status = when (progress) {
        PlayerSceneMiningProgress.Idle -> return
        PlayerSceneMiningProgress.Preparing -> stringResource(KMR.strings.anki_scene_preparing)
        PlayerSceneMiningProgress.Committing -> stringResource(KMR.strings.anki_scene_committing)
    }
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            if (progress.canCancel) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(KMR.strings.anki_scene_cancel))
                }
            }
        },
        text = { Text(status) },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

internal fun Context.showPlayerAnkiMediaWarnings(warnings: List<AnkiMediaWarning>) {
    warnings.distinct().forEach { warning ->
        toast(
            when (warning) {
                AnkiMediaWarning.SceneGenerationFailed -> KMR.strings.anki_scene_fallback_generation
                AnkiMediaWarning.AnimatedStorageFailed -> KMR.strings.anki_scene_fallback_storage
                AnkiMediaWarning.StillStorageFailed -> KMR.strings.anki_scene_still_storage_failed
                is AnkiMediaWarning.SentenceAudioGenerationFailed -> when (warning.failure) {
                    AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE -> {
                        KMR.strings.anki_sentence_audio_track_mapping_unavailable
                    }
                    AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE -> {
                        KMR.strings.anki_sentence_audio_source_unavailable
                    }
                    AnkiSentenceAudioFailure.TIMING_UNAVAILABLE -> {
                        KMR.strings.anki_sentence_audio_timing_unavailable
                    }
                    AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED -> {
                        KMR.strings.anki_sentence_audio_probe_failed
                    }
                    AnkiSentenceAudioFailure.AUDIO_STREAM_UNREADABLE -> {
                        KMR.strings.anki_sentence_audio_stream_unreadable
                    }
                    AnkiSentenceAudioFailure.EXTRACTION_FAILED -> {
                        KMR.strings.anki_sentence_audio_extraction_failed
                    }
                    AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT -> {
                        KMR.strings.anki_sentence_audio_extraction_timed_out
                    }
                    AnkiSentenceAudioFailure.UNKNOWN -> KMR.strings.anki_sentence_audio_generation_failed
                }
                AnkiMediaWarning.SentenceAudioStorageFailed -> KMR.strings.anki_sentence_audio_storage_failed
            },
        )
    }
}
