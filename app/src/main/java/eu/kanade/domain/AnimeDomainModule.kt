package eu.kanade.domain

import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.anime.interactor.UpdateAnime as AppUpdateAnime
import eu.kanade.domain.episode.interactor.SetSeenStatus as AppSetSeenStatus
import eu.kanade.domain.track.interactor.SyncEpisodeProgressWithTrack
import eu.kanade.domain.track.interactor.TrackEpisode
import tachiyomi.data.anime.CustomAnimeRepositoryImpl
import tachiyomi.data.anime.AnimeRepositoryImpl
import tachiyomi.data.category.AnimeCategoryRepositoryImpl
import tachiyomi.data.episode.EpisodeRepositoryImpl
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.FetchInterval
import tachiyomi.domain.anime.interactor.GetAnimeWithEpisodes
import tachiyomi.domain.anime.interactor.GetCustomAnimeInfo
import tachiyomi.domain.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.anime.interactor.GetFavoriteAnime
import tachiyomi.domain.anime.interactor.GetFavorites
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.interactor.SetCustomAnimeInfo
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.anime.interactor.UpdateAnime as DomainUpdateAnime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.anime.repository.CustomAnimeRepository
import tachiyomi.domain.category.interactor.CreateAnimeCategory
import tachiyomi.domain.category.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.category.repository.AnimeCategoryRepository
import eu.kanade.domain.episode.interactor.SyncEpisodesWithSource
import tachiyomi.domain.episode.interactor.FilterEpisodesForDownload
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.episode.interactor.SetSeenStatus as DomainSetSeenStatus
import tachiyomi.domain.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.repository.EpisodeRepository
import tachiyomi.domain.history.interactor.GetNextEpisodes
import tachiyomi.domain.library.service.AnimeLibraryPreferences
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class AnimeDomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<AnimeRepository> { AnimeRepositoryImpl(get()) }
        addSingletonFactory<CustomAnimeRepository> { CustomAnimeRepositoryImpl(get()) }
        addFactory { GetAnime(get()) }
        addFactory { FetchInterval(get()) }
        addFactory { GetAnimeWithEpisodes(get(), get()) }
        addFactory { GetCustomAnimeInfo(get()) }
        addFactory { DomainUpdateAnime(get()) }
        addFactory { AppUpdateAnime(get(), get()) }
        addFactory { GetFavoriteAnime(get()) }
        addFactory { GetFavorites(get()) }
        addFactory { GetLibraryAnime(get()) }
        addFactory { NetworkToLocalAnime(get()) }
        addFactory { SetCustomAnimeInfo(get()) }
        addFactory { SetAnimeEpisodeFlags(get()) }
        addFactory { GetDuplicateLibraryAnime(get()) }

        addSingletonFactory<EpisodeRepository> { EpisodeRepositoryImpl(get()) }
        addFactory { GetEpisode(get()) }
        addFactory { GetEpisodesByAnimeId(get()) }
        addFactory { UpdateEpisode(get()) }
        addFactory { SetAnimeDefaultEpisodeFlags(get(), get(), get()) }
        addFactory { DomainSetSeenStatus(get()) }
        addFactory { AppSetSeenStatus(get(), get()) }
        addFactory { ShouldUpdateDbEpisode() }
        addFactory { SyncEpisodesWithSource(get(), get(), get(), get(), get()) }
        addFactory { FilterEpisodesForDownload(get(), get(), get()) }
        addFactory { GetNextEpisodes(get(), get(), get()) }

        addSingletonFactory<AnimeCategoryRepository> { AnimeCategoryRepositoryImpl(get()) }
        addFactory { GetAnimeCategories(get()) }
        addFactory { CreateAnimeCategory(get()) }
        addFactory { DeleteAnimeCategory(get()) }
        addFactory { SetAnimeCategories(get()) }

        addSingletonFactory { AnimeLibraryPreferences(get()) }

        addFactory { InsertAnimeTrack(get()) }
        addFactory { SyncEpisodeProgressWithTrack(get(), get(), get()) }
        addFactory { AddAnimeTracks(get(), get(), get(), get()) }
        addFactory { TrackEpisode() }
    }
}
