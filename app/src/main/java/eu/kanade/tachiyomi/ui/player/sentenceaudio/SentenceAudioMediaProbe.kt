package eu.kanade.tachiyomi.ui.player.sentenceaudio

import java.util.Locale

/** Parses the small, explicitly requested audio subset of ffprobe's key/value output. */
internal object SentenceAudioMediaProbe {
    fun inspectSelectedAudio(output: String): AudioInspection {
        val values = output.keyValues()
        if (values.isEmpty()) return AudioInspection.StreamMissing
        if (output.hasProtectionMarker()) return AudioInspection.Protected
        return if (values["codec_type"] == "audio") AudioInspection.Readable else AudioInspection.NotAudio
    }

    fun audioStreams(output: String): List<AudioStream> = output.streamBlocks().mapNotNull { block ->
        val values = block.keyValues()
        if (values["codec_type"] != "audio") return@mapNotNull null
        AudioStream(index = values["index"]?.toIntOrNull(), protected = block.hasProtectionMarker())
    }

    sealed interface AudioInspection {
        data object Readable : AudioInspection
        data object StreamMissing : AudioInspection
        data object NotAudio : AudioInspection
        data object Protected : AudioInspection
    }

    data class AudioStream(val index: Int?, val protected: Boolean)

    private fun String.keyValues(): Map<String, String> = lineSequence().mapNotNull { line ->
        val separator = line.indexOf('=')
        if (separator <= 0) null else {
            line.substring(0, separator).trim().lowercase(Locale.ROOT) to
                line.substring(separator + 1).trim().lowercase(Locale.ROOT)
        }
    }.toMap()

    private fun String.streamBlocks(): List<String> {
        val blocks = mutableListOf<String>()
        var current: StringBuilder? = null
        lineSequence().forEach { line ->
            when (line.trim()) {
                "[STREAM]" -> current = StringBuilder()
                "[/STREAM]" -> current?.let { blocks += it.toString(); current = null }
                else -> current?.append(line)?.append('\n')
            }
        }
        return blocks.ifEmpty { listOf(this) }
    }

    private fun String.hasProtectionMarker(): Boolean {
        val normalized = lowercase(Locale.ROOT)
        return protectionMarkers.any(normalized::contains)
    }

    private val protectionMarkers = setOf("cenc", "cbcs", "crypto", "encrypted", "encryption", "drm")
}
