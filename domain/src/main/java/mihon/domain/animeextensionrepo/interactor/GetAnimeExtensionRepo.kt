package mihon.domain.animeextensionrepo.interactor

import kotlinx.coroutines.flow.Flow
import mihon.domain.animeextensionrepo.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo

class GetAnimeExtensionRepo(
    private val repository: AnimeExtensionRepoRepository,
) {
    fun subscribeAll(): Flow<List<ExtensionRepo>> = repository.subscribeAll()

    suspend fun getAll(): List<ExtensionRepo> = repository.getAll()
}
