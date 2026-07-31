package eu.kanade.tachiyomi.ui.player.controls

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import chimahon.anki.AnkiMediaWarning
import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioInputSource
import chimahon.anki.AnkiSentenceAudioPlayableFallback
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
                        when (warning.diagnostic?.inputSource) {
                            AnkiSentenceAudioInputSource.ORIGINAL_VIDEO -> {
                                KMR.strings.anki_sentence_audio_probe_failed_original
                            }
                            AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO -> {
                                KMR.strings.anki_sentence_audio_probe_failed_playable
                            }
                            AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO,
                            null -> KMR.strings.anki_sentence_audio_probe_failed
                        }
                    }
                    AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND -> {
                        when (warning.diagnostic?.inputSource) {
                            AnkiSentenceAudioInputSource.ORIGINAL_VIDEO -> {
                                when (warning.diagnostic?.playableFallback) {
                                    AnkiSentenceAudioPlayableFallback.MISSING -> {
                                        KMR.strings
                                            .anki_sentence_audio_streams_not_found_original_playable_missing
                                    }
                                    AnkiSentenceAudioPlayableFallback.SAME_AS_ORIGINAL -> {
                                        KMR.strings
                                            .anki_sentence_audio_streams_not_found_original_playable_same
                                    }
                                    AnkiSentenceAudioPlayableFallback.UNAVAILABLE -> {
                                        KMR.strings
                                            .anki_sentence_audio_streams_not_found_original_playable_unavailable
                                    }
                                    null -> KMR.strings.anki_sentence_audio_streams_not_found_original
                                }
                            }
                            AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO -> {
                                KMR.strings.anki_sentence_audio_streams_not_found_playable
                            }
                            AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO -> {
                                KMR.strings.anki_sentence_audio_streams_not_found_external
                            }
                            null -> KMR.strings.anki_sentence_audio_streams_not_found
                        }
                    }
                    AnkiSentenceAudioFailure.AUDIO_CODEC_RESTRICTED -> {
                        when (warning.diagnostic?.inputSource) {
                            AnkiSentenceAudioInputSource.ORIGINAL_VIDEO -> {
                                KMR.strings.anki_sentence_audio_codec_restricted_original
                            }
                            AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO -> {
                                KMR.strings.anki_sentence_audio_codec_restricted_playable
                            }
                            AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO -> {
                                KMR.strings.anki_sentence_audio_codec_restricted_external
                            }
                            null -> KMR.strings.anki_sentence_audio_codec_restricted
                        }
                    }
                    AnkiSentenceAudioFailure.AUDIO_STREAM_INDEX_UNAVAILABLE -> {
                        KMR.strings.anki_sentence_audio_stream_index_unavailable
                    }
                    AnkiSentenceAudioFailure.AUDIO_STREAM_NOT_AUDIO -> {
                        KMR.strings.anki_sentence_audio_stream_not_audio
                    }
                    AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED -> {
                        KMR.strings.anki_sentence_audio_stream_protected
                    }
                    AnkiSentenceAudioFailure.AUDIO_STREAM_UNREADABLE -> {
                        KMR.strings.anki_sentence_audio_stream_unreadable
                    }
                    AnkiSentenceAudioFailure.EXTRACTION_FAILED -> {
                        KMR.strings.anki_sentence_audio_extraction_failed
                    }
                    AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_MISSING -> {
                        KMR.strings.anki_sentence_audio_extraction_output_missing
                    }
                    AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_READ_FAILED -> {
                        KMR.strings.anki_sentence_audio_extraction_output_read_failed
                    }
                    AnkiSentenceAudioFailure.EXTRACTION_STREAM_MAPPING_FAILED -> {
                        KMR.strings.anki_sentence_audio_extraction_stream_mapping_failed
                    }
                    AnkiSentenceAudioFailure.EXTRACTION_SOURCE_READ_FAILED -> {
                        when (warning.diagnostic?.inputSource) {
                            AnkiSentenceAudioInputSource.ORIGINAL_VIDEO -> {
                                KMR.strings.anki_sentence_audio_extraction_source_read_failed_original
                            }
                            AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO -> {
                                KMR.strings.anki_sentence_audio_extraction_source_read_failed_playable
                            }
                            AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO,
                            null -> KMR.strings.anki_sentence_audio_extraction_source_read_failed
                        }
                    }
                    AnkiSentenceAudioFailure.EXTRACTION_SEEK_FAILED -> {
                        KMR.strings.anki_sentence_audio_extraction_seek_failed
                    }
                    AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_WRITE_FAILED -> {
                        KMR.strings.anki_sentence_audio_extraction_output_write_failed
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
