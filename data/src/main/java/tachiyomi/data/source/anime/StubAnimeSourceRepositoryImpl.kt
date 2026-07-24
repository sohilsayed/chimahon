package tachiyomi.data.source.anime

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.StubAnimeSourceRepository

class StubAnimeSourceRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : StubAnimeSourceRepository {

    override fun subscribeAll(): Flow<List<StubAnimeSource>> {
        return handler.subscribeToList { animesourcesQueries.findAll(::mapStubAnimeSource) }
    }

    override suspend fun getStubSource(id: Long): StubAnimeSource? {
        return handler.awaitOneOrNull { animesourcesQueries.findOne(id, ::mapStubAnimeSource) }
    }

    override suspend fun upsertStubSource(id: Long, lang: String, name: String) {
        handler.await { animesourcesQueries.upsert(id, lang, name) }
    }

    private fun mapStubAnimeSource(
        id: Long,
        lang: String,
        name: String,
    ): StubAnimeSource = StubAnimeSource(id = id, lang = lang, name = name)
}
