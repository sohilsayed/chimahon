package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.MergeAnimeSettingsUpdate
import tachiyomi.domain.anime.model.MergedAnimeReference

interface AnimeMergeRepository {
    suspend fun getMergedAnime(): List<Anime>

    suspend fun subscribeMergedAnime(): Flow<List<Anime>>

    suspend fun getMergedAnimeById(id: Long): List<Anime>

    suspend fun subscribeMergedAnimeById(id: Long): Flow<List<Anime>>

    suspend fun getReferencesById(id: Long): List<MergedAnimeReference>

    suspend fun subscribeReferencesById(id: Long): Flow<List<MergedAnimeReference>>

    suspend fun updateSettings(update: MergeAnimeSettingsUpdate): Boolean

    suspend fun updateAllSettings(values: List<MergeAnimeSettingsUpdate>): Boolean

    suspend fun insert(reference: MergedAnimeReference): Long?

    suspend fun insertAll(references: List<MergedAnimeReference>)

    suspend fun deleteById(id: Long)

    suspend fun deleteByMergeId(mergeId: Long)

    suspend fun getMergeAnimeForDownloading(mergeId: Long): List<Anime>
}
