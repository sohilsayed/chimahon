package eu.kanade.tachiyomi.ui.browse.animesource

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.core.common.preference.getAndSet
import java.util.SortedMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.anime.model.AnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSourcesFilterScreenModel(
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
    private val toggleLanguage: ToggleLanguage = Injekt.get(),
) : StateScreenModel<AnimeSourcesFilterScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            combine(
                preferences.enabledLanguages().changes(),
                preferences.disabledAnimeSources().changes(),
            ) { enabledLanguages, disabledSources ->
                val allSources = animeSourceManager.getCatalogueSources()
                    .map { source ->
                        AnimeSource(
                            id = source.id,
                            lang = source.lang,
                            name = source.name,
                            supportsLatest = source.supportsLatest,
                            isStub = false,
                        )
                    }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

                val byLang = allSources
                    .groupBy { it.lang }
                    .toSortedMap()

                State.Success(
                    items = byLang.mapValues { it.value.toImmutableList() }.toSortedMap(),
                    enabledLanguages = enabledLanguages.toImmutableSet(),
                    disabledSources = disabledSources.toImmutableSet(),
                )
            }
                .collectLatest { state ->
                    mutableState.update { state }
                }
        }
    }

    fun toggleLanguage(language: String) {
        toggleLanguage.await(language)
    }

    fun toggleSource(source: AnimeSource) {
        val isEnabled = source.id.toString() !in preferences.disabledAnimeSources().get()
        preferences.disabledAnimeSources().getAndSet { disabled ->
            if (isEnabled) disabled + source.id.toString() else disabled - source.id.toString()
        }
    }

    sealed interface State {

        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val items: SortedMap<String, ImmutableList<AnimeSource>>,
            val enabledLanguages: ImmutableSet<String>,
            val disabledSources: ImmutableSet<String>,
        ) : State {

            val isEmpty: Boolean
                get() = items.isEmpty()
        }
    }
}
