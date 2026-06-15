package tachiyomi.data.animesource

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.animesource.model.StubAnimeSource
import tachiyomi.domain.animesource.repository.StubAnimeSourceRepository

class StubAnimeSourceRepositoryImpl(
    private val handler: DatabaseHandler,
) : StubAnimeSourceRepository {

    override fun subscribeAll(): Flow<List<StubAnimeSource>> {
        return handler.subscribeToList { anime_sourcesQueries.findAll(::mapStubAnimeSource) }
    }

    override suspend fun getStubSource(id: Long): StubAnimeSource? {
        return handler.awaitOneOrNull { anime_sourcesQueries.findOne(id, ::mapStubAnimeSource) }
    }

    override suspend fun upsertStubSource(id: Long, lang: String, name: String) {
        handler.await { anime_sourcesQueries.upsert(id, lang, name) }
    }

    private fun mapStubAnimeSource(
        id: Long,
        lang: String,
        name: String,
    ): StubAnimeSource = StubAnimeSource(id = id, lang = lang, name = name)
}
