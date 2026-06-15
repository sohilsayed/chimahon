package eu.kanade.tachiyomi.ui.player

import android.app.Application
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import chimahon.DictionaryRepository
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.data.torrentServer.service.TorrentServerService
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.source.isSourceForTorrents
import eu.kanade.tachiyomi.torrentServer.TorrentServerUtils
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.mpv.MPVView
import eu.kanade.tachiyomi.ui.player.setting.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.utils.AniSkipApi
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.animesource.service.AnimeSourceManager
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.history.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.model.AnimeHistoryUpdate
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
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
    private val application: Application = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val upsertAnimeHistory: UpsertAnimeHistory = Injekt.get(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(
        State(
            currentSpeed = playerPreferences.playerSpeed().get(),
            aspectRatio = playerPreferences.aspectState().get(),
        ),
    )
    val state = mutableState.asStateFlow()

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    private val progressSaveFlow = MutableSharedFlow<Pair<Long, Long>>(extraBufferCapacity = 1)

    private var animeId: Long = savedState[KEY_ANIME_ID] ?: -1L
    private var episodeId: Long = savedState[KEY_EPISODE_ID] ?: -1L

    var lookupDeferred: Deferred<DictionaryRepository.LookupResult2>? = null
        private set

    private var aniSkipStamps: List<TimeStamp> = emptyList()
    private var mpvChapters: List<IndexedSegment> = emptyList()
    private var timerJob: Job? = null

    private val introSkipEnabled = playerPreferences.enableSkipIntro().get()
    private val autoSkip = playerPreferences.autoSkipIntro().get()
    private val netflixStyle = playerPreferences.enableNetflixStyleIntroSkip().get()
    private val defaultWaitingTime = playerPreferences.waitingTimeIntroSkip().get()
    @Volatile
    private var waitingSkipIntro = defaultWaitingTime

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

            val allEpisodes = withIOContext {
                getEpisodesByAnimeId.await(animeId)
            }.sortedBy { it.episodeNumber }

            val currentIndex = allEpisodes.indexOfFirst { it.id == episodeId }

            var resolvedVideo: Video? = null
            if (anime.source != LOCAL_ANIME_SOURCE_ID) {
                val source = animeSourceManager.get(anime.source)
                if (source != null) {
                    resolvedVideo = animeDownloadManager.buildVideoForPlayer(anime, episode, source)
                }
                if (resolvedVideo == null) {
                    resolvedVideo = resolveVideoFromSource(anime.source, episode)
                }
            }

            if (resolvedVideo == null && anime.source != LOCAL_ANIME_SOURCE_ID) {
                eventChannel.send(Event.Error("Could not find a playable video for ${episode.name}"))
                return
            }

            aniSkipStamps = emptyList()
            mpvChapters = emptyList()
            waitingSkipIntro = defaultWaitingTime

            mutableState.update {
                it.copy(
                    anime = anime,
                    episode = episode,
                    resolvedVideo = resolvedVideo,
                    isLoading = false,
                    episodes = allEpisodes,
                    currentEpisodeIndex = currentIndex,
                    chapters = emptyList(),
                    currentChapter = null,
                    skipIntroText = null,
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load anime/episode" }
            eventChannel.send(Event.Error(e.message ?: "Unknown error"))
        }
    }

    private suspend fun resolveVideoFromSource(sourceId: Long, episode: Episode): Video? {
        return withIOContext {
            try {
                val source = animeSourceManager.get(sourceId) ?: return@withIOContext null
                if (source.isSourceForTorrents()) {
                    TorrentServerService.start()
                    if (TorrentServerService.wait(10)) {
                        TorrentServerUtils.setTrackersList()
                    }
                }
                val sEpisode = SEpisode.create().apply {
                    url = episode.url
                    name = episode.name
                    date_upload = episode.dateUpload
                    episode_number = episode.episodeNumber.toFloat()
                    scanlator = episode.scanlator
                }

                val videos = try {
                    source.getVideoList(sEpisode)
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to get video list for ${episode.name}" }
                    emptyList()
                }

                resolveFirstPlayableVideo(source, videos)?.let {
                    return@withIOContext ensureHeaders(it, source)
                }

                try {
                    val hosters = source.getHosterList(sEpisode)
                    for (hoster in hosters) {
                        val hosterVideos = hoster.videoList ?: try {
                            source.getVideoList(hoster)
                        } catch (e: Throwable) {
                            logcat(LogPriority.WARN, e) { "Failed to get videos from hoster" }
                            emptyList()
                        }
                        resolveFirstPlayableVideo(source, hosterVideos)?.let {
                            return@withIOContext ensureHeaders(it, source)
                        }
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e) { "Hoster list resolution failed" }
                }

                logcat(LogPriority.ERROR) { "No playable video found for ${episode.name}" }
                null
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to resolve video URL from source" }
                null
            }
        }
    }

    private fun ensureHeaders(video: Video, source: AnimeSource): Video {
        if (video.headers != null) return video
        val defaultHeaders = (source as? AnimeHttpSource)?.headers ?: return video
        return video.copy(headers = defaultHeaders)
    }

    private suspend fun resolveFirstPlayableVideo(source: AnimeSource, videos: List<Video>): Video? {
        if (videos.isEmpty()) return null
        val httpSource = source as? AnimeHttpSource

        for (video in videos) {
            var resolved = video

            if (httpSource != null) {
                resolved = try {
                    httpSource.resolveVideo(video) ?: continue
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to resolve video: ${video.videoTitle}" }
                    video
                }
            }

            if (resolved.videoUrl.isBlank() && resolved.videoPageUrl.isNotBlank() && httpSource != null) {
                val url = try {
                    httpSource.getVideoUrl(resolved)
                } catch (e: Throwable) {
                    logcat(LogPriority.WARN, e) { "Failed to get video URL: ${resolved.videoTitle}" }
                    null
                }
                if (!url.isNullOrBlank()) {
                    resolved = resolved.copy(videoUrl = url)
                }
            }

            val url = resolved.videoUrl
            if (url.isNotBlank() && isPlayableScheme(url)) {
                return resolved
            }
        }
        return null
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        if (isPlaying == mutableState.value.isPlaying) return
        mutableState.update { it.copy(isPlaying = isPlaying) }
    }

    fun updatePosition(positionSec: Long) {
        if (positionSec == mutableState.value.currentPositionSec) return
        val duration = mutableState.value.durationSec
        mutableState.update { it.copy(currentPositionSec = positionSec) }
        progressSaveFlow.tryEmit(positionSec to duration)
        setChapter(positionSec.toFloat())
    }

    fun updateDuration(durationSec: Long) {
        if (durationSec == mutableState.value.durationSec) return
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

    fun toggleControls() {
        mutableState.update { it.copy(controlsVisible = !it.controlsVisible) }
    }

    fun hideControls() {
        mutableState.update { it.copy(controlsVisible = false) }
    }

    fun toggleLock() {
        mutableState.update { it.copy(isLocked = !it.isLocked) }
    }

    fun setSpeed(speed: Float) {
        mutableState.update { it.copy(currentSpeed = speed) }
        playerPreferences.playerSpeed().set(speed)
    }

    fun cycleAspectRatio() {
        val next = when (mutableState.value.aspectRatio) {
            VideoAspect.Fit -> VideoAspect.Crop
            VideoAspect.Crop -> VideoAspect.Stretch
            VideoAspect.Stretch -> VideoAspect.Fit
        }
        mutableState.update { it.copy(aspectRatio = next) }
        playerPreferences.aspectState().set(next)
    }

    fun updateBrightnessState(brightness: Float, visible: Boolean) {
        mutableState.update { it.copy(currentBrightness = brightness, showBrightnessSlider = visible) }
    }

    fun updateVolumeState(volume: Float, maxVolume: Float, visible: Boolean) {
        mutableState.update { it.copy(currentVolume = volume, maxVolume = maxVolume, showVolumeSlider = visible) }
    }

    fun toggleStats() {
        mutableState.update { it.copy(showStats = !it.showStats) }
    }

    fun updatePipMode(isInPip: Boolean) {
        mutableState.update { it.copy(isInPipMode = isInPip) }
    }

    fun hideBrightnessVolumeSliders() {
        mutableState.update { it.copy(showBrightnessSlider = false, showVolumeSlider = false) }
    }

    fun loadNextEpisode() {
        val state = mutableState.value
        val idx = state.currentEpisodeIndex
        if (idx < 0 || idx >= state.episodes.size - 1) return
        val nextEpisode = state.episodes[idx + 1]
        switchToEpisode(nextEpisode)
    }

    fun loadPreviousEpisode() {
        val state = mutableState.value
        val idx = state.currentEpisodeIndex
        if (idx <= 0) return
        val prevEpisode = state.episodes[idx - 1]
        switchToEpisode(prevEpisode)
    }

    fun loadEpisodeAt(index: Int) {
        val state = mutableState.value
        if (index < 0 || index >= state.episodes.size) return
        switchToEpisode(state.episodes[index])
    }

    private fun switchToEpisode(episode: Episode) {
        saveProgress()
        episodeId = episode.id
        savedState[KEY_EPISODE_ID] = episodeId
        mutableState.update { it.copy(isLoading = true, resolvedVideo = null) }
        viewModelScope.launchIO {
            loadFromDb()
            eventChannel.send(Event.EpisodeChanged(episode.id))
        }
    }

    fun onPlaybackCompleted() {
        val state = mutableState.value
        val episode = state.episode ?: return
        val duration = state.durationSec
        val position = state.currentPositionSec
        viewModelScope.launchIO {
            val threshold = playerPreferences.progressPreference().get()
            val seen = duration > 0 && position >= duration * threshold
            updateEpisode.await(
                EpisodeUpdate(
                    id = episode.id,
                    seen = if (seen) true else null,
                    lastSecondSeen = position,
                    totalSeconds = duration,
                ),
            )
            if (playerPreferences.autoplayEnabled().get() &&
                state.currentEpisodeIndex >= 0 &&
                state.currentEpisodeIndex < state.episodes.size - 1
            ) {
                val nextEpisode = state.episodes[state.currentEpisodeIndex + 1]
                switchToEpisode(nextEpisode)
            } else {
                eventChannel.send(Event.PlaybackCompleted)
            }
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
                coroutineScope {
                    launch {
                        updateEpisode.await(
                            EpisodeUpdate(
                                id = ep.id,
                                lastSecondSeen = positionSec,
                                totalSeconds = durationSec,
                            ),
                        )
                    }
                    launch {
                        upsertAnimeHistory.await(
                            AnimeHistoryUpdate(
                                episodeId = ep.id,
                                watchedAt = Date(),
                                sessionWatchDuration = positionSec,
                            ),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to save playback progress" }
        }
    }

    data class CustomSubtitle(val uri: String, val name: String)

    fun saveCustomSubtitle(uri: String, name: String) {
        val epId = mutableState.value.episode?.id ?: return
        val existing = getCustomSubtitles(epId)
        if (existing.any { it.uri == uri }) return
        val updated = existing + CustomSubtitle(uri, name)
        playerPreferences.customSubtitlesForEpisode(epId).set(
            updated.joinToString("|") { "${it.uri}\t${it.name}" },
        )
    }

    fun getCustomSubtitles(episodeId: Long = mutableState.value.episode?.id ?: -1L): List<CustomSubtitle> {
        if (episodeId <= 0) return emptyList()
        val raw = playerPreferences.customSubtitlesForEpisode(episodeId).get()
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull { entry ->
            val parts = entry.split("\t", limit = 2)
            if (parts.size == 2) CustomSubtitle(parts[0], parts[1]) else null
        }
    }

    // AniSkip

    suspend fun fetchAniSkipTimestamps(playerDuration: Int?) {
        if (!playerPreferences.aniSkipEnabled().get()) return
        val animeId = mutableState.value.anime?.id ?: return
        val trackerManager = Injekt.get<TrackerManager>()
        val episodeNumber = mutableState.value.episode?.episodeNumber?.toInt() ?: return

        val tracks = withIOContext { getTracks.await(animeId) }
        if (tracks.isEmpty()) {
            logcat { "AniSkip: No tracks found for anime $animeId" }
            return
        }

        for (track in tracks) {
            val tracker = trackerManager.get(track.trackerId)
            val malId: Long? = when (tracker) {
                is MyAnimeList -> track.remoteId
                is Anilist -> withIOContext { AniSkipApi().getMalIdFromAL(track.remoteId) }
                else -> null
            }
            val duration = playerDuration ?: continue
            val stamps = malId?.let {
                withIOContext { AniSkipApi().getResult(it.toInt(), episodeNumber, duration.toLong()) }
            }
            if (stamps != null) {
                aniSkipStamps = stamps
                rebuildChapters()
                return
            }
        }
    }

    fun loadChapters() {
        try {
            val count = MPVLib.getPropertyInt("chapter-list/count") ?: 0
            val chapters = mutableListOf<IndexedSegment>()
            for (i in 0 until count) {
                val title = MPVLib.getPropertyString("chapter-list/$i/title") ?: "Chapter ${i + 1}"
                val time = MPVLib.getPropertyInt("chapter-list/$i/time") ?: 0
                chapters.add(IndexedSegment(name = title, start = time.toFloat(), index = i))
            }
            mpvChapters = chapters
            rebuildChapters()
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to load MPV chapters" }
        }
    }

    private fun rebuildChapters() {
        val duration = mutableState.value.durationSec.toInt().takeIf { it > 0 }
        val merged = if (aniSkipStamps.isNotEmpty()) {
            ChapterUtils.mergeChapters(mpvChapters, aniSkipStamps, duration)
        } else {
            mpvChapters
        }
        mutableState.update { it.copy(chapters = merged) }
    }

    private fun setChapter(position: Float) {
        val chapters = mutableState.value.chapters
        if (chapters.isEmpty()) return

        getCurrentChapter(position)?.let { (chapterIndex, chapter) ->
            if (mutableState.value.currentChapter != chapter) {
                mutableState.update { it.copy(currentChapter = chapter) }
            }

            if (!introSkipEnabled) return

            if (chapter.chapterType == ChapterType.Other) {
                mutableState.update { it.copy(skipIntroText = null) }
                waitingSkipIntro = defaultWaitingTime
            } else {
                val nextChapterPos = chapters.getOrNull(chapterIndex + 1)?.start ?: position

                if (netflixStyle) {
                    if (waitingSkipIntro == defaultWaitingTime) {
                        viewModelScope.launchIO {
                            eventChannel.send(Event.ShowToast("Skipping ${chapter.name} in $waitingSkipIntro seconds"))
                        }
                    }
                    showSkipIntroButton(chapter, nextChapterPos, waitingSkipIntro)
                    waitingSkipIntro--
                } else if (autoSkip) {
                    viewModelScope.launchIO {
                        eventChannel.send(Event.SeekTo(nextChapterPos.toInt()))
                        eventChannel.send(Event.ShowToast("${chapter.name} skipped"))
                    }
                } else {
                    mutableState.update { it.copy(skipIntroText = "Skip ${chapter.name}") }
                }
            }
        }
    }

    private fun showSkipIntroButton(chapter: IndexedSegment, nextChapterPos: Float, waitingTime: Int) {
        if (waitingTime > -1) {
            if (waitingTime > 0) {
                mutableState.update { it.copy(skipIntroText = "Don't skip") }
            } else {
                viewModelScope.launchIO {
                    eventChannel.send(Event.SeekTo(nextChapterPos.toInt()))
                    eventChannel.send(Event.ShowToast("${chapter.name} skipped"))
                }
            }
        } else {
            mutableState.update { it.copy(skipIntroText = "Skip ${chapter.name}") }
        }
    }

    fun onSkipIntro() {
        val chapters = mutableState.value.chapters
        getCurrentChapter()?.let { (chapterIndex, chapter) ->
            if (waitingSkipIntro > 0 && netflixStyle) {
                waitingSkipIntro = -1
                return
            }

            val nextChapterPos = chapters.getOrNull(chapterIndex + 1)?.start
                ?: mutableState.value.currentPositionSec.toFloat()

            viewModelScope.launchIO {
                eventChannel.send(Event.SeekTo(nextChapterPos.toInt()))
                eventChannel.send(Event.ShowToast("${chapter.name} skipped"))
            }
        }
    }

    fun selectChapter(index: Int) {
        val chapters = mutableState.value.chapters
        val chapter = chapters.getOrNull(index) ?: return
        viewModelScope.launchIO {
            eventChannel.send(Event.SeekTo(chapter.start.toInt()))
        }
    }

    private fun getCurrentChapter(position: Float? = null): IndexedValue<IndexedSegment>? {
        val chapters = mutableState.value.chapters
        if (chapters.isEmpty()) return null
        val pos = position ?: mutableState.value.currentPositionSec.toFloat()
        return chapters.withIndex().lastOrNull { it.value.start <= pos }
    }


    // Sleep timer

    fun startTimer(seconds: Int) {
        timerJob?.cancel()
        if (seconds < 1) {
            mutableState.update { it.copy(remainingTimerSeconds = null) }
            return
        }
        mutableState.update { it.copy(remainingTimerSeconds = seconds) }
        timerJob = viewModelScope.launch {
            for (time in seconds downTo 0) {
                mutableState.update { it.copy(remainingTimerSeconds = time) }
                if (time == 0) {
                    eventChannel.send(Event.PauseForTimer)
                    mutableState.update { it.copy(remainingTimerSeconds = null) }
                    break
                }
                delay(1000)
            }
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        mutableState.update { it.copy(remainingTimerSeconds = null) }
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

            val title = resolveVideoTitle(videoUrl)
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

    private fun resolveVideoTitle(videoUrl: String): String {
        if (videoUrl.startsWith("content://")) {
            try {
                val uri = videoUrl.toUri()
                application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val name = cursor.getString(0)
                            if (!name.isNullOrBlank()) {
                                return name.substringBeforeLast('.')
                            }
                        }
                    }
            } catch (_: Exception) {}
        }
        return videoUrl.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Video" }
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
        val resolvedVideo: Video? = null,
        val isLoading: Boolean = true,
        val isPlaying: Boolean = false,
        val currentPositionSec: Long = 0,
        val durationSec: Long = 0,
        val currentSubText: String = "",
        val subtitleTracks: List<MPVView.Track> = emptyList(),
        val audioTracks: List<MPVView.Track> = emptyList(),
        val selectedSubId: Int = -1,
        val selectedAudioId: Int = -1,
        val lookupState: SubtitleLookupState? = null,
        val highlightRange: IntRange? = null,
        val isLocked: Boolean = false,
        val currentSpeed: Float = 1f,
        val aspectRatio: VideoAspect = VideoAspect.Fit,
        val showBrightnessSlider: Boolean = false,
        val showVolumeSlider: Boolean = false,
        val currentBrightness: Float = 0f,
        val currentVolume: Float = 0f,
        val maxVolume: Float = 15f,
        val episodes: List<Episode> = emptyList(),
        val currentEpisodeIndex: Int = -1,
        val isInPipMode: Boolean = false,
        val showStats: Boolean = false,
        val controlsVisible: Boolean = true,
        val chapters: List<IndexedSegment> = emptyList(),
        val currentChapter: IndexedSegment? = null,
        val skipIntroText: String? = null,
        val remainingTimerSeconds: Int? = null,
    )

    sealed interface Event {
        data class Error(val message: String) : Event
        data object PlaybackCompleted : Event
        data object PauseForLookup : Event
        data object ResumeFromLookup : Event
        data class EpisodeChanged(val episodeId: Long) : Event
        data class ShowToast(val message: String) : Event
        data class SeekTo(val positionSec: Int) : Event
        data object PauseForTimer : Event
    }

    companion object {
        const val KEY_ANIME_ID = "anime_id"
        const val KEY_EPISODE_ID = "episode_id"
        const val LOCAL_ANIME_SOURCE_ID = 0L
        private val STREAMABLE_SCHEMES = setOf("http", "https", "rtmp", "rtsp", "file", "content", "magnet")

        fun isTorrentUrl(url: String): Boolean {
            val normalized = url.trim().lowercase()
            return normalized.startsWith("magnet:") || normalized.endsWith(".torrent")
        }

        fun isPlayableScheme(url: String): Boolean {
            val scheme = url.substringBefore(":", missingDelimiterValue = "").lowercase()
            return scheme in STREAMABLE_SCHEMES
        }
    }
}
