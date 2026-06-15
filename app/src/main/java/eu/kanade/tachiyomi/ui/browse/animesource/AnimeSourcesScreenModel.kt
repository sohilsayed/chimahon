package eu.kanade.tachiyomi.ui.browse.animesource

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.animesource.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSourcesScreenModel(
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<AnimeSourcesScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            combine(
                animeSourceManager.catalogueSources,
                preferences.enabledLanguages().changes(),
                preferences.pinnedSources().changes(),
            ) { sources, enabledLanguages, pinnedSources ->
                sources
                    .filter { it.lang in enabledLanguages || enabledLanguages.isEmpty() }
                    .sortedWith(
                        compareBy<AnimeCatalogueSource> { it.id.toString() !in pinnedSources }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                    )
                    .groupBy { it.lang }
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
        val items: Map<String, List<AnimeCatalogueSource>> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = items.isEmpty()
    }
}
