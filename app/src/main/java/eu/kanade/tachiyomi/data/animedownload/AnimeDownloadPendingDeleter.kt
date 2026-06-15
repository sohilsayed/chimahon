package eu.kanade.tachiyomi.data.animedownload

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDownloadPendingDeleter(
    context: Context,
    private val json: Json = Injekt.get(),
) {

    private val preferences = context.getSharedPreferences("anime_episodes_to_delete", Context.MODE_PRIVATE)

    fun enqueueEpisodesToDelete(episodes: List<Episode>, anime: Anime) {
        val existingEntry = preferences.getString(anime.id.toString(), null)
        val existingEpisodes = existingEntry?.let { decodeEntry(it)?.episodes } ?: emptyList()
        val existingIds = existingEpisodes.map { it.id }.toSet()

        val merged = existingEpisodes + episodes
            .filter { it.id !in existingIds }
            .map { EpisodeEntry(it.id, it.name, it.scanlator) }
        val entry = Entry(merged, AnimeEntry(anime.id, anime.title, anime.source))
        preferences.edit {
            putString(anime.id.toString(), json.encodeToString(entry))
        }
    }

    fun getPendingEpisodes(anime: Anime): List<Episode> {
        val entry = preferences.getString(anime.id.toString(), null)?.let { decodeEntry(it) }
            ?: return emptyList()
        return entry.episodes.map { ep ->
            Episode.create().copy(id = ep.id, name = ep.name, scanlator = ep.scanlator)
        }
    }

    fun removePendingDelete(anime: Anime) {
        preferences.edit {
            remove(anime.id.toString())
        }
    }

    private fun decodeEntry(string: String): Entry? {
        return try {
            json.decodeFromString<Entry>(string)
        } catch (_: Exception) {
            null
        }
    }

    @Serializable
    private data class Entry(
        val episodes: List<EpisodeEntry>,
        val anime: AnimeEntry,
    )

    @Serializable
    private data class EpisodeEntry(
        val id: Long,
        val name: String,
        val scanlator: String?,
    )

    @Serializable
    private data class AnimeEntry(
        val id: Long,
        val title: String,
        val source: Long,
    )
}
