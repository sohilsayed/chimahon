package eu.kanade.tachiyomi.ui.player.controls

import android.content.Context
import chimahon.anki.AnkiMediaWarning
import chimahon.anki.AnkiSentenceAudioFailure
import chimahon.anki.AnkiSentenceAudioInputSource
import chimahon.anki.AnkiSentenceAudioPlayableFallback
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.kmk.KMR

internal enum class PlayerSentenceAudioWarningKey {
    GENERATION_FAILED,
    TRACK_MAPPING_UNAVAILABLE,
    SOURCE_UNAVAILABLE,
    TIMING_UNAVAILABLE,
    PROBE_FAILED,
    PROBE_FAILED_ORIGINAL,
    PROBE_FAILED_PLAYABLE,
    STREAMS_NOT_FOUND,
    STREAMS_NOT_FOUND_ORIGINAL,
    STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_MISSING,
    STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_SAME,
    STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_UNAVAILABLE,
    STREAMS_NOT_FOUND_PLAYABLE,
    STREAMS_NOT_FOUND_EXTERNAL,
    CODEC_RESTRICTED,
    CODEC_RESTRICTED_ORIGINAL,
    CODEC_RESTRICTED_PLAYABLE,
    CODEC_RESTRICTED_EXTERNAL,
    STREAM_INDEX_UNAVAILABLE,
    STREAM_NOT_AUDIO,
    STREAM_PROTECTED,
    STREAM_UNREADABLE,
    EXTRACTION_FAILED,
    EXTRACTION_OUTPUT_MISSING,
    EXTRACTION_OUTPUT_READ_FAILED,
    EXTRACTION_STREAM_MAPPING_FAILED,
    EXTRACTION_SOURCE_READ_FAILED,
    EXTRACTION_SOURCE_READ_FAILED_ORIGINAL,
    EXTRACTION_SOURCE_READ_FAILED_PLAYABLE,
    EXTRACTION_SEEK_FAILED,
    EXTRACTION_OUTPUT_WRITE_FAILED,
    EXTRACTION_TIMED_OUT,
    STORAGE_FAILED,
}

