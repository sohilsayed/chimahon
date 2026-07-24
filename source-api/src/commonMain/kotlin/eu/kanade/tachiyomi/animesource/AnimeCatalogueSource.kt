package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import rx.Observable
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.awaitSingle
import tachiyomi.core.common.util.system.logcat

interface AnimeCatalogueSource : AnimeSource {

    override val lang: String

    val supportsLatest: Boolean

    @Suppress("DEPRECATION")
    suspend fun getPopularAnime(page: Int): AnimesPage {
        return fetchPopularAnime(page).awaitSingle()
    }

    @Suppress("DEPRECATION")
    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        return fetchSearchAnime(page, query, filters).awaitSingle()
    }

    @Suppress("DEPRECATION")
    suspend fun getLatestUpdates(page: Int): AnimesPage {
        return fetchLatestUpdates(page).awaitSingle()
    }

    fun getFilterList(): AnimeFilterList

    /**
     * Whether the extension provides its own related anime request.
     */
    val supportsRelatedAnime: Boolean get() = false

    /**
     * Extensions can opt out of the app's title-search based recommendations.
     */
    val disableRelatedAnimeBySearch: Boolean get() = false

    /**
     * Disable showing any related anime.
     */
    val disableRelatedAnime: Boolean get() = false

    override suspend fun getRelatedAnimeList(
        anime: SAnime,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        val handler = CoroutineExceptionHandler { _, e -> exceptionHandler(e) }
        if (!disableRelatedAnime) {
            supervisorScope {
                if (supportsRelatedAnime) launch(handler) { getRelatedAnimeListByExtension(anime, pushResults) }
                if (!disableRelatedAnimeBySearch) launch(handler) { getRelatedAnimeListBySearch(anime, pushResults) }
            }
        }
    }

    suspend fun getRelatedAnimeListByExtension(
        anime: SAnime,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        runCatching { fetchRelatedAnimeList(anime) }
            .onSuccess { if (it.isNotEmpty()) pushResults(Pair("", it), false) }
            .onFailure { e ->
                logcat(LogPriority.ERROR, e) { "## getRelatedAnimeListByExtension: $e" }
            }
    }

    suspend fun fetchRelatedAnimeList(anime: SAnime): List<SAnime> = throw UnsupportedOperationException("Unsupported!")

    fun String.stripKeywordForRelatedAnime(): List<String> {
        val regexWhitespace = Regex("\\s+")
        val regexSpecialCharacters = Regex("([!~#$%^&*+_|/\\\\,?:;'\"<>(){}\\[\\]]|\\s-|-\\s|\\s\\.|\\.\\s])")
        val regexNumberOnly = Regex("^\\d+$")

        return replace(regexSpecialCharacters, " ")
            .split(regexWhitespace)
            .map {
                it.replace(regexNumberOnly, "")
                    .lowercase()
            }
            .filter { it.length > 1 }
    }

    suspend fun getRelatedAnimeListBySearch(
        anime: SAnime,
        pushResults: suspend (relatedAnime: Pair<String, List<SAnime>>, completed: Boolean) -> Unit,
    ) {
        val words = linkedSetOf(anime.title)
        anime.title.stripKeywordForRelatedAnime()
            .filterNot { word -> words.any { it.equals(word, ignoreCase = true) } }
            .onEach { words.add(it) }
        if (words.isEmpty()) return

        coroutineScope {
            val filterList = getFilterList()
            words.map { keyword ->
                launch {
                    runCatching {
                        getSearchAnime(1, keyword.sanitize(), filterList).animes
                    }
                        .onSuccess { if (it.isNotEmpty()) pushResults(Pair(keyword, it), false) }
                        .onFailure { e ->
                            logcat(LogPriority.ERROR, e) { "## getRelatedAnimeListBySearch: $e" }
                        }
                }
            }
        }
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPopularAnime"),
    )
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getSearchAnime"),
    )
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage>

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getLatestUpdates"),
    )
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>
}
