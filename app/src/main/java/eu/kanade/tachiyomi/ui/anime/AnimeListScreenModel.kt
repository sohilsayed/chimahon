package eu.kanade.tachiyomi.ui.anime

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeListScreenModel(
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
) : StateScreenModel<AnimeListScreenModel.State>(State.Loading) {

    private var loadJob: Job? = null

    sealed interface State {
        data object Loading : State
        data class Success(val items: List<AnimeWithEpisode>) : State
        data class Error(val error: Exception) : State
    }

    data class AnimeWithEpisode(
        val anime: Anime,
        val lastEpisode: Episode?,
        val unseenCount: Int = 0,
    )

    fun loadAnime() {
        loadJob?.cancel()
        loadJob = screenModelScope.launchIO {
            try {
                val animes = animeRepository.getAll()
                val items = coroutineScope {
                    animes.map { anime ->
                        async {
                            val episodes = getEpisodesByAnimeId.await(anime.id)
                            AnimeWithEpisode(
                                anime = anime,
                                lastEpisode = episodes.filter { it.lastSecondSeen > 0 }.maxByOrNull { it.lastModifiedAt },
                                unseenCount = episodes.count { !it.seen },
                            )
                        }
                    }.awaitAll()
                }
                mutableState.value = State.Success(items)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load anime list" }
                mutableState.value = State.Error(e)
            }
        }
    }
}
