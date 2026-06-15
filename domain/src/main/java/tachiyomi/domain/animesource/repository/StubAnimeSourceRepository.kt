package tachiyomi.domain.animesource.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.animesource.model.StubAnimeSource

interface StubAnimeSourceRepository {
    fun subscribeAll(): Flow<List<StubAnimeSource>>

    suspend fun getStubSource(id: Long): StubAnimeSource?

    suspend fun upsertStubSource(id: Long, lang: String, name: String)
}
