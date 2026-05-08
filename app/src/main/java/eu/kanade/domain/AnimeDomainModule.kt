package eu.kanade.domain

import tachiyomi.data.anime.AnimeRepositoryImpl
import tachiyomi.data.episode.EpisodeRepositoryImpl
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetFavoriteAnime
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.anime.interactor.UpdateAnime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.SetSeenStatus
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.repository.EpisodeRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AnimeDomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<AnimeRepository> { AnimeRepositoryImpl(get()) }
        addFactory { GetAnime(get()) }
        addFactory { UpdateAnime(get()) }
        addFactory { GetFavoriteAnime(get()) }
        addFactory { SetAnimeEpisodeFlags(get()) }

        addSingletonFactory<EpisodeRepository> { EpisodeRepositoryImpl(get()) }
        addFactory { GetEpisode(get()) }
        addFactory { GetEpisodesByAnimeId(get()) }
        addFactory { UpdateEpisode(get()) }
        addFactory { SetSeenStatus(get()) }
    }
}
