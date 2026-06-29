package eu.kanade.tachiyomi.ui.youtube

import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.ui.browse.animesource.AlwaysVisibleAnimeSource
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourceScreenProvider
import rx.Observable

object YouTubeSource : AnimeCatalogueSource, AnimeSourceScreenProvider, AlwaysVisibleAnimeSource {
    const val ID = 897438624915173743L

    override val id = ID
    override val name = "YouTube"
    override val lang = "all"
    override val supportsLatest = false

    private val emptyPage = AnimesPage(emptyList(), false)

    override suspend fun getPopularAnime(page: Int) = emptyPage

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList) = emptyPage

    override suspend fun getLatestUpdates(page: Int) = emptyPage

    override fun getFilterList() = AnimeFilterList()

    override fun createBrowseScreen(listingQuery: String?) = YouTubeBrowserScreen()

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPopularAnime"),
    )
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = Observable.just(emptyPage)

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getSearchAnime"),
    )
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        Observable.just(emptyPage)

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getLatestUpdates"),
    )
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = Observable.just(emptyPage)
}

val YouTubeExtension = AnimeExtension.Installed(
    name = "YouTube",
    pkgName = "app.chimahon.youtube",
    versionName = "1.0.0",
    versionCode = 1,
    libVersion = 14.0,
    lang = "all",
    isNsfw = false,
    isTorrent = false,
    signatureHash = "",
    pkgFactory = null,
    sources = listOf(YouTubeSource),
    icon = null,
    hasUpdate = false,
    isObsolete = false,
    isShared = false,
    repoUrl = null,
)
