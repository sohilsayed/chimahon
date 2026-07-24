package eu.kanade.domain.episode.model

import eu.kanade.tachiyomi.data.database.models.EpisodeImpl
import eu.kanade.tachiyomi.source.model.SEpisode
import tachiyomi.domain.episode.model.Episode
import eu.kanade.tachiyomi.data.database.models.Episode as DbEpisode

fun Episode.toSEpisode(): SEpisode {
    return SEpisode.create().also {
        it.url = url
        it.name = name
        it.summary = summary
        it.preview_url = previewUrl
        it.date_upload = dateUpload
        it.episode_number = episodeNumber.toFloat()
        it.scanlator = scanlator
    }
}

fun Episode.copyFromSEpisode(sEpisode: SEpisode): Episode {
    return this.copy(
        name = sEpisode.name,
        summary = sEpisode.summary?.ifBlank { null },
        previewUrl = sEpisode.preview_url?.ifBlank { null },
        url = sEpisode.url,
        dateUpload = sEpisode.date_upload,
        episodeNumber = sEpisode.episode_number.toDouble(),
        scanlator = sEpisode.scanlator?.ifBlank { null },
    )
}

fun Episode.toDbEpisode(): DbEpisode = EpisodeImpl().also {
    it.id = id
    it.anime_id = animeId
    it.url = url
    it.name = name
    it.summary = summary
    it.preview_url = previewUrl
    it.scanlator = scanlator
    it.seen = seen
    it.bookmark = bookmark
    it.fillermark = fillermark
    it.last_second_seen = lastSecondSeen
    it.total_seconds = totalSeconds
    it.date_fetch = dateFetch
    it.date_upload = dateUpload
    it.episode_number = episodeNumber.toFloat()
    it.source_order = sourceOrder.toInt()
    it.last_modified = lastModifiedAt
}
