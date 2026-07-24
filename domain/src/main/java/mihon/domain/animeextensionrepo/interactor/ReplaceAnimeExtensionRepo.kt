package mihon.domain.animeextensionrepo.interactor

import mihon.domain.animeextensionrepo.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo

class ReplaceAnimeExtensionRepo(
    private val repository: AnimeExtensionRepoRepository,
) {
    suspend fun await(repo: ExtensionRepo) {
        repository.replaceRepo(repo)
    }
}
