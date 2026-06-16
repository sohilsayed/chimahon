package tachiyomi.source.local.entries.anime

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.runBlocking
import rx.Observable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.source.local.filter.anime.AnimeOrderBy
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem

actual class LocalAnimeSource(
    private val context: Context,
    private val fileSystem: LocalAnimeSourceFileSystem,
) : AnimeCatalogueSource, UnmeteredSource {

    override val name = "Local Anime Source"
    override val id: Long = ID
    override val lang = "other"
    override fun toString() = name
    override val supportsLatest = true

    override suspend fun getPopularAnime(page: Int) = getSearchAnime(page, "", getFilterList())
    override suspend fun getLatestUpdates(page: Int) = getSearchAnime(page, "", getFilterList())

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage = withIOContext {
        val animeDirs = fileSystem.getFilesInBaseDirectory()
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }

        val animes = animeDirs.map { animeDir ->
            SAnime.create().apply {
                title = animeDir.name.orEmpty()
                url = animeDir.name.orEmpty()
            }
        }

        AnimesPage(animes.toList(), false)
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int) = fetchSearchAnime(page, "", getFilterList())

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int) = fetchSearchAnime(page, "", getFilterList())

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return runBlocking {
            Observable.just(getSearchAnime(page, query, filters))
        }
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()

    override fun getFilterList() = AnimeFilterList(listOf(AnimeOrderBy.Popular(context)))

    override suspend fun getVideoList(episode: SEpisode): List<Video> = throw UnsupportedOperationException("Unused")

    companion object {
        const val ID = 0L
    }
}

fun Anime.isLocal(): Boolean = source == LocalAnimeSource.ID
fun AnimeSource.isLocal(): Boolean = id == LocalAnimeSource.ID
