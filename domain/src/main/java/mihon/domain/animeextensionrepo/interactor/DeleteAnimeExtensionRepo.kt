package mihon.domain.animeextensionrepo.interactor

import mihon.domain.animeextensionrepo.repository.AnimeExtensionRepoRepository

class DeleteAnimeExtensionRepo(
    private val repository: AnimeExtensionRepoRepository,
) {
    suspend fun await(baseUrl: String) {
        repository.deleteRepo(baseUrl)
    }
}
