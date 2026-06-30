package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeHistory
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.backupAnimeTrackMapper
import eu.kanade.tachiyomi.data.backup.models.backupEpisodeMapper
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.history.interactor.GetAnimeHistory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeBackupCreator(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val getHistory: GetAnimeHistory = Injekt.get(),
) {

    suspend operator fun invoke(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        return animes.map { backupAnime(it, options) }
    }

    private suspend fun backupAnime(anime: Anime, options: BackupOptions): BackupAnime {
        val animeObject = anime.toBackupAnime()

        animeObject.excludedScanlators = handler.awaitList {
            excluded_anime_scanlatorsQueries.getExcludedScanlatorsByAnimeId(anime.id)
        }

        if (options.chapters) {
            handler.awaitList {
                episodesQueries.getEpisodesByAnimeId(
                    animeId = anime.id,
                    applyFilter = 0L,
                    mapper = backupEpisodeMapper,
                )
            }
                .takeUnless(List<BackupEpisode>::isEmpty)
                ?.let { animeObject.episodes = it }
        }

        if (options.categories) {
            val categoriesForAnime = getCategories.await(anime.id)
            if (categoriesForAnime.isNotEmpty()) {
                animeObject.categories = categoriesForAnime.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = handler.awaitList { anime_syncQueries.getTracksByAnimeId(anime.id, backupAnimeTrackMapper) }
            if (tracks.isNotEmpty()) {
                animeObject.tracking = tracks
            }
        }

        if (options.history) {
            val historyByAnimeId = getHistory.await(anime.id)
            if (historyByAnimeId.isNotEmpty()) {
                val history = historyByAnimeId.mapNotNull { history ->
                    val episode = handler.awaitOneOrNull { episodesQueries.getEpisodeById(history.episodeId) }
                    episode?.let { BackupAnimeHistory(it.url, history.watchedAt?.time ?: 0L, history.watchDuration) }
                }
                if (history.isNotEmpty()) {
                    animeObject.history = history
                }
            }
        }

        return animeObject
    }
}

private fun Anime.toBackupAnime() =
    BackupAnime(
        url = url,
        title = ogTitle,
        artist = ogArtist,
        author = ogAuthor,
        description = ogDescription,
        genre = ogGenre.orEmpty(),
        status = ogStatus.toInt(),
        thumbnailUrl = ogThumbnailUrl,
        favorite = favorite,
        source = source,
        dateAdded = dateAdded,
        viewerFlags = viewerFlags.toInt(),
        episodeFlags = episodeFlags.toInt(),
        updateStrategy = updateStrategy,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
        backgroundUrl = backgroundUrl,
        fetchType = fetchType,
        parentId = parentId,
        id = id,
        seasonFlags = seasonFlags,
        seasonNumber = seasonNumber,
        seasonSourceOrder = seasonSourceOrder,
    )
