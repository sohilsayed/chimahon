package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeHistory
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeTracking
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.model.AnimeTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max

class AnimeRestorer(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val updateAnimeInteractor: UpdateAnime = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val insertTrack: InsertAnimeTrack = Injekt.get(),
) {

    private val now = ZonedDateTime.now()
    private val currentFetchWindow = Injekt.get<tachiyomi.domain.entries.anime.interactor.FetchInterval>().getWindow(now)

    suspend fun sortByNew(backupAnimes: List<BackupAnime>): List<BackupAnime> {
        val urlsBySource = handler.awaitList { animesQueries.getAllAnimeSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupAnimes
            .sortedWith(
                compareBy<BackupAnime> { it.url in urlsBySource[it.source].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restore(
        backupAnime: BackupAnime,
        backupCategories: List<BackupCategory>,
        backupSeasons: List<BackupAnime>,
    ) {
        handler.await(inTransaction = true) {
            val dbAnime = findExistingAnime(backupAnime)
            val anime = backupAnime.getAnimeImpl()
            val restoredAnime = if (dbAnime == null) {
                restoreNewAnime(anime)
            } else {
                restoreExistingAnime(anime, dbAnime)
            }

            backupSeasons.forEach { backupSeason ->
                val dbSeason = findExistingAnime(backupSeason)
                val season = backupSeason.getAnimeImpl().copy(parentId = restoredAnime.id)
                if (dbSeason == null) {
                    restoreNewAnime(season)
                } else {
                    restoreExistingAnime(season, dbSeason)
                }
            }

            restoreAnimeDetails(
                anime = restoredAnime,
                episodes = backupAnime.episodes,
                categories = backupAnime.categories,
                backupCategories = backupCategories,
                history = backupAnime.history,
                tracks = backupAnime.tracking,
                excludedScanlators = backupAnime.excludedScanlators,
            )

            animesQueries.resetIsSyncing()
            episodesQueries.resetIsSyncing()
        }
    }

    private suspend fun findExistingAnime(backupAnime: BackupAnime): Anime? {
        return getAnimeByUrlAndSourceId.await(backupAnime.url, backupAnime.source)
    }

    private suspend fun restoreExistingAnime(anime: Anime, dbAnime: Anime): Anime {
        return if (anime.version > dbAnime.version) {
            updateAnime(dbAnime.copyFrom(anime).copy(id = dbAnime.id, parentId = anime.parentId))
        } else {
            updateAnime(anime.copyFrom(dbAnime).copy(id = dbAnime.id, parentId = anime.parentId))
        }
    }

    private fun Anime.copyFrom(newer: Anime): Anime {
        return copy(
            favorite = favorite || newer.favorite,
            ogAuthor = newer.ogAuthor,
            ogArtist = newer.ogArtist,
            ogDescription = newer.ogDescription,
            ogGenre = newer.ogGenre,
            ogThumbnailUrl = newer.ogThumbnailUrl,
            ogStatus = newer.ogStatus,
            backgroundUrl = newer.backgroundUrl,
            initialized = initialized || newer.initialized,
            version = newer.version,
            fetchType = newer.fetchType,
            parentId = newer.parentId,
        )
    }

    private suspend fun updateAnime(anime: Anime): Anime {
        handler.await(true) {
            animesQueries.update(
                source = anime.source,
                url = anime.url,
                artist = anime.ogArtist,
                author = anime.ogAuthor,
                description = anime.ogDescription,
                genre = anime.ogGenre?.let(StringListColumnAdapter::encode),
                title = anime.ogTitle,
                status = anime.ogStatus,
                thumbnailUrl = anime.ogThumbnailUrl,
                favorite = anime.favorite,
                lastUpdate = anime.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = anime.initialized,
                viewer = anime.viewerFlags,
                episodeFlags = anime.episodeFlags,
                coverLastModified = anime.coverLastModified,
                dateAdded = anime.dateAdded,
                animeId = anime.id,
                updateStrategy = anime.updateStrategy.let(UpdateStrategyColumnAdapter::encode),
                version = anime.version,
                isSyncing = 1,
                fetchType = anime.fetchType.let(FetchTypeColumnAdapter::encode),
                parentId = anime.parentId,
                seasonFlags = anime.seasonFlags,
                seasonNumber = anime.seasonNumber,
                seasonSourceOrder = anime.seasonSourceOrder,
                backgroundUrl = anime.backgroundUrl,
                backgroundLastModified = anime.backgroundLastModified,
            )
        }
        return anime
    }

    private suspend fun restoreNewAnime(anime: Anime): Anime {
        return anime.copy(
            initialized = anime.description != null,
            id = insertAnime(anime),
            version = anime.version,
        )
    }

    private suspend fun insertAnime(anime: Anime): Long {
        return handler.awaitOneExecutable(true) {
            animesQueries.insert(
                source = anime.source,
                url = anime.url,
                artist = anime.ogArtist,
                author = anime.ogAuthor,
                description = anime.ogDescription,
                genre = anime.ogGenre,
                title = anime.ogTitle,
                status = anime.ogStatus,
                thumbnailUrl = anime.ogThumbnailUrl,
                favorite = anime.favorite,
                lastUpdate = anime.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = anime.initialized,
                viewerFlags = anime.viewerFlags,
                episodeFlags = anime.episodeFlags,
                coverLastModified = anime.coverLastModified,
                dateAdded = anime.dateAdded,
                updateStrategy = anime.updateStrategy,
                version = anime.version,
                fetchType = anime.fetchType,
                parentId = anime.parentId,
                seasonFlags = anime.seasonFlags,
                seasonNumber = anime.seasonNumber,
                seasonSourceOrder = anime.seasonSourceOrder,
                backgroundUrl = anime.backgroundUrl,
                backgroundLastModified = anime.backgroundLastModified,
            )
            animesQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreAnimeDetails(
        anime: Anime,
        episodes: List<BackupEpisode>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupAnimeHistory>,
        tracks: List<BackupAnimeTracking>,
        excludedScanlators: List<String>,
    ): Anime {
        restoreCategories(anime, categories, backupCategories)
        restoreEpisodes(anime, episodes)
        restoreTracking(anime, tracks)
        restoreHistory(history)
        restoreExcludedScanlators(anime, excludedScanlators)
        updateAnimeInteractor.awaitUpdateFetchInterval(anime, now, currentFetchWindow)
        return anime
    }

    private suspend fun restoreCategories(
        anime: Anime,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val animeCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.let { dbCategory ->
                    anime.id to dbCategory.id
                }
            }
        }

        if (animeCategoriesToUpdate.isNotEmpty()) {
            handler.await(true) {
                animes_categoriesQueries.deleteAnimeCategoryByAnimeId(anime.id)
                animeCategoriesToUpdate.forEach { (animeId, categoryId) ->
                    animes_categoriesQueries.insert(animeId, categoryId)
                }
            }
        }
    }

    private suspend fun restoreEpisodes(anime: Anime, backupEpisodes: List<BackupEpisode>) {
        val dbEpisodesByUrl = getEpisodesByAnimeId.await(anime.id)
            .associateBy { it.url }

        val (existingEpisodes, newEpisodes) = backupEpisodes
            .mapNotNull {
                val episode = it.toEpisodeImpl().copy(animeId = anime.id)
                val dbEpisode = dbEpisodesByUrl[episode.url] ?: return@mapNotNull episode

                if (episode.forComparison() == dbEpisode.forComparison()) {
                    return@mapNotNull null
                }

                episode.copyFrom(dbEpisode)
                    .copy(
                        id = dbEpisode.id,
                        bookmark = episode.bookmark || dbEpisode.bookmark,
                        fillermark = episode.fillermark || dbEpisode.fillermark,
                    )
                    .let { updatedEpisode ->
                        when {
                            dbEpisode.seen && !updatedEpisode.seen -> updatedEpisode.copy(
                                seen = true,
                                lastSecondSeen = dbEpisode.lastSecondSeen,
                            )
                            updatedEpisode.lastSecondSeen == 0L && dbEpisode.lastSecondSeen != 0L -> updatedEpisode.copy(
                                lastSecondSeen = dbEpisode.lastSecondSeen,
                            )
                            else -> updatedEpisode
                        }
                    }
            }
            .partition { it.id > 0 }

        insertNewEpisodes(newEpisodes)
        updateExistingEpisodes(existingEpisodes)
    }

    private fun Episode.forComparison() =
        copy(id = 0L, animeId = 0L, dateFetch = 0L, dateUpload = 0L, lastModifiedAt = 0L, version = 0L)

    private suspend fun insertNewEpisodes(episodes: List<Episode>) {
        handler.await(true) {
            episodes.forEach { episode ->
                episodesQueries.insert(
                    episode.animeId,
                    episode.url,
                    episode.name,
                    episode.scanlator,
                    episode.seen,
                    episode.bookmark,
                    episode.lastSecondSeen,
                    episode.totalSeconds,
                    episode.episodeNumber,
                    episode.sourceOrder,
                    episode.dateFetch,
                    episode.dateUpload,
                    episode.version,
                    episode.summary,
                    episode.previewUrl,
                    episode.fillermark,
                )
            }
        }
    }

    private suspend fun updateExistingEpisodes(episodes: List<Episode>) {
        handler.await(true) {
            episodes.forEach { episode ->
                episodesQueries.update(
                    animeId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    seen = episode.seen,
                    bookmark = episode.bookmark,
                    fillermark = episode.fillermark,
                    lastSecondSeen = episode.lastSecondSeen,
                    totalSeconds = episode.totalSeconds,
                    episodeNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    episodeId = episode.id,
                    version = episode.version,
                    isSyncing = 1,
                    summary = null,
                    previewUrl = null,
                )
            }
        }
    }

    private suspend fun restoreHistory(backupHistory: List<BackupAnimeHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            val dbHistory = handler.awaitOneOrNull { animehistoryQueries.getHistoryByEpisodeUrl(history.url) }
            val item = history.getHistoryImpl()

            if (dbHistory == null) {
                val episode = handler.awaitList { episodesQueries.getEpisodeByUrl(history.url) }.firstOrNull()
                return@mapNotNull episode?.let { item.copy(episodeId = it._id) }
            }

            item.copy(
                id = dbHistory._id,
                episodeId = dbHistory.episode_id,
                watchedAt = max(item.watchedAt?.time ?: 0L, dbHistory.last_seen?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let { Date(it) },
                watchDuration = max(item.watchDuration, dbHistory.watch_duration) - dbHistory.watch_duration,
            )
        }

        if (toUpdate.isNotEmpty()) {
            handler.await(true) {
                toUpdate.forEach {
                    animehistoryQueries.upsert(
                        it.episodeId,
                        it.watchedAt,
                        it.watchDuration,
                    )
                }
            }
        }
    }

    private suspend fun restoreTracking(anime: Anime, backupTracks: List<BackupAnimeTracking>) {
        val dbTrackByTrackerId = getTracks.await(anime.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: return@mapNotNull track.copy(id = 0, animeId = anime.id)

                if (track.forComparison() == dbTrack.forComparison()) {
                    return@mapNotNull null
                }

                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastEpisodeSeen = max(dbTrack.lastEpisodeSeen, track.lastEpisodeSeen),
                )
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            insertTrack.awaitAll(newTracks)
        }
        if (existingTracks.isNotEmpty()) {
            handler.await(true) {
                existingTracks.forEach { track ->
                    anime_syncQueries.update(
                        animeId = track.animeId,
                        syncId = track.trackerId,
                        mediaId = track.remoteId,
                        libraryId = track.libraryId,
                        title = track.title,
                        lastEpisodeSeen = track.lastEpisodeSeen,
                        totalEpisodes = track.totalEpisodes,
                        status = track.status,
                        score = track.score,
                        trackingUrl = track.remoteUrl,
                        startDate = track.startDate,
                        finishDate = track.finishDate,
                        private = track.private,
                        id = track.id,
                    )
                }
            }
        }
    }

    private fun AnimeTrack.forComparison() = copy(id = 0L, animeId = 0L)

    private suspend fun restoreExcludedScanlators(anime: Anime, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return

        val existingExcludedScanlators = handler.awaitList {
            excluded_anime_scanlatorsQueries.getExcludedScanlatorsByAnimeId(anime.id)
        }.toSet()
        val toInsert = excludedScanlators.toSet().subtract(existingExcludedScanlators)
        if (toInsert.isNotEmpty()) {
            handler.await(true) {
                toInsert.forEach {
                    excluded_anime_scanlatorsQueries.insert(anime.id, it)
                }
            }
        }
    }
}
