package eu.kanade.tachiyomi.ui.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.AnimeUpdate
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.animesource.service.AnimeSourceManager
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.SetSeenStatus
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.manga.model.applyFilter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeScreenModel(
    private val animeId: Long,
    private val getAnime: GetAnime = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val episodeRepository: EpisodeRepository = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
) : StateScreenModel<AnimeScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            fetchEpisodesFromSourceIfNeeded()
        }

        screenModelScope.launchIO {
            combine(
                getAnime.subscribe(animeId),
                getEpisodesByAnimeId.subscribe(animeId),
                animeDownloadManager.queueState,
                animeDownloadManager.stateVersion,
            ) { anime, episodes, downloadQueue, _ ->
                val sortedEpisodes = applySort(anime, episodes)
                val filteredEpisodes = applyFilters(anime, sortedEpisodes)
                val queueById = downloadQueue.associateBy { it.episode.id }
                val episodeDownloadState = buildMap {
                    for (ep in episodes) {
                        val dl = queueById[ep.id]
                        if (dl != null) {
                            put(ep.id, EpisodeDownloadState(dl.status, dl.progress))
                        } else if (animeDownloadManager.isEpisodeDownloaded(ep.name, ep.scanlator, anime.title, anime.source)) {
                            put(ep.id, EpisodeDownloadState(AnimeDownload.State.DOWNLOADED, 100))
                        }
                    }
                }
                State.Success(
                    anime = anime,
                    episodes = filteredEpisodes,
                    allEpisodeCount = episodes.size,
                    nextUnseenEpisode = episodes.asSequence().filter { !it.seen }.minByOrNull { it.sourceOrder },
                    episodeDownloadState = episodeDownloadState,
                )
            }.collectLatest { newState ->
                mutableState.update { old ->
                    val dialog = (old as? State.Success)?.dialog
                    newState.copy(dialog = dialog)
                }
            }
        }
    }

    private suspend fun fetchEpisodesFromSourceIfNeeded() {
        val dbEpisodes = getEpisodesByAnimeId.await(animeId)
        if (dbEpisodes.isNotEmpty()) return
        syncEpisodesFromSource()
    }

    private suspend fun syncEpisodesFromSource() {
        val anime = getAnime.await(animeId) ?: return
        val source = animeSourceManager.get(anime.source) ?: return

        try {
            val sAnime = SAnime.create().apply {
                url = anime.url
                title = anime.title
            }
            val sourceEpisodes = source.getEpisodeList(sAnime)
            val existingEpisodes = getEpisodesByAnimeId.await(animeId)
            val existingUrls = existingEpisodes.map { it.url }.toSet()

            val newEpisodes = sourceEpisodes
                .filter { it.url !in existingUrls }
                .mapIndexed { index, sEpisode ->
                    episodeFromSource(animeId, sEpisode, (existingEpisodes.size + index).toLong())
                }
            if (newEpisodes.isNotEmpty()) {
                episodeRepository.addAll(newEpisodes)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch episodes from source" }
        }
    }

    private fun episodeFromSource(animeId: Long, sEpisode: SEpisode, sourceOrder: Long): Episode {
        return Episode.create().copy(
            animeId = animeId,
            url = sEpisode.url,
            name = sEpisode.name,
            dateUpload = sEpisode.date_upload,
            episodeNumber = sEpisode.episode_number.toDouble(),
            scanlator = sEpisode.scanlator,
            sourceOrder = sourceOrder,
            dateFetch = System.currentTimeMillis(),
        )
    }

    private fun applySort(anime: Anime, episodes: List<Episode>): List<Episode> {
        val comparator: Comparator<Episode> = when (anime.sorting) {
            Anime.EPISODE_SORTING_NUMBER -> compareBy { it.episodeNumber }
            Anime.EPISODE_SORTING_UPLOAD_DATE -> compareBy { it.dateUpload }
            Anime.EPISODE_SORTING_ALPHABET -> compareBy { it.name }
            else -> compareBy { it.sourceOrder }
        }
        return if (anime.sortDescending()) {
            episodes.sortedWith(comparator.reversed())
        } else {
            episodes.sortedWith(comparator)
        }
    }

    private fun applyFilters(anime: Anime, episodes: List<Episode>): List<Episode> {
        return episodes.filter { episode ->
            applyFilter(anime.unseenFilter) { !episode.seen } &&
                applyFilter(anime.bookmarkedFilter) { episode.bookmark }
        }
    }

    fun refreshEpisodes() {
        screenModelScope.launchIO {
            syncEpisodesFromSource()
        }
    }

    fun toggleFavorite() {
        screenModelScope.launchNonCancellable {
            val anime = (state.value as? State.Success)?.anime ?: return@launchNonCancellable
            updateAnime.await(
                AnimeUpdate(
                    id = anime.id,
                    favorite = !anime.favorite,
                    dateAdded = if (!anime.favorite) System.currentTimeMillis() else 0L,
                ),
            )
        }
    }

    fun markEpisodesSeen(episodes: List<Episode>, seen: Boolean) {
        screenModelScope.launchNonCancellable {
            setSeenStatus.await(
                seen = seen,
                episodes = episodes.toTypedArray(),
            )
        }
    }

    fun toggleBookmark(episodes: List<Episode>) {
        screenModelScope.launchNonCancellable {
            val updates = episodes.map {
                EpisodeUpdate(id = it.id, bookmark = !it.bookmark)
            }
            updateEpisode.awaitAll(updates)
        }
    }

    fun deleteAnime() {
        screenModelScope.launchNonCancellable {
            animeRepository.deleteAnime(animeId)
        }
    }

    fun setSortMode(mode: Long) {
        screenModelScope.launchNonCancellable {
            val anime = (state.value as? State.Success)?.anime ?: return@launchNonCancellable
            setAnimeEpisodeFlags.awaitSetSortingModeOrFlipOrder(anime, mode)
        }
    }

    fun setUnseenFilter(flag: Long) {
        screenModelScope.launchNonCancellable {
            val anime = (state.value as? State.Success)?.anime ?: return@launchNonCancellable
            setAnimeEpisodeFlags.awaitSetUnseenFilter(anime, flag)
        }
    }

    fun setBookmarkFilter(flag: Long) {
        screenModelScope.launchNonCancellable {
            val anime = (state.value as? State.Success)?.anime ?: return@launchNonCancellable
            setAnimeEpisodeFlags.awaitSetBookmarkFilter(anime, flag)
        }
    }

    fun startDownload(episode: Episode) {
        screenModelScope.launchIO {
            val successState = state.value as? State.Success ?: return@launchIO
            val anime = successState.anime
            val source = animeSourceManager.get(anime.source) ?: return@launchIO

            val httpSource = source as? AnimeHttpSource
            if (httpSource == null) {
                logcat(LogPriority.ERROR) { "Source is not AnimeHttpSource, cannot download" }
                return@launchIO
            }

            mutableState.update { s ->
                if (s is State.Success) s.copy(dialog = Dialog.DownloadLoading(episode)) else s
            }

            try {
                val videos = resolveVideosForEpisode(httpSource, episode)
                if (videos.isEmpty()) {
                    mutableState.update { s ->
                        if (s is State.Success) s.copy(dialog = null) else s
                    }
                    return@launchIO
                }
                mutableState.update { s ->
                    if (s is State.Success) s.copy(dialog = Dialog.QualitySelection(episode, videos)) else s
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to resolve videos for download" }
                mutableState.update { s ->
                    if (s is State.Success) s.copy(dialog = null) else s
                }
            }
        }
    }

    fun confirmDownload(episode: Episode, video: Video) {
        logcat(LogPriority.INFO) { "AnimeScreenModel: confirmDownload for ${episode.name}, video=${video.videoTitle}" }
        screenModelScope.launchNonCancellable {
            val successState = state.value as? State.Success ?: return@launchNonCancellable
            val anime = successState.anime
            logcat(LogPriority.INFO) { "AnimeScreenModel: calling downloadEpisodes for anime=${anime.title}" }
            animeDownloadManager.downloadEpisodes(anime, listOf(episode), listOf(video))
        }
        dismissDialog()
    }

    fun deleteEpisodeDownload(episode: Episode) {
        screenModelScope.launchNonCancellable {
            val successState = state.value as? State.Success ?: return@launchNonCancellable
            val anime = successState.anime
            val source = animeSourceManager.get(anime.source) ?: return@launchNonCancellable
            animeDownloadManager.deleteEpisodes(listOf(episode), anime, source)
        }
    }

    private suspend fun resolveVideosForEpisode(source: AnimeHttpSource, episode: Episode): List<Video> {
        return withIOContext {
            val sEpisode = SEpisode.create().apply {
                url = episode.url
                name = episode.name
            }

            val videos = mutableListOf<Video>()

            try {
                videos.addAll(source.getVideoList(sEpisode))
            } catch (_: Throwable) {}

            if (videos.isEmpty()) {
                try {
                    val hosters = source.getHosterList(sEpisode)
                    for (hoster in hosters) {
                        val hosterVideos = hoster.videoList ?: try {
                            source.getVideoList(hoster)
                        } catch (_: Throwable) {
                            emptyList()
                        }
                        videos.addAll(hosterVideos)
                    }
                } catch (_: Throwable) {}
            }

            // Resolve URLs
            videos.mapNotNull { video ->
                var resolved = video
                try {
                    resolved = source.resolveVideo(video) ?: return@mapNotNull null
                } catch (_: Throwable) {}

                if (resolved.videoUrl.isBlank() && resolved.videoPageUrl.isNotBlank()) {
                    val url = try { source.getVideoUrl(resolved) } catch (_: Throwable) { null }
                    if (!url.isNullOrBlank()) {
                        resolved = resolved.copy(videoUrl = url)
                    }
                }

                val headers = resolved.headers ?: source.headers
                resolved = resolved.copy(headers = headers)

                if (resolved.videoUrl.isNotBlank()) resolved else null
            }
        }
    }

    fun showDialog(dialog: Dialog) {
        mutableState.update { state ->
            when (state) {
                is State.Success -> state.copy(dialog = dialog)
                else -> state
            }
        }
    }

    fun dismissDialog() {
        mutableState.update { state ->
            when (state) {
                is State.Success -> state.copy(dialog = null)
                else -> state
            }
        }
    }

    @Immutable
    data class EpisodeDownloadState(
        val status: AnimeDownload.State,
        val progress: Int,
    )

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val anime: Anime,
            val episodes: List<Episode>,
            val allEpisodeCount: Int = 0,
            val nextUnseenEpisode: Episode? = null,
            val episodeDownloadState: Map<Long, EpisodeDownloadState> = emptyMap(),
            val dialog: Dialog? = null,
        ) : State
    }

    sealed interface Dialog {
        data object ConfirmDelete : Dialog
        data class DownloadLoading(val episode: Episode) : Dialog
        data class QualitySelection(val episode: Episode, val videos: List<Video>) : Dialog
    }
}
