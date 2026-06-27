package eu.kanade.domain

import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.entries.anime.interactor.UpdateAnime as AppUpdateAnime
import eu.kanade.domain.episode.interactor.SetSeenStatus as AppSetSeenStatus
import eu.kanade.domain.track.interactor.SyncEpisodeProgressWithTrack
import eu.kanade.domain.track.interactor.TrackEpisode
import tachiyomi.data.entries.anime.CustomAnimeRepositoryImpl
import tachiyomi.data.entries.anime.AnimeRepositoryImpl
import tachiyomi.data.category.AnimeCategoryRepositoryImpl
import tachiyomi.data.episode.EpisodeRepositoryImpl
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.FetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodes
import tachiyomi.domain.entries.anime.interactor.GetCustomAnimeInfo
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.GetFavoriteAnime
import tachiyomi.domain.entries.anime.interactor.GetFavorites
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.interactor.SetCustomAnimeInfo
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.anime.interactor.UpdateAnime as DomainUpdateAnime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.anime.repository.CustomAnimeRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.category.interactor.CreateAnimeCategory
import tachiyomi.domain.category.interactor.DeleteAnimeCategory
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.category.interactor.HideAnimeCategory
import tachiyomi.domain.category.interactor.RenameAnimeCategory
import tachiyomi.domain.category.interactor.ReorderAnimeCategory
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
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.AnimeLibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
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
        addFactory { NetworkToLocalAnime(get(), get()) }
        addFactory { SetCustomAnimeInfo(get()) }
        addFactory { SetAnimeEpisodeFlags(get()) }
        addFactory { SetAnimeSeasonFlags(get()) }
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
        addFactory { CreateAnimeCategory(get(), get()) }
        addFactory { DeleteAnimeCategory(get(), get(), get()) }
        addFactory { RenameAnimeCategory(get()) }
        addFactory { ReorderAnimeCategory(get()) }
        addFactory { HideAnimeCategory(get()) }
        addFactory { SetAnimeCategories(get()) }

        addSingletonFactory { AnimeLibraryPreferences(get()) }

        addFactory { InsertAnimeTrack(get()) }
        addFactory { SyncEpisodeProgressWithTrack(get(), get(), get()) }
        addFactory { AddAnimeTracks(get(), get(), get(), get()) }
        addFactory { TrackEpisode() }
    }
}
