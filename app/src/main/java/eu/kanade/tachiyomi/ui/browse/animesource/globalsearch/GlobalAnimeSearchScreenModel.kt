package eu.kanade.tachiyomi.ui.browse.animesource.globalsearch

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource

class GlobalAnimeSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
) : AnimeSearchScreenModel(
    State(
        searchQuery = initialQuery,
    ),
) {

    init {
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                setSourceFilter(AnimeSourceFilter.All)
            }
            search()
        }
    }

    override fun getEnabledSources(): List<AnimeCatalogueSource> {
        return super.getEnabledSources()
            .filter { state.value.sourceFilter != AnimeSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
    }
}
