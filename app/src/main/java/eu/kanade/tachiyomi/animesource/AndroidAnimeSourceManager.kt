package eu.kanade.tachiyomi.animesource

import android.content.Context
import eu.kanade.tachiyomi.animeextension.AnimeExtensionManager
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.repository.StubAnimeSourceRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AndroidAnimeSourceManager(
    private val context: Context,
    private val animeExtensionManager: AnimeExtensionManager,
    private val animeSourceRepository: StubAnimeSourceRepository,
) : AnimeSourceManager {

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(HashMap<Long, eu.kanade.tachiyomi.animesource.AnimeSource>())

    private val stubSourcesMap = HashMap<Long, StubAnimeSource>()

    override val catalogueSources: Flow<List<AnimeCatalogueSource>> = sourcesMapFlow.map {
        it.values.filterIsInstance<AnimeCatalogueSource>()
    }

    init {
        scope.launch {
            animeExtensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = HashMap<Long, eu.kanade.tachiyomi.animesource.AnimeSource>().apply {
                        put(
                            LocalAnimeSource.ID,
                            LocalAnimeSource(
                                context = context,
                                fileSystem = Injekt.get(),
                                coverManager = Injekt.get(),
                                backgroundManager = Injekt.get(),
                                thumbnailManager = Injekt.get(),
                                fetchTypeManager = Injekt.get(),
                            ),
                        )
                    }
                    extensions.forEach { extension ->
                        extension.sources.forEach { source ->
                            mutableMap[source.id] = source
                            registerStubSource(StubAnimeSource.from(source))
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                    _isInitialized.value = true
                }
        }

        scope.launch {
            animeSourceRepository.subscribeAll()
                .collectLatest { sources ->
                    sources.forEach {
                        stubSourcesMap[it.id] = it
                    }
                }
        }
    }

    override fun get(sourceKey: Long): eu.kanade.tachiyomi.animesource.AnimeSource? {
        return sourcesMapFlow.value[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): eu.kanade.tachiyomi.animesource.AnimeSource {
        return sourcesMapFlow.value[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { createStubSource(sourceKey) }
        }
    }

    override fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<AnimeHttpSource>()

    override fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<AnimeCatalogueSource>()

    override fun getStubSources(): List<StubAnimeSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    private fun registerStubSource(source: StubAnimeSource) {
        scope.launch {
            val dbSource = animeSourceRepository.getStubSource(source.id)
            if (dbSource == source) return@launch
            animeSourceRepository.upsertStubSource(source.id, source.lang, source.name)
        }
    }

    private suspend fun createStubSource(id: Long): StubAnimeSource {
        animeSourceRepository.getStubSource(id)?.let {
            return it
        }
        animeExtensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubAnimeSource(id = id, lang = "", name = "")
    }
}
