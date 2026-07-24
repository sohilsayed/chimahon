package eu.kanade.tachiyomi.ui.youtube

import eu.kanade.tachiyomi.animesource.model.Video

private const val DISABLE_EXTERNAL_SUBTITLE_LOOKUP = "externalSubtitleLookup=false"

fun Video.withoutExternalSubtitleLookup(): Video {
    if (!allowsExternalSubtitleLookup()) return this
    val metadata = internalData
        .lineSequence()
        .filter { it.isNotBlank() }
        .plus(DISABLE_EXTERNAL_SUBTITLE_LOOKUP)
        .joinToString("\n")

    return copy(
        internalData = metadata,
        initialized = initialized,
        videoPageUrl = videoPageUrl,
    )
}

fun Video.allowsExternalSubtitleLookup(): Boolean {
    return internalData
        .lineSequence()
        .none { it.trim().equals(DISABLE_EXTERNAL_SUBTITLE_LOOKUP, ignoreCase = true) }
}
