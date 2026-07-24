package tachiyomi.data.entries.anime

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.MergeAnimeSettingsUpdate
import tachiyomi.domain.entries.anime.model.MergedAnimeReference
import tachiyomi.domain.entries.anime.repository.AnimeMergeRepository

class AnimeMergeRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeMergeRepository {

    override suspend fun getMergedAnime(): List<Anime> {
        return handler.awaitList { merged_animeQueries.selectAllMergedAnimes(AnimeMapper::mapAnime) }
    }

    override suspend fun subscribeMergedAnime(): Flow<List<Anime>> {
        return handler.subscribeToList { merged_animeQueries.selectAllMergedAnimes(AnimeMapper::mapAnime) }
    }

    override suspend fun getMergedAnimeById(id: Long): List<Anime> {
        return handler.awaitList { merged_animeQueries.selectMergedAnimesById(id, AnimeMapper::mapAnime) }
    }

    override suspend fun subscribeMergedAnimeById(id: Long): Flow<List<Anime>> {
        return handler.subscribeToList { merged_animeQueries.selectMergedAnimesById(id, AnimeMapper::mapAnime) }
    }

    override suspend fun getReferencesById(id: Long): List<MergedAnimeReference> {
        return handler.awaitList { merged_animeQueries.selectByMergeId(id, MergedAnimeMapper::map) }
    }

    override suspend fun subscribeReferencesById(id: Long): Flow<List<MergedAnimeReference>> {
        return handler.subscribeToList { merged_animeQueries.selectByMergeId(id, MergedAnimeMapper::map) }
    }

    override suspend fun updateSettings(update: MergeAnimeSettingsUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllSettings(values: List<MergeAnimeSettingsUpdate>): Boolean {
        return try {
            partialUpdate(*values.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdate(vararg values: MergeAnimeSettingsUpdate) {
        handler.await(inTransaction = true) {
            values.forEach { value ->
                merged_animeQueries.updateSettingsById(
                    id = value.id,
                    getEpisodeUpdates = value.getEpisodeUpdates ?: false,
                    downloadEpisodes = value.downloadEpisodes ?: false,
                    infoAnime = value.isInfoAnime ?: false,
                    episodePriority = value.episodePriority?.toLong() ?: 0L,
                    episodeSortMode = value.episodeSortMode?.toLong() ?: 0L,
                    mergeUrl = value.mergeUrl ?: "",
                )
            }
        }
    }

    override suspend fun insert(reference: MergedAnimeReference): Long? {
        return handler.awaitOneOrNullExecutable {
            merged_animeQueries.insert(
                infoAnime = reference.isInfoAnime,
                getEpisodeUpdates = reference.getEpisodeUpdates,
                episodeSortMode = reference.episodeSortMode.toLong(),
                episodePriority = reference.episodePriority.toLong(),
                downloadEpisodes = reference.downloadEpisodes,
                mergeId = reference.mergeId!!,
                mergeUrl = reference.mergeUrl,
                animeId = reference.animeId,
                animeUrl = reference.animeUrl,
                animeSource = reference.animeSourceId,
            )
            merged_animeQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun insertAll(references: List<MergedAnimeReference>) {
        handler.await(true) {
            references.forEach { reference ->
                merged_animeQueries.insert(
                    infoAnime = reference.isInfoAnime,
                    getEpisodeUpdates = reference.getEpisodeUpdates,
                    episodeSortMode = reference.episodeSortMode.toLong(),
                    episodePriority = reference.episodePriority.toLong(),
                    downloadEpisodes = reference.downloadEpisodes,
                    mergeId = reference.mergeId!!,
                    mergeUrl = reference.mergeUrl,
                    animeId = reference.animeId,
                    animeUrl = reference.animeUrl,
                    animeSource = reference.animeSourceId,
                )
            }
        }
    }

    override suspend fun deleteById(id: Long) {
        handler.await {
            merged_animeQueries.deleteById(id)
        }
    }

    override suspend fun deleteByMergeId(mergeId: Long) {
        handler.await {
            merged_animeQueries.deleteByMergeId(mergeId)
        }
    }

    override suspend fun getMergeAnimeForDownloading(mergeId: Long): List<Anime> {
        return handler.awaitList { merged_animeQueries.selectMergedAnimesForDownloadingById(mergeId, AnimeMapper::mapAnime) }
    }
}
