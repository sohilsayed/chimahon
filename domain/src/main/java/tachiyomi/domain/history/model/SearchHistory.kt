package tachiyomi.domain.history.model

data class SearchHistory(
    val id: Long,
    val scope: String,
    val query: String,
    val lastSearchedAt: Long,
) {
    companion object {
        const val SCOPE_ANIME_MANGA = "ANIME_MANGA_SEARCH"
        const val SCOPE_EXTENSION_MIGRATE = "EXTENSION_MIGRATE_SEARCH"
        const val SCOPE_SETTINGS = "SETTINGS_SEARCH"
        const val SCOPE_DICTIONARY = "DICTIONARY_SEARCH"
        const val MAX_ITEMS_PER_SCOPE = 20L
    }
}
