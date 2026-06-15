package eu.kanade.tachiyomi.ui.anime

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.animedownload.AnimeDownloadManager
import eu.kanade.tachiyomi.data.animedownload.model.AnimeDownload
import eu.kanade.tachiyomi.data.torrentServer.service.TorrentServerService
import eu.kanade.tachiyomi.source.isSourceForTorrents
import eu.kanade.tachiyomi.torrentServer.TorrentServerUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetDuplicateLibraryAnime
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
import tachiyomi.core.common.preference.TriState
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
    private val getDuplicateLibraryAnime: GetDuplicateLibraryAnime = Injekt.get(),
) : StateScreenModel<AnimeScreenModel.State>(State.Loading) {

    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    private val selectedEpisodeIds: HashSet<Long> = HashSet()

    init {
        screenModelScope.launchIO {
            fetchAnimeFromSourceIfNeeded()
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

                val episodeItems = filteredEpisodes.map { episode ->
                    val dl = queueById[episode.id]
                    val downloadState = when {
                        dl != null -> dl.status
                        animeDownloadManager.isEpisodeDownloaded(
                            episode.name,
                            episode.scanlator,
                            anime.title,
                            anime.source,
                        ) -> AnimeDownload.State.DOWNLOADED
                        else -> AnimeDownload.State.NOT_DOWNLOADED
                    }
                    EpisodeList.Item(
                        episode = episode,
                        downloadState = downloadState,
                        downloadProgress = dl?.progress ?: 0,
                        selected = episode.id in selectedEpisodeIds,
                    )
                }

                val allEpisodes = episodes
                val nextUnseen = allEpisodes.asSequence()
                    .filter { !it.seen }
                    .minByOrNull { it.sourceOrder }

                State.Success(
                    anime = anime,
                    episodes = episodeItems,
                    allEpisodeCount = allEpisodes.size,
                    nextUnseenEpisode = nextUnseen,
                )
            }.collectLatest { newState ->
                mutableState.update { old ->
                    val dialog = (old as? State.Success)?.dialog
                    val refreshing = (old as? State.Success)?.isRefreshingData ?: false
                    newState.copy(dialog = dialog, isRefreshingData = refreshing)
                }
            }
        }
    }

    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    // region Selection

    fun toggleSelection(
        item: EpisodeList.Item,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.toMutableList().apply {
                val selectedIndex = indexOfFirst { it.id == item.episode.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if (selectedItem.selected == selected) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedEpisodeIds.addOrRemove(item.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inBetweenItem = get(it)
                            if (!inBetweenItem.selected) {
                                selectedEpisodeIds.add(inBetweenItem.id)
                                set(it, inBetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            successState.copy(episodes = newEpisodes)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    // endregion

    // region Source sync

    private suspend fun fetchAnimeFromSourceIfNeeded() {
        val anime = getAnime.await(animeId) ?: return
        if (anime.initialized && !anime.description.isNullOrBlank()) return
        fetchAnimeFromSource()
    }

    private suspend fun fetchAnimeFromSource() {
        val anime = getAnime.await(animeId) ?: return
        val source = animeSourceManager.get(anime.source) ?: return
        prepareTorrentSourceIfNeeded(source)

        try {
            val sAnime = SAnime.create().apply {
                url = anime.url
                title = anime.title
            }
            val networkAnime = source.getAnimeDetails(sAnime)
            updateAnime.await(
                AnimeUpdate(
                    id = anime.id,
                    title = networkAnime.title.takeIf { it.isNotBlank() } ?: anime.title,
                    artist = networkAnime.artist,
                    author = networkAnime.author,
                    description = networkAnime.description,
                    genre = networkAnime.getGenres(),
                    status = networkAnime.status.toLong(),
                    thumbnailUrl = networkAnime.thumbnail_url,
                    initialized = true,
                ),
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch anime details from source" }
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
        prepareTorrentSourceIfNeeded(source)

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

    // endregion

    // region Sorting & Filtering

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

    // endregion

    // region Actions

    fun refreshEpisodes() {
        screenModelScope.launchIO {
            updateSuccessState { it.copy(isRefreshingData = true) }
            fetchAnimeFromSource()
            syncEpisodesFromSource()
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    fun toggleFavorite(checkDuplicate: Boolean = true) {
        screenModelScope.launchNonCancellable {
            val anime = (state.value as? State.Success)?.anime ?: return@launchNonCancellable

            if (!anime.favorite && checkDuplicate) {
                val duplicates = getDuplicateLibraryAnime(anime)
                if (duplicates.isNotEmpty()) {
                    updateSuccessState { it.copy(dialog = Dialog.DuplicateAnime(duplicates)) }
                    return@launchNonCancellable
                }
            }

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

    fun markPreviousAsSeen(episode: Episode) {
        screenModelScope.launchNonCancellable {
            val successState = state.value as? State.Success ?: return@launchNonCancellable
            val allEpisodes = successState.episodes.map { it.episode }
            val index = allEpisodes.indexOfFirst { it.id == episode.id }
            if (index < 0) return@launchNonCancellable

            val previousEpisodes = if (successState.anime.sortDescending()) {
                allEpisodes.subList(index + 1, allEpisodes.size)
            } else {
                allEpisodes.subList(0, index)
            }
            setSeenStatus.await(
                seen = true,
                episodes = previousEpisodes.toTypedArray(),
            )
        }
    }

    fun toggleBookmark(episodes: List<Episode>, bookmarked: Boolean) {
        screenModelScope.launchNonCancellable {
            val updates = episodes.map {
                EpisodeUpdate(id = it.id, bookmark = bookmarked)
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

    fun setUnseenFilter(state: TriState) {
        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_SEEN
        }
        screenModelScope.launchNonCancellable {
            val anime = (this@AnimeScreenModel.state.value as? State.Success)?.anime
                ?: return@launchNonCancellable
            setAnimeEpisodeFlags.awaitSetUnseenFilter(anime, flag)
        }
    }

    fun setBookmarkFilter(state: TriState) {
        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_BOOKMARKED
        }
        screenModelScope.launchNonCancellable {
            val anime = (this@AnimeScreenModel.state.value as? State.Success)?.anime
                ?: return@launchNonCancellable
            setAnimeEpisodeFlags.awaitSetBookmarkFilter(anime, flag)
        }
    }

    fun setDisplayMode(mode: Long) {
        screenModelScope.launchNonCancellable {
            val anime = (state.value as? State.Success)?.anime ?: return@launchNonCancellable
            setAnimeEpisodeFlags.awaitSetDisplayMode(anime, mode)
        }
    }

    // endregion

    // region Downloads

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
            prepareTorrentSourceIfNeeded(httpSource)

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

    fun downloadEpisodes(episodes: List<Episode>) {
        screenModelScope.launchNonCancellable {
            val successState = state.value as? State.Success ?: return@launchNonCancellable
            val anime = successState.anime
            val videos = episodes.map { null as Video? }
            animeDownloadManager.downloadEpisodes(anime, episodes, videos)
        }
    }

    fun deleteEpisodeDownloads(episodes: List<Episode>) {
        screenModelScope.launchNonCancellable {
            val successState = state.value as? State.Success ?: return@launchNonCancellable
            val anime = successState.anime
            val source = animeSourceManager.get(anime.source) ?: return@launchNonCancellable
            animeDownloadManager.deleteEpisodes(episodes, anime, source)
        }
    }

    private suspend fun resolveVideosForEpisode(source: AnimeHttpSource, episode: Episode): List<Video> {
        return withIOContext {
            val sEpisode = SEpisode.create().apply {
                url = episode.url
                name = episode.name
                date_upload = episode.dateUpload
                episode_number = episode.episodeNumber.toFloat()
                scanlator = episode.scanlator
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

    private fun prepareTorrentSourceIfNeeded(source: AnimeSource) {
        if (!source.isSourceForTorrents()) return
        TorrentServerService.start()
        if (TorrentServerService.wait(10)) {
            TorrentServerUtils.setTrackersList()
        }
    }

    // endregion

    // region Dialog

    fun showDialog(dialog: Dialog) {
        updateSuccessState { it.copy(dialog = dialog) }
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    // endregion

    // region State definitions

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val anime: Anime,
            val episodes: List<EpisodeList.Item>,
            val allEpisodeCount: Int = 0,
            val nextUnseenEpisode: Episode? = null,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
        ) : State {
            val isAnySelected by lazy {
                episodes.any { it.selected }
            }

            val selectedEpisodes by lazy {
                episodes.filter { it.selected }
            }

            val filterActive: Boolean
                get() = anime.unseenFilter != TriState.DISABLED || anime.bookmarkedFilter != TriState.DISABLED
        }
    }

    sealed interface Dialog {
        data object ConfirmDelete : Dialog
        data object SettingsSheet : Dialog
        data class DownloadLoading(val episode: Episode) : Dialog
        data class QualitySelection(val episode: Episode, val videos: List<Video>) : Dialog
        data class DuplicateAnime(val duplicates: List<Anime>) : Dialog
    }

    // endregion
}

@Immutable
sealed class EpisodeList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : EpisodeList()

    @Immutable
    data class Item(
        val episode: Episode,
        val downloadState: AnimeDownload.State,
        val downloadProgress: Int,
        val selected: Boolean = false,
    ) : EpisodeList() {
        val id = episode.id
        val isDownloaded = downloadState == AnimeDownload.State.DOWNLOADED
    }
}
