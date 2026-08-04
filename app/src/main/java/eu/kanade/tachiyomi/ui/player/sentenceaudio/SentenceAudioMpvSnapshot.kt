package eu.kanade.tachiyomi.ui.player.sentenceaudio

internal interface SentenceAudioMpvPropertyReader {
    fun string(name: String): String?
    fun int(name: String): Int?
    fun boolean(name: String): Boolean?
}

internal data class SentenceAudioMpvSnapshot(
    val playableValue: String?,
    val selectedAudioId: Int?,
    val selectedExternalAudioValue: String?,
    val selectedAudioIsExternal: Boolean,
    val audioTrackCount: Int,
    val selectedAudioFfmpegIndex: Int?,
    val seekable: Boolean?,
)

internal class SentenceAudioMpvSnapshotReader(private val reader: SentenceAudioMpvPropertyReader) {
    fun read(): SentenceAudioMpvSnapshot {
        val selectedId = reader.string("aid")?.toIntOrNull() ?: reader.int("aid")
        val count = reader.int("track-list/count") ?: 0
        val tracks = (0 until count).map { index ->
            Track(index, reader.string("track-list/$index/type"), reader.int("track-list/$index/id"))
        }
        val selected = tracks.firstOrNull { it.type == "audio" && it.id == selectedId }
        val selectedIndex = selected?.index
        val externalFilename = selectedIndex?.let { reader.string("track-list/$it/external-filename") }?.takeIf(String::isNotBlank)
        val ffIndex = selectedIndex?.let { reader.int("track-list/$it/ff-index") }?.takeIf { it >= 0 }
        return SentenceAudioMpvSnapshot(
            playableValue = reader.string("path"),
            selectedAudioId = selectedId,
            selectedExternalAudioValue = externalFilename,
            selectedAudioIsExternal = selectedIndex?.let { reader.boolean("track-list/$it/external") == true } == true || externalFilename != null,
            audioTrackCount = tracks.count { it.type == "audio" },
            selectedAudioFfmpegIndex = ffIndex,
            seekable = reader.boolean("seekable"),
        )
    }
    private data class Track(val index: Int, val type: String?, val id: Int?)
}
