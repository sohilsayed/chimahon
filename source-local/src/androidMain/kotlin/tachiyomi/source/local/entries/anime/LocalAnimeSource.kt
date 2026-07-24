package tachiyomi.source.local.entries.anime

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import logcat.LogPriority
import rx.Observable
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.extension
import tachiyomi.i18n.MR
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.metadata.tachiyomi.AnimeDetails
import tachiyomi.core.metadata.tachiyomi.EpisodeDetails
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.episode.service.EpisodeRecognition
import tachiyomi.source.local.filter.anime.AnimeOrderBy
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.image.anime.LocalEpisodeThumbnailManager
import tachiyomi.source.local.io.ArchiveAnime
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

actual class LocalAnimeSource(
    private val context: Context,
    private val fileSystem: LocalAnimeSourceFileSystem,
    private val coverManager: LocalAnimeCoverManager,
    private val backgroundManager: LocalAnimeBackgroundManager,
    private val thumbnailManager: LocalEpisodeThumbnailManager,
    private val fetchTypeManager: LocalAnimeFetchTypeManager,
) : AnimeCatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()

    private val PopularFilters = AnimeFilterList(AnimeOrderBy.Popular(context))

    private val LatestFilters = AnimeFilterList(AnimeOrderBy.Latest(context))

    override val name = context.stringResource(MR.strings.local_anime_source)

    override val id: Long = ID

    override val lang = "other"

    override fun toString() = name

    override val supportsLatest = true

    // Browse related
    override suspend fun getPopularAnime(page: Int) = getSearchAnime(page, "", PopularFilters)

    override suspend fun getLatestUpdates(page: Int) = getSearchAnime(page, "", LatestFilters)

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage = withIOContext {
        val lastModifiedLimit = if (filters === LatestFilters) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }

        var animeDirs = fileSystem.getFilesInBaseDirectory()
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }
            .filter {
                if (lastModifiedLimit == 0L && query.isBlank()) {
                    true
                } else if (lastModifiedLimit == 0L) {
                    it.name.orEmpty().contains(query, ignoreCase = true)
                } else {
                    it.lastModified() >= lastModifiedLimit
                }
            }

        filters.forEach { filter ->
            when (filter) {
                is AnimeOrderBy.Popular -> {
                    animeDirs = if (filter.state!!.ascending) {
                        animeDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                    } else {
                        animeDirs.sortedWith(
                            compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() },
                        )
                    }
                }
                is AnimeOrderBy.Latest -> {
                    animeDirs = if (filter.state!!.ascending) {
                        animeDirs.sortedBy(UniFile::lastModified)
                    } else {
                        animeDirs.sortedByDescending(UniFile::lastModified)
                    }
                }
                else -> {}
            }
        }

        val animes = animeDirs
            .map { animeDir ->
                async {
                    getSAnime(animeDir.name)
                }
            }
            .awaitAll()

        AnimesPage(animes.toList(), false)
    }

    private fun getSAnime(animeDir: String?): SAnime {
        return SAnime.create().apply {
            title = animeDir.orEmpty().substringAfterLast(File.separator)
            url = animeDir.orEmpty()
            fetch_type = fetchTypeManager.find(animeDir.orEmpty())

            coverManager.find(animeDir.orEmpty())?.let {
                thumbnail_url = it.uri.toString()
            }

            backgroundManager.find(animeDir.orEmpty())?.let {
                background_url = it.uri.toString()
            }
        }
    }

    // Old fetch functions
    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int) = fetchSearchAnime(page, "", PopularFilters)

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int) = fetchSearchAnime(page, "", LatestFilters)

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return runBlocking {
            Observable.just(getSearchAnime(page, query, filters))
        }
    }

    // Anime details related
    override suspend fun getAnimeDetails(anime: SAnime): SAnime = withIOContext {
        coverManager.find(anime.url)?.let {
            anime.thumbnail_url = it.uri.toString()
        }

        backgroundManager.find(anime.url)?.let {
            anime.background_url = it.uri.toString()
        }

        fileSystem.getFilesInAnimeDirectory(anime.url)
            .firstOrNull { it.extension == "json" && it.nameWithoutExtension == "details" }
            ?.let { file ->
                json.decodeFromStream<AnimeDetails>(file.openInputStream()).run {
                    title?.let { anime.title = it }
                    author?.let { anime.author = it }
                    artist?.let { anime.artist = it }
                    description?.let { anime.description = it }
                    genre?.let { anime.genre = it.joinToString() }
                    status?.let { anime.status = it }
                }
            }

        anime
    }

    // Seasons
    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = withIOContext {
        val animeDirs = fileSystem.getFilesInAnimeDirectory(anime.url)
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }

        animeDirs
            .map { animeDir ->
                async {
                    val url = animeDir.name?.let { season ->
                        buildString {
                            append(anime.url)
                            append(File.separator)
                            append(season)
                        }
                    }
                    getSAnime(url)
                }
            }
            .awaitAll()
            .toList()
    }

    // Episodes
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = withIOContext {
        val episodesData = fileSystem.getFilesInAnimeDirectory(anime.url)
            .firstOrNull {
                it.extension == "json" && it.nameWithoutExtension == "episodes"
            }?.let { file ->
                json.decodeFromStream<List<EpisodeDetails>>(file.openInputStream())
            }

        val episodes = fileSystem.getFilesInAnimeDirectory(anime.url)
            .filterNot { it.name.orEmpty().startsWith('.') }
            .filter { ArchiveAnime.isSupported(it) }
            .map { episodeFile ->
                SEpisode.create().apply {
                    url = "${anime.url}/${episodeFile.name}"
                    name = episodeFile.nameWithoutExtension.orEmpty()
                    date_upload = episodeFile.lastModified()

                    val episodeNumber = EpisodeRecognition.parseEpisodeNumber(
                        anime.title,
                        this.name,
                        this.episode_number.toDouble(),
                    ).toFloat()
                    episode_number = episodeNumber

                    episodesData?.also { dataList ->
                        dataList.firstOrNull { it.episode_number.equalsTo(episodeNumber) }?.also { data ->
                            data.name?.also { name = it }
                            data.date_upload?.also { date_upload = parseDate(it) }
                            scanlator = data.scanlator
                            summary = data.summary
                        }
                    }

                    if (this.preview_url == null) {
                        try {
                            val tempFileSuffix = anime.title + this.name + DEFAULT_THUMBNAIL_NAME
                            val updateThumbnail: (InputStream) -> Unit = { thumbnailManager.update(anime, this, it) }
                            updateImageFromVideo(this, anime, tempFileSuffix, updateThumbnail)
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR) { "Couldn't extract thumbnail from video: $e" }
                        }
                    }
                }
            }
            .sortedWith { e1, e2 ->
                val e = e2.episode_number.compareTo(e1.episode_number)
                if (e == 0) e2.name.compareToCaseInsensitiveNaturalOrder(e1.name) else e
            }

        if (anime.thumbnail_url == null || coverManager.find(anime.url) == null) {
            try {
                episodes.lastOrNull()?.let { episode ->
                    val tempFileSuffix = anime.title + DEFAULT_COVER_NAME
                    val updateCover: (InputStream) -> Unit = { coverManager.update(anime, it) }
                    updateImageFromVideo(episode, anime, tempFileSuffix, updateCover)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Couldn't extract cover from video: $e" }
            }
        }

        if (anime.background_url == null || backgroundManager.find(anime.url) == null) {
            try {
                episodes.lastOrNull()?.let { episode ->
                    val tempFileSuffix = anime.title + DEFAULT_BACKGROUND_NAME
                    val updateBackground: (InputStream) -> Unit = { backgroundManager.update(anime, it) }
                    updateImageFromVideo(episode, anime, tempFileSuffix, updateBackground)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Couldn't extract background from video: $e" }
            }
        }

        episodes
    }

    private fun parseDate(isoDate: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(isoDate)?.time ?: 0L
    }

    private fun Float.equalsTo(other: Float): Boolean {
        return abs(this - other) < 0.0001
    }

    // Filters
    override fun getFilterList() = AnimeFilterList(AnimeOrderBy.Popular(context))

    // Unused stuff
    override suspend fun getVideoList(episode: SEpisode): List<Video> = throw UnsupportedOperationException("Unused")

    private fun updateImageFromVideo(
        episode: SEpisode,
        anime: SAnime,
        tempFileSuffix: String,
        updateImage: (InputStream) -> Unit,
    ) {
        val tempFile = File.createTempFile("tmp_", tempFileSuffix)
        val outFile = tempFile.path

        val episodeName = episode.url.split('/', limit = 2).last()
        val animeDir = fileSystem.getAnimeDirectory(anime.url) ?: return
        val episodeFile = animeDir.findFile(episodeName) ?: return
        val filePath = episodeFile.filePath ?: return

        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val durationStr = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
            )
            val durationMs = durationStr?.toLongOrNull() ?: return
            val frameAtMs = durationMs / 2

            val bitmap = retriever.getFrameAtTime(
                frameAtMs * 1000,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: return

            tempFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }

            if (tempFile.length() > 0L) {
                tempFile.inputStream().use(updateImage)
            }
        } finally {
            retriever.release()
            tempFile.delete()
        }
    }

    companion object {
        const val ID = 0L
        const val HELP_URL = "https://aniyomi.org/help/guides/local-anime/"

        private const val DEFAULT_COVER_NAME = "cover.jpg"
        private const val DEFAULT_BACKGROUND_NAME = "background.jpg"
        private const val DEFAULT_THUMBNAIL_NAME = "thumbnail.jpg"
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)
    }
}

fun Anime.isLocal(): Boolean = source == LocalAnimeSource.ID
fun AnimeSource.isLocal(): Boolean = id == LocalAnimeSource.ID
