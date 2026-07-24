package eu.kanade.tachiyomi.data.animedownload

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDownloadPendingDeleter(
    context: Context,
    private val json: Json = Injekt.get(),
) {

    private val preferences = context.getSharedPreferences("anime_episodes_to_delete", Context.MODE_PRIVATE)

    private var lastAddedEntry: Entry? = null

    @Synchronized
    fun addEpisodes(episodes: List<Episode>, anime: Anime) {
        val lastEntry = lastAddedEntry

        val newEntry = if (lastEntry != null && lastEntry.anime.id == anime.id) {
            val newEpisodes = lastEntry.episodes.addUniqueById(episodes)
            if (newEpisodes.size == lastEntry.episodes.size) return
            lastEntry.copy(episodes = newEpisodes)
        } else {
            val existingEntry = preferences.getString(anime.id.toString(), null)
            if (existingEntry != null) {
                val savedEntry = json.decodeFromString<Entry>(existingEntry)
                val newEpisodes = savedEntry.episodes.addUniqueById(episodes)
                if (newEpisodes.size == savedEntry.episodes.size) return
                savedEntry.copy(episodes = newEpisodes)
            } else {
                Entry(episodes.map { it.toEntry() }, anime.toEntry())
            }
        }

        val json = json.encodeToString(newEntry)
        preferences.edit {
            putString(newEntry.anime.id.toString(), json)
        }
        lastAddedEntry = newEntry
    }

    @Synchronized
    fun getPendingEpisodes(): Map<Anime, List<Episode>> {
        val entries = decodeAll()
        preferences.edit { clear() }
        lastAddedEntry = null

        return entries.associate { (episodes, anime) ->
            anime.toModel() to episodes.map { it.toModel() }
        }
    }

    private fun decodeAll(): List<Entry> {
        return preferences.all.values.mapNotNull { rawEntry ->
            try {
                (rawEntry as? String)?.let { json.decodeFromString<Entry>(it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun List<EpisodeEntry>.addUniqueById(episodes: List<Episode>): List<EpisodeEntry> {
        val newList = toMutableList()
        for (episode in episodes) {
            if (none { it.id == episode.id }) {
                newList.add(episode.toEntry())
            }
        }
        return newList
    }

    private fun Anime.toEntry() = AnimeEntry(id, url, ogTitle, source)
    private fun Episode.toEntry() = EpisodeEntry(id, url, name, scanlator)
    private fun AnimeEntry.toModel() = Anime.create().copy(url = url, ogTitle = ogTitle, source = source, id = id)
    private fun EpisodeEntry.toModel() = Episode.create().copy(id = id, url = url, name = name, scanlator = scanlator)

    @Serializable
    private data class Entry(
        val episodes: List<EpisodeEntry>,
        val anime: AnimeEntry,
    )

    @Serializable
    private data class EpisodeEntry(
        val id: Long,
        val url: String,
        val name: String,
        val scanlator: String? = null,
    )

    @Serializable
    private data class AnimeEntry(
        val id: Long,
        val url: String,
        val ogTitle: String,
        val source: Long,
    )
}