internal fun AnkiMediaWarning.toPlayerSentenceAudioWarningKey(): PlayerSentenceAudioWarningKey = when (this) {
    AnkiMediaWarning.SentenceAudioStorageFailed -> PlayerSentenceAudioWarningKey.STORAGE_FAILED
    is AnkiMediaWarning.SentenceAudioGenerationFailed -> when (failure) {
        AnkiSentenceAudioFailure.TRACK_MAPPING_UNAVAILABLE -> PlayerSentenceAudioWarningKey.TRACK_MAPPING_UNAVAILABLE
        AnkiSentenceAudioFailure.SOURCE_UNAVAILABLE -> PlayerSentenceAudioWarningKey.SOURCE_UNAVAILABLE
        AnkiSentenceAudioFailure.TIMING_UNAVAILABLE -> PlayerSentenceAudioWarningKey.TIMING_UNAVAILABLE
        AnkiSentenceAudioFailure.AUDIO_PROBE_FAILED -> when (diagnostic?.inputSource) {
            AnkiSentenceAudioInputSource.ORIGINAL_VIDEO -> PlayerSentenceAudioWarningKey.PROBE_FAILED_ORIGINAL
            AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO -> PlayerSentenceAudioWarningKey.PROBE_FAILED_PLAYABLE
            AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO, null -> PlayerSentenceAudioWarningKey.PROBE_FAILED
        }
        AnkiSentenceAudioFailure.AUDIO_STREAMS_NOT_FOUND -> when (diagnostic?.inputSource) {
            AnkiSentenceAudioInputSource.ORIGINAL_VIDEO -> when (diagnostic?.playableFallback) {
                AnkiSentenceAudioPlayableFallback.MISSING -> PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_MISSING
                AnkiSentenceAudioPlayableFallback.SAME_AS_ORIGINAL -> PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_SAME
                AnkiSentenceAudioPlayableFallback.UNAVAILABLE -> PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_UNAVAILABLE
                null -> PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_ORIGINAL
            }
            AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO -> PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_PLAYABLE
            AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO -> PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_EXTERNAL
            null -> PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND
        }
        AnkiSentenceAudioFailure.AUDIO_CODEC_RESTRICTED -> when (diagnostic?.inputSource) {
            AnkiSentenceAudioInputSource.ORIGINAL_VIDEO -> PlayerSentenceAudioWarningKey.CODEC_RESTRICTED_ORIGINAL
            AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO -> PlayerSentenceAudioWarningKey.CODEC_RESTRICTED_PLAYABLE
            AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO -> PlayerSentenceAudioWarningKey.CODEC_RESTRICTED_EXTERNAL
            null -> PlayerSentenceAudioWarningKey.CODEC_RESTRICTED
        }
        AnkiSentenceAudioFailure.AUDIO_STREAM_INDEX_UNAVAILABLE -> PlayerSentenceAudioWarningKey.STREAM_INDEX_UNAVAILABLE
        AnkiSentenceAudioFailure.AUDIO_STREAM_NOT_AUDIO -> PlayerSentenceAudioWarningKey.STREAM_NOT_AUDIO
        AnkiSentenceAudioFailure.AUDIO_STREAM_PROTECTED -> PlayerSentenceAudioWarningKey.STREAM_PROTECTED
        AnkiSentenceAudioFailure.AUDIO_STREAM_UNREADABLE -> PlayerSentenceAudioWarningKey.STREAM_UNREADABLE
        AnkiSentenceAudioFailure.EXTRACTION_FAILED -> PlayerSentenceAudioWarningKey.EXTRACTION_FAILED
        AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_MISSING -> PlayerSentenceAudioWarningKey.EXTRACTION_OUTPUT_MISSING
        AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_READ_FAILED -> PlayerSentenceAudioWarningKey.EXTRACTION_OUTPUT_READ_FAILED
        AnkiSentenceAudioFailure.EXTRACTION_STREAM_MAPPING_FAILED -> PlayerSentenceAudioWarningKey.EXTRACTION_STREAM_MAPPING_FAILED
        AnkiSentenceAudioFailure.EXTRACTION_SOURCE_READ_FAILED -> when (diagnostic?.inputSource) {
            AnkiSentenceAudioInputSource.ORIGINAL_VIDEO -> PlayerSentenceAudioWarningKey.EXTRACTION_SOURCE_READ_FAILED_ORIGINAL
            AnkiSentenceAudioInputSource.MPV_PLAYABLE_VIDEO -> PlayerSentenceAudioWarningKey.EXTRACTION_SOURCE_READ_FAILED_PLAYABLE
            AnkiSentenceAudioInputSource.MPV_EXTERNAL_AUDIO, null -> PlayerSentenceAudioWarningKey.EXTRACTION_SOURCE_READ_FAILED
        }
        AnkiSentenceAudioFailure.EXTRACTION_SEEK_FAILED -> PlayerSentenceAudioWarningKey.EXTRACTION_SEEK_FAILED
        AnkiSentenceAudioFailure.EXTRACTION_OUTPUT_WRITE_FAILED -> PlayerSentenceAudioWarningKey.EXTRACTION_OUTPUT_WRITE_FAILED
        AnkiSentenceAudioFailure.EXTRACTION_TIMED_OUT -> PlayerSentenceAudioWarningKey.EXTRACTION_TIMED_OUT
        AnkiSentenceAudioFailure.UNKNOWN -> PlayerSentenceAudioWarningKey.GENERATION_FAILED
    }
}

