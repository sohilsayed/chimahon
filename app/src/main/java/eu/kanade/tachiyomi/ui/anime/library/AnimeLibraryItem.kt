package eu.kanade.tachiyomi.ui.anime.library

import tachiyomi.domain.animesource.service.AnimeSourceManager
import tachiyomi.domain.library.model.LibraryAnime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class AnimeLibraryItem(
    val libraryAnime: LibraryAnime,
    var downloadCount: Long = -1,
    var unseenCount: Long = -1,
    var isLocal: Boolean = false,
    var sourceLanguage: String = "",
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) {
    fun matches(constraint: String): Boolean {
        val sourceName by lazy {
            sourceManager.getOrStub(libraryAnime.anime.source).name
        }
        return libraryAnime.anime.title.contains(constraint, true) ||
            (libraryAnime.anime.author?.contains(constraint, true) ?: false) ||
            (libraryAnime.anime.artist?.contains(constraint, true) ?: false) ||
            (libraryAnime.anime.description?.contains(constraint, true) ?: false) ||
            constraint.split(",").map { it.trim() }.all { subconstraint ->
                checkNegatableConstraint(subconstraint) {
                    sourceName.contains(it, true) ||
                        (libraryAnime.anime.genre?.any { genre -> genre.equals(it, true) } ?: false)
                }
            }
    }

    private fun checkNegatableConstraint(
        constraint: String,
        predicate: (String) -> Boolean,
    ): Boolean {
        return if (constraint.startsWith("-")) {
            !predicate(constraint.substringAfter("-").trimStart())
        } else {
            predicate(constraint)
        }
    }
}
