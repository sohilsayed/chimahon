package eu.kanade.tachiyomi.ui.browse.source

import tachiyomi.core.common.storage.nameWithoutExtension

object MangaImportUtil {
    // Regex to find volume/chapter markers to strip them from the base title (supports English and Japanese)
    private val unwanted = Regex("""(?:\b(?:v|ver|vol|version|volume|season|s|ch|chapter)[^a-z]?[0-9]+)|(?:第?\s*[0-9]+\s*(?:巻|話|章))""", RegexOption.IGNORE_CASE)
    
    // Illegal characters for filenames on most systems (Android is Linux based but SD cards might be FAT32/exFAT)
    private val illegalCharacters = Regex("""[\\/:*?"<>|]""")

    /**
     * Extracts the base series title by removing volume/chapter markers.
     * Example: "One Piece Vol. 1" -> "One Piece"
     */
    fun getBaseTitle(fileName: String): String {
        val nameWithoutExt = fileName.substringBeforeLast(".")
        return unwanted.replace(nameWithoutExt, "")
            .trim()
            .removeSuffix("-")
            .removeSuffix("_")
            .trim()
    }

    /**
     * Replaces illegal characters with underscores and trims the result.
     */
    fun getSafeFolderName(title: String): String {
        return illegalCharacters.replace(title, "_").trim()
    }
}
