package tachiyomi.source.local.entries.anime

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rx.Observable
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.service.EpisodeRecognition
import tachiyomi.source.local.filter.anime.AnimeOrderBy
import tachiyomi.source.local.image.anime.LocalAnimeBackgroundManager
import tachiyomi.source.local.image.anime.LocalAnimeCoverManager
import tachiyomi.source.local.io.ArchiveAnime
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem
import uy.kohesive.injekt.injectLazy

actual class LocalAnimeSource(
    private val context: Context,
    private val fileSystem: LocalAnimeSourceFileSystem,
    private val coverManager: LocalAnimeCoverManager,
    private val backgroundManager: LocalAnimeBackgroundManager,
) : AnimeCatalogueSource, UnmeteredSource {

    private val json: Json by injectLazy()

    private val popularFilters = AnimeFilterList(AnimeOrderBy.Popular(context))
    private val latestFilters = AnimeFilterList(AnimeOrderBy.Latest(context))

    override val name = "Local anime"
    override val id: Long = ID
    override val lang = "other"
    override val supportsLatest = true

    override fun toString() = name

    override suspend fun getPopularAnime(page: Int) = getSearchAnime(page, "", popularFilters)

    override suspend fun getLatestUpdates(page: Int) = getSearchAnime(page, "", latestFilters)

    override suspend fun getSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage = withIOContext {
        var directories = fileSystem.getFilesInBaseDirectory()
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }
            .filter { query.isBlank() || it.name.orEmpty().contains(query, ignoreCase = true) }

        filters.filterIsInstance<AnimeOrderBy>().firstOrNull()?.state?.let { selection ->
            directories = when (selection.index) {
                1 -> if (selection.ascending) {
                    directories.sortedBy(UniFile::lastModified)
                } else {
                    directories.sortedByDescending(UniFile::lastModified)
                }
                else -> if (selection.ascending) {
                    directories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                } else {
                    directories.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                }
            }
        }

        AnimesPage(directories.map(::toAnime), false)
    }

    private fun toAnime(directory: UniFile): SAnime {
        val animeUrl = directory.name.orEmpty()
        return SAnime.create().apply {
            url = animeUrl
            title = animeUrl
            fetch_type = FetchType.Episodes
            thumbnail_url = coverManager.find(animeUrl)?.uri?.toString()
            background_url = backgroundManager.find(animeUrl)?.uri?.toString()
            initialized = false
        }
    }

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = withIOContext {
        anime.thumbnail_url = coverManager.find(anime.url)?.uri?.toString() ?: anime.thumbnail_url
        anime.background_url = backgroundManager.find(anime.url)?.uri?.toString() ?: anime.background_url

        fileSystem.getFilesInAnimeDirectory(anime.url)
            .firstOrNull {
                it.isFile &&
                    it.extension.equals("json", ignoreCase = true) &&
                    it.nameWithoutExtension.equals("details", ignoreCase = true)
            }
            ?.let { detailsFile ->
                runCatching {
                    detailsFile.openInputStream().bufferedReader().use { reader ->
                        json.decodeFromString<LocalAnimeDetails>(reader.readText())
                    }
                }.getOrNull()?.let { details ->
                    details.title?.takeIf(String::isNotBlank)?.let { anime.title = it }
                    anime.author = details.author
                    anime.artist = details.artist
                    anime.description = details.description
                    anime.genre = details.genre?.joinToString()
                    details.status?.let { anime.status = it }
                }
            }

        anime.initialized = true
        anime
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = withIOContext {
        fileSystem.getAnimeDirectory(anime.url)
            ?.videoFiles()
            .orEmpty()
            .map { (relativePath, file) ->
                SEpisode.create().apply {
                    url = "${anime.url}/$relativePath"
                    name = relativePath.substringBeforeLast('.')
                    date_upload = file.lastModified()
                    episode_number = EpisodeRecognition.parseEpisodeNumber(
                        anime.title,
                        name,
                    ).toFloat()
                }
            }
            .sortedWith(
                compareByDescending<SEpisode> { it.episode_number }
                    .thenByDescending(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> = withIOContext {
        val file = episode.url
            .split('/')
            .filter(String::isNotBlank)
            .fold(fileSystem.getBaseDirectory()) { directory, segment -> directory?.findFile(segment) }
            ?: return@withIOContext emptyList()

        listOf(Video(file.uri.toString(), "Local source: ${episode.url}"))
    }

    override fun getFilterList() = AnimeFilterList(AnimeOrderBy.Popular(context))

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int) = fetchSearchAnime(page, "", popularFilters)

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int) = fetchSearchAnime(page, "", latestFilters)

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> {
        return runBlocking { Observable.just(getSearchAnime(page, query, filters)) }
    }

    private fun UniFile.videoFiles(prefix: String = ""): List<Pair<String, UniFile>> {
        return listFiles().orEmpty().flatMap { file ->
            val name = file.name.orEmpty()
            if (name.startsWith('.')) {
                emptyList()
            } else {
                val relativePath = if (prefix.isEmpty()) name else "$prefix/$name"
                when {
                    file.isDirectory -> file.videoFiles(relativePath)
                    ArchiveAnime.isSupported(file) -> listOf(relativePath to file)
                    else -> emptyList()
                }
            }
        }
    }

    @Serializable
    private data class LocalAnimeDetails(
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Int? = null,
    )

    companion object {
        const val ID = 0L
    }
}

fun Anime.isLocal(): Boolean = source == LocalAnimeSource.ID
fun AnimeSource.isLocal(): Boolean = id == LocalAnimeSource.ID
