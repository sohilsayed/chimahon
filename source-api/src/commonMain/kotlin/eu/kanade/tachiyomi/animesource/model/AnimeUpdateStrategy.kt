package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.source.model.UpdateStrategy

@Suppress("UNUSED")
enum class AnimeUpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}

fun AnimeUpdateStrategy.toUpdateStrategy(): UpdateStrategy {
    return UpdateStrategy.entries[ordinal]
}

fun UpdateStrategy.toAnimeUpdateStrategy(): AnimeUpdateStrategy {
    return AnimeUpdateStrategy.entries[ordinal]
}
