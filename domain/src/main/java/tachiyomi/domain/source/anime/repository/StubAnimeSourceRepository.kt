package tachiyomi.domain.source.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.anime.model.StubAnimeSource

interface StubAnimeSourceRepository {
    fun subscribeAll(): Flow<List<StubAnimeSource>>

    suspend fun getStubSource(id: Long): StubAnimeSource?

    suspend fun upsertStubSource(id: Long, lang: String, name: String)
}
