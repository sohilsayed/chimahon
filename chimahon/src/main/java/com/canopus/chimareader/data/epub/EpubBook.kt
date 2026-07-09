package com.canopus.chimareader.data.epub

import android.util.Log
import org.jsoup.Jsoup
import java.io.File

data class EpubBook(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
    val coverPath: String? = null,
    val metadata: EpubMetadata = EpubMetadata(),
    val manifest: EpubManifest = EpubManifest(),
    val spine: EpubSpine = EpubSpine(),
    val tableOfContents: List<TocEntry> = emptyList(),
    val contentDirectory: String = "",
    val zipPath: String = "",
    val extractedDir: File? = null,
) {
    val coverHref: String?
        get() {
            val coverId = metadata.coverId ?: return null
            val manifestItem = manifest.items[coverId] ?: return null
            // Prepend content directory if present
            return if (contentDirectory.isNotEmpty()) {
                "$contentDirectory${manifestItem.href}"
            } else {
                manifestItem.href
            }
        }

    val linearSpineItems: List<SpineItem>
        get() = spine.items.filter { it.linear }

    fun getChapterHref(index: Int): String? {
        val spineItem = linearSpineItems.getOrNull(index) ?: return null
        val manifestItem = manifest.items[spineItem.idref] ?: return null
        // Prepend content directory if present (e.g., "item/xhtml/p-001.xhtml")
        return if (contentDirectory.isNotEmpty()) {
            "$contentDirectory${manifestItem.href}"
        } else {
            manifestItem.href
        }
    }

    /**
     * Same label as the novel reader chapter list / app bar:
     * TOC title → file name → "Chapter {spineIndex}" (0-based, matches list fallback).
     */
    fun getChapterTitle(chapterIndex: Int): String {
        val href = getChapterHref(chapterIndex)
            ?: return "Chapter $chapterIndex"
        findTocLabel(tableOfContents, href)?.let { return it }
        return href.substringAfterLast("/").substringBefore(".")
    }

    /** Depth-first TOC list (same order as reader chapter sheet / chapterStarts). */
    fun flattenedToc(indentLabels: Boolean = false): List<TocEntry> {
        val flat = mutableListOf<TocEntry>()
        fun flatten(entries: List<TocEntry>, depth: Int) {
            for (e in entries) {
                val label = if (indentLabels) "  ".repeat(depth) + e.label else e.label
                flat.add(e.copy(label = label))
                flatten(e.children, depth + 1)
            }
        }
        flatten(tableOfContents, 0)
        return flat
    }

    fun getSpineIndexForHref(href: String): Int? {
        val decodedHref = java.net.URLDecoder.decode(
            href.substringBefore('#').substringBefore('?'),
            "UTF-8",
        )
        val fileName = decodedHref.substringAfterLast("/")
        for (i in linearSpineItems.indices) {
            val chapterHref = getChapterHref(i) ?: continue
            val chapterFileName = chapterHref.substringAfterLast("/")
            if (chapterHref.endsWith(decodedHref) || chapterFileName == fileName) {
                return i
            }
        }
        return null
    }

    /**
     * 1-based chapter number by TOC order (matches stats [chapterStarts] when TOC exists).
     * Spine index overcounts front matter / non-TOC pages — map spine → last TOC entry at or before it.
     * No TOC: fall back to 1-based spine position.
     */
    fun tocChapterNumber(spineIndex: Int): Int {
        val flat = flattenedToc()
        if (flat.isEmpty()) return spineIndex + 1

        val tocSpines = flat.mapIndexedNotNull { tocIndex, entry ->
            val href = entry.href ?: return@mapIndexedNotNull null
            val spine = getSpineIndexForHref(href) ?: return@mapIndexedNotNull null
            tocIndex to spine
        }
        if (tocSpines.isEmpty()) return spineIndex + 1

        tocSpines.lastOrNull { it.second == spineIndex }?.let { return it.first + 1 }
        tocSpines.lastOrNull { it.second <= spineIndex }?.let { return it.first + 1 }
        return 1
    }

    private fun findTocLabel(toc: List<TocEntry>, href: String): String? {
        val fileName = href.substringAfterLast("/")
        for (entry in toc) {
            val entryHref = entry.href ?: continue
            if (entryHref.endsWith(fileName) || entryHref.contains(fileName.substringBefore("."))) {
                return entry.label
            }
            val found = findTocLabel(entry.children, href)
            if (found != null) return found
        }
        return null
    }

    // Hoshi Shims
    fun title(): String? = title
    fun spine(): EpubSpine = spine
    fun chapterAbsolutePath(index: UInt): String? {
        val href = getChapterHref(index.toInt()) ?: return null
        val baseDir = extractedDir ?: return null
        return File(baseDir, href).absolutePath
    }

    /**
     * Returns the cached image URL for an image-only spine item.
     * The URL was resolved once during book parsing and stored in [SpineItem.imageUrl].
     */
    fun getImageUrl(index: Int): String? =
        linearSpineItems.getOrNull(index)
            ?.takeIf { it.type == SpineItemType.IMAGE_ONLY }
            ?.imageUrl

    // Cache to prevent recalculating Chapter Character length
    private val chapterLengthCache = mutableMapOf<Int, Int>()

    /**
     * Calculates the exploredCharCount of a specific chapter matching TTSU logic
     */
    fun getChapterCharacters(index: Int): Int {
        if (chapterLengthCache.containsKey(index)) return chapterLengthCache[index]!!
        
        val spineType = linearSpineItems.getOrNull(index)?.type
        if (spineType == SpineItemType.IMAGE_ONLY) {
            chapterLengthCache[index] = 0
            return 0
        }

        return try {
            val content = EpubParser().parseChapter(this, index) ?: return 0
            
            // Use the same approach as Hoshi Reader's characterCount():
            // 1. Extract <body>...</body>
            // 2. Strip <rt> (furigana), <script>, <style>, then ALL tags
            // 3. Decode HTML entities
            // 4. Apply TTSU character filter regex
            var text = content
            val bodyMatch = Regex("(?s)<body.*?</body>").find(text)
            if (bodyMatch != null) {
                text = bodyMatch.value
            }
            // Strip <rt>...</rt> (furigana annotations)
            text = text.replace(Regex("(?s)<rt>.*?</rt>"), "")
            // Strip <script>...</script> and <style>...</style>
            text = text.replace(Regex("(?s)<(script|style)[^>]*>.*?</\\1>"), "")
            // Strip all HTML tags
            text = text.replace(Regex("<[^>]+>"), "")
            // Decode common HTML entities
            text = text.replace("&nbsp;", " ")
            text = text.replace("&amp;", "&")
            text = text.replace("&lt;", "<")
            text = text.replace("&gt;", ">")

            // TTSU character filter (matching Hoshi's regex with \p{Unified_Ideograph} and \p{IsHangul})
            val ttsuRegex = Regex("[^0-9A-Za-z○◯々-〇〻ぁ-ゖゝ-ゞァ-ヺー０-９Ａ-Ｚａ-ｚｦ-ﾝ\\p{IsHan}\\p{IsHangul}]")
            val filteredText = text.replace(ttsuRegex, "")
            
            val charCount = filteredText.codePointCount(0, filteredText.length)
            chapterLengthCache[index] = charCount
            charCount
        } catch(e: Exception) {
            0
        }
    }

    /**
     * Converts an absolute character count back into a 
     * specific chapter index and decimal progress (0.0 to 1.0) for that chapter.
     */
    fun convertCharsToProgress(totalExploredChars: Int): Pair<Int, Double> {
        var unallocatedChars = totalExploredChars
        for (i in 0 until linearSpineItems.size) {
            val chapterChars = getChapterCharacters(i)
            if (unallocatedChars <= chapterChars) {
                // If chapter has 0 chars (e.g. image), prevent NaN/Infinity
                val progress = if (chapterChars == 0) 0.0 else unallocatedChars.toDouble() / chapterChars.toDouble()
                return Pair(i, progress.coerceIn(0.0, 1.0))
            }
            unallocatedChars -= chapterChars
        }
        // If chars exceed total book size, clamp to the last chapter at 100%
        return Pair(maxOf(0, linearSpineItems.size - 1), 1.0)
    }
}