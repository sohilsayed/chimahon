package chimahon.dictionary

import chimahon.anki.AnkiProfile
import chimahon.anki.AnkiProfileStore

/**
 * Resolves which [AnkiProfile] to use for a given reader session.
 *
 * Resolution priority (highest first):
 * 1. Manga override  – user pinned a specific profile to this manga ID.
 * 2. Source override – user pinned a specific profile to this source ID.
 * 3. Language match  – first profile in the list whose [AnkiProfile.languageCode]
 *    matches the source's language code (non-empty, non-"all" sources only).
 * 4. Global active   – whatever is currently selected in Settings.
 *
 * Overrides are stored as lightweight SharedPreferences strings (not SQLite),
 * keyed by [mangaOverrideKey] / [sourceOverrideKey].  The store lambdas are
 * supplied by the caller so this class stays free of Android dependencies.
 *
 * @param profileStore       the shared [AnkiProfileStore] singleton
 * @param readMangaOverride  returns the raw profile-id string stored for [mangaId], or ""
 * @param readSourceOverride returns the raw profile-id string stored for [sourceId], or ""
 */
class DictionaryProfileResolver(
    private val profileStore: AnkiProfileStore,
    private val readMangaOverride: (mangaId: Long) -> String,
    private val readSourceOverride: (sourceId: Long) -> String,
) {

    /**
     * Resolve the profile for a reader session.
     *
     * @param mangaId   ID of the manga being read (0 if unknown / novel context)
     * @param sourceId  ID of the source (0 if unknown)
     * @param sourceLang BCP-47 language code from the source, e.g. "ja", "all", "" (unknown)
     * @return the resolved [AnkiProfile]; never null (falls back to first available)
     */
    fun resolve(
        mangaId: Long = 0L,
        sourceId: Long = 0L,
        sourceLang: String = "",
    ): AnkiProfile {
        val profiles = profileStore.getProfiles()
        if (profiles.isEmpty()) return profileStore.getActiveProfile()

        // 1. Manga-level override
        if (mangaId != 0L) {
            val overrideId = readMangaOverride(mangaId)
            val found = profiles.firstOrNull { it.id == overrideId }
            if (found != null) return found
        }

        // 2. Source-level override
        if (sourceId != 0L) {
            val overrideId = readSourceOverride(sourceId)
            val found = profiles.firstOrNull { it.id == overrideId }
            if (found != null) return found
        }

        // 3. Language auto-match (skip for "all" / empty / unknown)
        if (sourceLang.isNotBlank() && sourceLang != "all") {
            val langMatch = profiles.firstOrNull {
                it.languageCode.equals(sourceLang, ignoreCase = true)
            }
            if (langMatch != null) return langMatch
        }

        // 4. Global active profile
        return profileStore.getActiveProfile()
    }

    companion object {
        /** SharedPreferences key for a manga-level profile override. */
        fun mangaOverrideKey(mangaId: Long) = "pref_dict_profile_manga_$mangaId"

        /** SharedPreferences key for a source-level profile override. */
        fun sourceOverrideKey(sourceId: Long) = "pref_dict_profile_source_$sourceId"
    }
}
