package eu.kanade.tachiyomi.util.episode

import tachiyomi.domain.episode.model.Episode

fun List<Episode>.getNextUnseen(): Episode? {
    return this.firstOrNull { !it.seen }
}