internal fun Context.showPlayerAnkiMediaWarnings(warnings: List<AnkiMediaWarning>) {
    warnings.distinct().forEach { warning ->
        toast(
            when (warning.toPlayerSentenceAudioWarningKey()) {
                PlayerSentenceAudioWarningKey.GENERATION_FAILED -> KMR.strings.anki_sentence_audio_generation_failed
                PlayerSentenceAudioWarningKey.TRACK_MAPPING_UNAVAILABLE -> KMR.strings.anki_sentence_audio_track_mapping_unavailable
                PlayerSentenceAudioWarningKey.SOURCE_UNAVAILABLE -> KMR.strings.anki_sentence_audio_source_unavailable
                PlayerSentenceAudioWarningKey.TIMING_UNAVAILABLE -> KMR.strings.anki_sentence_audio_timing_unavailable
                PlayerSentenceAudioWarningKey.PROBE_FAILED -> KMR.strings.anki_sentence_audio_probe_failed
                PlayerSentenceAudioWarningKey.PROBE_FAILED_ORIGINAL -> KMR.strings.anki_sentence_audio_probe_failed_original
                PlayerSentenceAudioWarningKey.PROBE_FAILED_PLAYABLE -> KMR.strings.anki_sentence_audio_probe_failed_playable
                PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND -> KMR.strings.anki_sentence_audio_streams_not_found
                PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_ORIGINAL -> KMR.strings.anki_sentence_audio_streams_not_found_original
                PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_MISSING -> KMR.strings.anki_sentence_audio_streams_not_found_original_playable_missing
                PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_SAME -> KMR.strings.anki_sentence_audio_streams_not_found_original_playable_same
                PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_ORIGINAL_PLAYABLE_UNAVAILABLE -> KMR.strings.anki_sentence_audio_streams_not_found_original_playable_unavailable
                PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_PLAYABLE -> KMR.strings.anki_sentence_audio_streams_not_found_playable
                PlayerSentenceAudioWarningKey.STREAMS_NOT_FOUND_EXTERNAL -> KMR.strings.anki_sentence_audio_streams_not_found_external
                PlayerSentenceAudioWarningKey.CODEC_RESTRICTED -> KMR.strings.anki_sentence_audio_codec_restricted
                PlayerSentenceAudioWarningKey.CODEC_RESTRICTED_ORIGINAL -> KMR.strings.anki_sentence_audio_codec_restricted_original
                PlayerSentenceAudioWarningKey.CODEC_RESTRICTED_PLAYABLE -> KMR.strings.anki_sentence_audio_codec_restricted_playable
                PlayerSentenceAudioWarningKey.CODEC_RESTRICTED_EXTERNAL -> KMR.strings.anki_sentence_audio_codec_restricted_external
                PlayerSentenceAudioWarningKey.STREAM_INDEX_UNAVAILABLE -> KMR.strings.anki_sentence_audio_stream_index_unavailable
                PlayerSentenceAudioWarningKey.STREAM_NOT_AUDIO -> KMR.strings.anki_sentence_audio_stream_not_audio
                PlayerSentenceAudioWarningKey.STREAM_PROTECTED -> KMR.strings.anki_sentence_audio_stream_protected
                PlayerSentenceAudioWarningKey.STREAM_UNREADABLE -> KMR.strings.anki_sentence_audio_stream_unreadable
                PlayerSentenceAudioWarningKey.EXTRACTION_FAILED -> KMR.strings.anki_sentence_audio_extraction_failed
                PlayerSentenceAudioWarningKey.EXTRACTION_OUTPUT_MISSING -> KMR.strings.anki_sentence_audio_extraction_output_missing
                PlayerSentenceAudioWarningKey.EXTRACTION_OUTPUT_READ_FAILED -> KMR.strings.anki_sentence_audio_extraction_output_read_failed
                PlayerSentenceAudioWarningKey.EXTRACTION_STREAM_MAPPING_FAILED -> KMR.strings.anki_sentence_audio_extraction_stream_mapping_failed
                PlayerSentenceAudioWarningKey.EXTRACTION_SOURCE_READ_FAILED -> KMR.strings.anki_sentence_audio_extraction_source_read_failed
                PlayerSentenceAudioWarningKey.EXTRACTION_SOURCE_READ_FAILED_ORIGINAL -> KMR.strings.anki_sentence_audio_extraction_source_read_failed_original
                PlayerSentenceAudioWarningKey.EXTRACTION_SOURCE_READ_FAILED_PLAYABLE -> KMR.strings.anki_sentence_audio_extraction_source_read_failed_playable
                PlayerSentenceAudioWarningKey.EXTRACTION_SEEK_FAILED -> KMR.strings.anki_sentence_audio_extraction_seek_failed
                PlayerSentenceAudioWarningKey.EXTRACTION_OUTPUT_WRITE_FAILED -> KMR.strings.anki_sentence_audio_extraction_output_write_failed
                PlayerSentenceAudioWarningKey.EXTRACTION_TIMED_OUT -> KMR.strings.anki_sentence_audio_extraction_timed_out
                PlayerSentenceAudioWarningKey.STORAGE_FAILED -> KMR.strings.anki_sentence_audio_storage_failed
            },
        )
    }
}
