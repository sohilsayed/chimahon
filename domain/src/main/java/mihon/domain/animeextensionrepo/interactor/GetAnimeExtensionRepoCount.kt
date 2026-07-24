package mihon.domain.animeextensionrepo.interactor

import mihon.domain.animeextensionrepo.repository.AnimeExtensionRepoRepository

class GetAnimeExtensionRepoCount(
    private val repository: AnimeExtensionRepoRepository,
) {
    fun subscribe() = repository.getCount()
}
