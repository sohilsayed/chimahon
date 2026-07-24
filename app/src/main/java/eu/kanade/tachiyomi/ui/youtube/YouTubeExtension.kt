package eu.kanade.tachiyomi.ui.youtube

import android.app.Application
import eu.kanade.tachiyomi.animeextension.model.AnimeExtension
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.ui.browse.animesource.AlwaysVisibleAnimeSource
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourceScreenProvider
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object YouTubeSource : AnimeHttpSource(), AnimeSourceScreenProvider, AlwaysVisibleAnimeSource {
    const val ID = 897438624915173743L

    override val id = ID
    override val name = "YouTube"
    override val lang = "all"
    override val supportsLatest = true

    override val baseUrl = "https://m.youtube.com/"
    const val WATCH_PREFIX = "watch?v="
    const val CHANNEL_PREFIX = "channel/"
    const val SUBSCRIPTIONS_SUFFIX = "feed/subscriptions"

    override val supportsRelatedAnime = false
    override val disableRelatedAnimeBySearch = true
    override val disableRelatedAnime = true

    override fun createBrowseScreen(listingQuery: String?, targetUrl: String?) = YouTubeBrowserScreen(listingQuery, targetUrl)

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = withIOContext()
    {
        val channelId = anime.url.removePrefix(CHANNEL_PREFIX)
        val channelMetadata = YouTubeResolver.resolveChannel(channelId)

        anime.title = channelMetadata.name
        anime.description = channelMetadata.description
        anime.thumbnail_url = channelMetadata?.avatarUrl
        anime.background_url = channelMetadata?.bannerUrl

        anime
    }

    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get()
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId = Injekt.get()

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = withIOContext() {
        val channelId = anime.url.removePrefix(CHANNEL_PREFIX)

        val channelVideos = YouTubeResolver.resolveVideosFromTab(channelId, "videos") +
                            YouTubeResolver.resolveVideosFromTab(channelId, "shorts") +
                            YouTubeResolver.resolveVideosFromTab(channelId, "livestreams")

        val videos = run {
            val animeEntry = getAnimeByUrlAndSourceId.await(anime.url, id)
            if (animeEntry != null) {
                val episodes = getEpisodesByAnimeId.await(animeEntry.id)
                if (episodes != null) {
                    val existingEpisodes = episodes.map { episode ->
                        val videoId = episode.url.removePrefix(WATCH_PREFIX)

                        YouTubeVideoItem(
                            id = videoId,
                            name = episode.name,
                            url = episode.url,
                            duration = episode.totalSeconds,
                            uploadDate = episode.dateUpload,
                            videoType = episode.scanlator,
                            thumbnailUrl = episode.previewUrl ?: "",
                            viewCount = 0L,
                            shortDescription = episode.summary,
                        )
                    }

                    val mergedVideos = (channelVideos + existingEpisodes)
                        .distinctBy { it.id }

                    mergedVideos
                } else {
                    channelVideos
                }
            } else {
                channelVideos
            }
        }

        videos.map { video ->
            SEpisode.create().apply {
                name = video.name
                url = video.url
                date_upload = video.uploadDate
                scanlator = video.videoType
                preview_url = video.thumbnailUrl
                summary = video.shortDescription
                episode_number = 0.0f
            }
        }
    }

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val prefs = YouTubePreferences(Injekt.get<Application>())
        val videoId = episode.url.removePrefix(WATCH_PREFIX)
        val videoMetadata = YouTubeResolver.resolveVideo(videoId, prefs.preferredQuality)
        if (videoMetadata.videoStreams.isEmpty())
            return emptyList()

        val hoster = Hoster(hosterName = "YouTube", videoList = videoMetadata.videoStreams)
        return listOf(hoster)
    }

    // UNSUPPORTED
    override fun popularAnimeRequest(page: Int): Request {
        throw UnsupportedOperationException("YouTubeSource popularAnimeRequest is not supported")
    }

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        throw UnsupportedOperationException("YouTubeSource searchAnimeRequest is not supported")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException("YouTubeSource latestUpdatesRequest is not supported")
    }

    override fun animeDetailsParse(response: Response): SAnime {
        throw UnsupportedOperationException("YouTubeSource animeDetailsParse is not supported")
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        throw UnsupportedOperationException("YouTubeSource episodeListParse is not supported")
    }

    override fun hosterListParse(response: Response): List<Hoster> {
        throw UnsupportedOperationException("YouTubeSource hosterListParse is not supported")
    }

    override fun videoListParse(
        response: Response,
        hoster: Hoster,
    ): List<Video> {
        throw UnsupportedOperationException("YouTubeSource videoListParse is not supported")
    }

    override fun videoUrlParse(response: Response): String {
        throw UnsupportedOperationException("YouTubeSource videoUrlParse is not supported")
    }

    // EMPTY
    private val emptyPage = AnimesPage(emptyList(), false)
    override suspend fun getPopularAnime(page: Int) = emptyPage
    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList) = emptyPage
    override suspend fun getLatestUpdates(page: Int) = emptyPage
    override fun getFilterList() = AnimeFilterList()

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getPopularAnime"),
    )
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = Observable.just(emptyPage)
    override fun popularAnimeParse(response: Response): AnimesPage = emptyPage

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getSearchAnime"),
    )
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        Observable.just(emptyPage)

    override fun searchAnimeParse(response: Response): AnimesPage = emptyPage

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getLatestUpdates"),
    )
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = Observable.just(emptyPage)
    override fun latestUpdatesParse(response: Response): AnimesPage = emptyPage
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
