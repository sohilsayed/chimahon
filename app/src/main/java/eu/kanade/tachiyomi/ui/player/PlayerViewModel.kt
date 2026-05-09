package eu.kanade.tachiyomi.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chimahon.DictionaryRepository
import eu.kanade.tachiyomi.ui.player.mpv.MPVView
import eu.kanade.tachiyomi.ui.player.setting.PlayerPreferences
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class PlayerViewModel @JvmOverloads constructor(
    private val savedState: SavedStateHandle,
    private val getAnime: GetAnime = Injekt.get(),
    private val getEpisode: GetEpisode = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val playerPreferences: PlayerPreferences = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val episodeRepository: EpisodeRepository = Injekt.get(),
    private val dictionaryRepository: DictionaryRepository = Injekt.get(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        State(doubleTapSeekSec = playerPreferences.doubleTapSeekLength().get()),
    )
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    private val progressSaveFlow = MutableSharedFlow<Pair<Long, Long>>(extraBufferCapacity = 1)

    private var animeId: Long = savedState[KEY_ANIME_ID] ?: -1L
    private var episodeId: Long = savedState[KEY_EPISODE_ID] ?: -1L

    var lookupDeferred: Deferred<DictionaryRepository.LookupResult2>? = null
        private set

    init {
        progressSaveFlow
            .sample(playerPreferences.progressSaveIntervalSec().get().seconds)
            .onEach { (pos, dur) -> persistProgress(pos, dur) }
            .launchIn(viewModelScope)
    }

    suspend fun init(animeId: Long, episodeId: Long, videoUrl: String?) {
        if (this.animeId > 0 && this.episodeId > 0 && mutableState.value.episode != null) return
        try {
            if (animeId > 0 && episodeId > 0) {
                this.animeId = animeId
                this.episodeId = episodeId
                savedState[KEY_ANIME_ID] = animeId
                savedState[KEY_EPISODE_ID] = episodeId
                loadFromDb()
            } else if (!videoUrl.isNullOrBlank()) {
                createOrGetAnimeForUrl(videoUrl)
                loadFromDb()
            } else {
                eventChannel.send(Event.Error("No video URL or episode ID provided"))
                return
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize player" }
            eventChannel.send(Event.Error(e.message ?: "Failed to initialize player"))
        }
    }

    private suspend fun loadFromDb() {
        try {
            val (anime, episode) = coroutineScope {
                val animeDeferred = async { withIOContext { getAnime.await(animeId) } }
                val episodeDeferred = async { withIOContext { getEpisode.await(episodeId) } }
                animeDeferred.await() to episodeDeferred.await()
            }
            if (anime == null || episode == null) {
                eventChannel.send(Event.Error("Anime or episode not found"))
                return
            }
            mutableState.update { it.copy(anime = anime, episode = episode, isLoading = false) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load anime/episode" }
            eventChannel.send(Event.Error(e.message ?: "Unknown error"))
        }
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        mutableState.update { it.copy(isPlaying = isPlaying) }
    }

    fun updatePosition(positionSec: Long) {
        if (positionSec == mutableState.value.currentPositionSec) return
        val duration = mutableState.value.durationSec
        mutableState.update { it.copy(currentPositionSec = positionSec) }
        progressSaveFlow.tryEmit(positionSec to duration)
    }

    fun updateDuration(durationSec: Long) {
        mutableState.update { it.copy(durationSec = durationSec) }
    }

    fun updateSubText(text: String) {
        if (text == mutableState.value.currentSubText) return
        mutableState.update { it.copy(currentSubText = text) }
    }

    fun updateTracks(subs: List<MPVView.Track>, audio: List<MPVView.Track>, selectedSub: Int, selectedAudio: Int) {
        mutableState.update {
            it.copy(
                subtitleTracks = subs,
                audioTracks = audio,
                selectedSubId = selectedSub,
                selectedAudioId = selectedAudio,
            )
        }
    }

    fun updateSelectedSubId(id: Int) {
        mutableState.update { it.copy(selectedSubId = id) }
    }

    fun updateSelectedAudioId(id: Int) {
        mutableState.update { it.copy(selectedAudioId = id) }
    }

    fun startLookup(
        word: String,
        fullText: String,
        charOffset: Int,
        anchorX: Float,
        anchorY: Float,
        termPaths: List<String>,
    ) {
        lookupDeferred?.cancel()
        mutableState.update { it.copy(highlightRange = null) }

        lookupDeferred = viewModelScope.async(Dispatchers.IO) {
            dictionaryRepository.lookup(word, termPaths)
        }

        mutableState.update {
            it.copy(
                lookupState = SubtitleLookupState(word, fullText, charOffset, anchorX, anchorY),
            )
        }
        viewModelScope.launchIO { eventChannel.send(Event.PauseForLookup) }
    }

    fun dismissLookup() {
        mutableState.update { it.copy(lookupState = null, highlightRange = null) }
        lookupDeferred?.cancel()
        lookupDeferred = null
        viewModelScope.launchIO { eventChannel.send(Event.ResumeFromLookup) }
    }

    fun updateHighlightRange(range: IntRange) {
        mutableState.update { it.copy(highlightRange = range) }
    }

    fun onPlaybackCompleted() {
        val state = mutableState.value
        val episode = state.episode ?: return
        val duration = state.durationSec
        val position = state.currentPositionSec
        viewModelScope.launchIO {
            val seen = duration > 0 && position >= duration * 0.9
            updateEpisode.await(
                EpisodeUpdate(
                    id = episode.id,
                    seen = if (seen) true else null,
                    lastSecondSeen = position,
                    totalSeconds = duration,
                ),
            )
            eventChannel.send(Event.PlaybackCompleted)
        }
    }

    fun saveProgress() {
        val state = mutableState.value
        if (state.currentPositionSec > 0 && state.episode != null) {
            viewModelScope.launchNonCancellable {
                persistProgress(state.currentPositionSec, state.durationSec)
            }
        }
    }

    private suspend fun persistProgress(positionSec: Long, durationSec: Long) {
        val ep = mutableState.value.episode ?: return
        try {
            withIOContext {
                updateEpisode.await(
                    EpisodeUpdate(
                        id = ep.id,
                        lastSecondSeen = positionSec,
                        totalSeconds = durationSec,
                    ),
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to save playback progress" }
        }
    }

    private suspend fun createOrGetAnimeForUrl(videoUrl: String) {
        withIOContext {
            val existingAnime = animeRepository.getAnimeByUrlAndSourceId(videoUrl, LOCAL_ANIME_SOURCE_ID)
            if (existingAnime != null) {
                animeId = existingAnime.id
                val episodes = getEpisodesByAnimeId.await(existingAnime.id)
                val episode = episodes.firstOrNull { it.url == videoUrl } ?: episodes.firstOrNull()
                if (episode != null) {
                    episodeId = episode.id
                    savedState[KEY_ANIME_ID] = animeId
                    savedState[KEY_EPISODE_ID] = episodeId
                    return@withIOContext
                }
                val newEpisode = insertEpisode(existingAnime.id, videoUrl, existingAnime.title)
                    ?: error("Failed to create episode")
                episodeId = newEpisode.id
                savedState[KEY_ANIME_ID] = animeId
                savedState[KEY_EPISODE_ID] = episodeId
                return@withIOContext
            }

            val title = videoUrl.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Video" }
            val anime = Anime.create().copy(
                url = videoUrl,
                title = title,
                source = LOCAL_ANIME_SOURCE_ID,
                favorite = false,
            )
            animeId = animeRepository.insert(anime)

            val newEpisode = insertEpisode(animeId, videoUrl, title)
                ?: error("Failed to create episode")
            episodeId = newEpisode.id

            savedState[KEY_ANIME_ID] = animeId
            savedState[KEY_EPISODE_ID] = episodeId
        }
    }

    private suspend fun insertEpisode(animeId: Long, url: String, name: String): Episode? {
        val episode = Episode.create().copy(
            animeId = animeId,
            url = url,
            name = name,
            episodeNumber = 1.0,
        )
        return episodeRepository.addAll(listOf(episode)).firstOrNull()
    }

    @Immutable
    data class SubtitleLookupState(
        val word: String,
        val sentence: String,
        val charOffset: Int,
        val anchorX: Float,
        val anchorY: Float,
    )

    @Immutable
    data class State(
        val anime: Anime? = null,
        val episode: Episode? = null,
        val isLoading: Boolean = true,
        val isPlaying: Boolean = false,
        val currentPositionSec: Long = 0,
        val durationSec: Long = 0,
        val doubleTapSeekSec: Int = 10,
        val currentSubText: String = "",
        val subtitleTracks: List<MPVView.Track> = emptyList(),
        val audioTracks: List<MPVView.Track> = emptyList(),
        val selectedSubId: Int = -1,
        val selectedAudioId: Int = -1,
        val lookupState: SubtitleLookupState? = null,
        val highlightRange: IntRange? = null,
    )

    sealed interface Event {
        data class Error(val message: String) : Event
        data object PlaybackCompleted : Event
        data object PauseForLookup : Event
        data object ResumeFromLookup : Event
    }

    companion object {
        const val KEY_ANIME_ID = "anime_id"
        const val KEY_EPISODE_ID = "episode_id"
        const val LOCAL_ANIME_SOURCE_ID = 0L
    }
}
