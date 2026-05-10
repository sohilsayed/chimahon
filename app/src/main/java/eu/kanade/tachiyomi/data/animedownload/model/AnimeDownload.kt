package eu.kanade.tachiyomi.data.animedownload.model

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode

data class AnimeDownload(
    val source: AnimeHttpSource,
    val anime: Anime,
    val episode: Episode,
    @Volatile var video: Video? = null,
) {
    @Volatile
    var totalBytes: Long = -1L

    @Volatile
    var downloadedBytes: Long = 0L

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    private val _progressFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = _progressFlow.asStateFlow()
    var progress: Int
        get() = _progressFlow.value
        set(value) {
            _progressFlow.value = value
        }

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

}
