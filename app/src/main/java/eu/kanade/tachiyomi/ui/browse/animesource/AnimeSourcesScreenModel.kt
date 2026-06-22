package eu.kanade.tachiyomi.ui.browse.animesource

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animeextension.AnimeExtensionManager
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSourcesScreenModel(
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val animeExtensionManager: AnimeExtensionManager = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<AnimeSourcesScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            combine(
                animeSourceManager.catalogueSources,
                preferences.enabledLanguages().changes(),
                preferences.pinnedSources().changes(),
                animeExtensionManager.installedExtensionsFlow,
            ) { sources, enabledLanguages, pinnedSources, extensions ->
                val extensionBySourceId = extensions
                    .flatMap { extension -> extension.sources.map { source -> source.id to extension } }
                    .toMap()

                sources
                    .filter {
                        it.id == LocalAnimeSource.ID ||
                            it.lang in enabledLanguages ||
                            enabledLanguages.isEmpty()
                    }
                    .map { source -> AnimeSourceUiModel(source, extensionBySourceId[source.id]) }
                    .sortedWith(
                        compareBy<AnimeSourceUiModel> { it.source.id.toString() !in pinnedSources }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.source.name },
                    )
                    .groupBy { it.source.lang }
                    .toSortedMap(LocaleHelper.comparator)
            }
                .collectLatest { grouped ->
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            items = grouped,
                        )
                    }
                }
        }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val items: Map<String, List<AnimeSourceUiModel>> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = items.isEmpty()
    }

    @Immutable
    data class AnimeSourceUiModel(
        val source: AnimeCatalogueSource,
        val extension: AnimeExtension.Installed?,
    )
}
