package com.canopus.chimareader.data.epub

import android.util.Log
import java.io.File

/**
 * Counts the number of standalone image elements (img or svg-with-image) in the body.
 * Used to guard against inline images being mistaken for full-page images.
 */
private fun countBodyImages(body: String): Int {
    val imgCount = Regex("<img[^>]*>", RegexOption.IGNORE_CASE).findAll(body).count()
    val svgCount = Regex("<svg[^>]*>.*?</svg>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .findAll(body)
        .count { it.value.contains("xlink:href", ignoreCase = true) || it.value.contains("<image", ignoreCase = true) }
    return imgCount + svgCount
}

/**
 * Detects if an HTML file is truly an image-only page (no meaningful text besides the image).
 *
 * Strategy:
 *   1. Must have exactly 1 standalone image element in the body (multiple images → it's a
 *      content page with inline illustrations, not a cover/full-page image).
 *   2. After stripping the image element AND <figcaption> blocks (not the whole <figure>)
 *      the remaining text must be blank.
 *   3. Strip alt attributes, HTML entities, and numeric character refs before the blank check.
 *   4. Reject if the <img> has explicit small dimensions (< 300px on either axis) — these
 *      are decorative dividers or inline illustrations, not full-page images.
 *   5. Reject if the body or a containing element signals it's an illustration via
 *      epub:type="illustration" or well-known class names (illus, illustration, float-img, etc.).
 */
private fun detectImageOnlyPage(htmlContent: String): Boolean {
    // Quick pre-check: must contain at least one image marker
    val hasImgTag = htmlContent.contains("<img", ignoreCase = true)
    val hasSvgImg = htmlContent.contains("<svg", ignoreCase = true) &&
        (
            htmlContent.contains("xlink:href", ignoreCase = true) ||
                htmlContent.contains("<image", ignoreCase = true)
            )
    if (!hasImgTag && !hasSvgImg) return false

    // Extract body text only (ignore <head>)
    val bodyRegex = Regex("<body[^>]*>(.*)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val body = bodyRegex.find(htmlContent)?.groupValues?.get(1) ?: htmlContent

    // Guard: pages with more than 1 image element are treated as inline-image content pages
    if (countBodyImages(body) > 1) return false

    // Guard: if the <img> carries explicit small dimensions it's a decorative/inline image.
    // Full-page cover images rarely have explicit pixel dimensions; small decorative ones do.
    val imgTagMatch = Regex("<img[^>]*>", RegexOption.IGNORE_CASE).find(body)
    if (imgTagMatch != null) {
        val imgTag = imgTagMatch.value
        val widthAttr = Regex("""width\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE).find(imgTag)?.groupValues?.get(1)?.toIntOrNull()
        val heightAttr = Regex("""height\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE).find(imgTag)?.groupValues?.get(1)?.toIntOrNull()
        // If either explicit dimension is present and clearly small → not a full-page image
        if ((widthAttr != null && widthAttr < 300) || (heightAttr != null && heightAttr < 300)) return false
    }

    // Guard: epub:type="illustration" or illustration-like class names on any element in body
    // indicate this is a decorative image embedded in flowing content, not a standalone page.
    val illustrationPattern = Regex(
        """(?:epub:type\s*=\s*["'][^"']*illustration[^"']*["'])|(?:class\s*=\s*["'][^"']*\b(?:illus(?:tration)?|float[-_]?img|inline[-_]?img|decorat)\b[^"']*["'])""",
        setOf(RegexOption.IGNORE_CASE),
    )
    if (illustrationPattern.containsMatchIn(body)) return false

    val textOnly = body
        // Remove full SVG blocks
        .replace(Regex("<svg[^>]*>.*?</svg>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        // Remove <img> tags including alt text embedded in the attribute
        .replace(Regex("<img[^>]*>", RegexOption.IGNORE_CASE), "")
        // Remove <figcaption> separately (captions don't make it a text page)
        .replace(Regex("<figcaption[^>]*>.*?</figcaption>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        // Remove <figure> wrappers (but not content — handled above)
        .replace(Regex("<figure[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</figure>", RegexOption.IGNORE_CASE), "")
        // Strip all remaining HTML tags
        .replace(Regex("<[^>]+>"), "")
        // Strip HTML named entities (&nbsp; etc.) and numeric char refs (&#160; &#x2F; etc.)
        .replace(Regex("&[a-zA-Z]+;"), "")
        .replace(Regex("&#x?[0-9a-fA-F]+;"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    return textOnly.isBlank()
}

/**
 * Extracts the image src from IMAGE_ONLY HTML content and resolves it to a file:// URL.
 */
private fun extractImageSrcUrl(
    htmlContent: String,
    chapterHref: String,
    baseDir: File?,
    zipPath: String,
    extractor: EpubExtractorBase,
): String? {
    var imgSrc = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
        .find(htmlContent)?.groupValues?.get(1)
    if (imgSrc == null) {
        imgSrc = Regex("<image[^>]+xlink:href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(htmlContent)?.groupValues?.get(1)
    }
    imgSrc ?: return null

    val basePath = chapterHref.substringBeforeLast("/")
    val resolvedPath = when {
        imgSrc.startsWith("http") -> return imgSrc
        imgSrc.startsWith("/") -> imgSrc
        else -> "$basePath/$imgSrc".replace("//", "/")
    }

    if (baseDir != null) {
        val file = File(baseDir, resolvedPath)
        if (file.exists()) return "file://${file.absolutePath}"
    }

    if (zipPath.isNotEmpty()) {
        val stream = extractor.getFileStream(resolvedPath) ?: return null
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "epub_img_cache")
        cacheDir.mkdirs()
        val cached = File(cacheDir, resolvedPath.substringAfterLast("/"))
        cached.parentFile?.mkdirs()
        stream.use { it.copyTo(cached.outputStream()) }
        return "file://${cached.absolutePath}"
    }

    return null
}

class EpubParser {

    fun parse(epubFile: File, cachedSpine: EpubSpine? = null): EpubBook {
        val isDirectory = epubFile.isDirectory
        val extractor = if (isDirectory) {
            EpubExtractor.fromDirectory(epubFile)
        } else {
            EpubExtractor.fromFile(epubFile)
        }

        try {
            val packageOpfPath = extractor.getPackageOpfPath()
            val contentDir = extractor.getContentDirectory(packageOpfPath)

            val opfContent = extractor.getFileContent(packageOpfPath)
                ?: throw EpubParseException("Failed to read package.opf")

            val opfParser = OpfParser()
            val opfResult = opfParser.parseOpf(opfContent, contentDir)

            val tocParser = TocParser()
            val tableOfContents = tocParser.parseTocFromManifest(opfResult.manifest, extractor, contentDir)

            val coverPath = opfResult.metadata.coverId?.let { coverId ->
                opfResult.manifest.items[coverId]?.href?.let { href ->
                    if (contentDir.isNotEmpty()) "$contentDir$href" else href
                }
            }

            val coverIdref = opfResult.metadata.coverId?.let { coverId ->
                // coverId points to a manifest item id — we need to find which spine idref it is
                coverId
            }

            val spineWithTypes = if (cachedSpine != null && cachedSpine.items.isNotEmpty()) {
                cachedSpine.items
            } else {
                opfResult.spine.items.map { spineItem ->
                    val manifestItem = opfResult.manifest.items[spineItem.idref]
                    val href = manifestItem?.href
                    val isNativeImage = href != null && (href.endsWith(".jpg", true) || href.endsWith(".jpeg", true) || href.endsWith(".png", true) || href.endsWith(".webp", true))
                    // Cover pages declared via OPF metadata are always IMAGE_ONLY,
                    // even if detectImageOnlyPage() returns false due to minor extra text
                    val isCoverSpineItem = spineItem.idref == coverIdref

                    val isImageOnly = when {
                        isNativeImage -> true
                        isCoverSpineItem -> true // OPF-declared cover always treated as image
                        href != null && (href.endsWith(".xhtml", true) || href.endsWith(".html", true) || href.endsWith(".htm", true)) -> {
                            val content = extractor.getFileContent("$contentDir$href")
                            content != null && detectImageOnlyPage(content)
                        }
                        else -> false
                    }

                    val chapterHref = if (href != null) "$contentDir$href" else null
                    val imageUrl = if (isImageOnly && chapterHref != null) {
                        if (isNativeImage) {
                            if (isDirectory) {
                                "file://${File(epubFile, chapterHref).absolutePath}"
                            } else {
                                val stream = extractor.getFileStream(chapterHref)
                                if (stream != null) {
                                    val cacheDir = File(System.getProperty("java.io.tmpdir"), "epub_img_cache")
                                    cacheDir.mkdirs()
                                    val cached = File(cacheDir, chapterHref.substringAfterLast("/"))
                                    cached.parentFile?.mkdirs()
                                    stream.use { it.copyTo(cached.outputStream()) }
                                    "file://${cached.absolutePath}"
                                } else {
                                    null
                                }
                            }
                        } else {
                            val content = extractor.getFileContent(chapterHref)
                            if (content != null) {
                                extractImageSrcUrl(
                                    content,
                                    chapterHref,
                                    if (isDirectory) epubFile else null,
                                    if (!isDirectory) epubFile.absolutePath else "",
                                    extractor,
                                )
                            } else {
                                null
                            }
                        }
                    } else {
                        null
                    }
                    spineItem.copy(
                        type = if (isImageOnly) SpineItemType.IMAGE_ONLY else SpineItemType.TEXT,
                        imageUrl = imageUrl,
                    )
                }
            }

            return EpubBook(
                title = opfResult.metadata.title,
                author = opfResult.metadata.creator?.name,
                language = opfResult.metadata.language,
                coverPath = coverPath,
                metadata = opfResult.metadata,
                manifest = opfResult.manifest,
                spine = opfResult.spine.copy(items = spineWithTypes),
                tableOfContents = tableOfContents,
                contentDirectory = contentDir,
                zipPath = if (!isDirectory) epubFile.absolutePath else "",
                extractedDir = if (isDirectory) epubFile else null,
            )
        } finally {
            extractor.close()
        }
    }

    fun parseChapter(epubBook: EpubBook, chapterIndex: Int): String? {
        val href = epubBook.getChapterHref(chapterIndex) ?: return null

        val extractor = if (epubBook.extractedDir != null) {
            EpubExtractor.fromDirectory(epubBook.extractedDir)
        } else {
            EpubExtractor.fromFile(File(epubBook.zipPath))
        }

        return try {
            extractor.getFileContent(href)
        } finally {
            extractor.close()
        }
    }

    fun getChapterInputStream(epubBook: EpubBook, chapterIndex: Int): java.io.InputStream? {
        val href = epubBook.getChapterHref(chapterIndex) ?: return null

        val extractor = if (epubBook.extractedDir != null) {
            EpubExtractor.fromDirectory(epubBook.extractedDir)
        } else {
            EpubExtractor.fromFile(File(epubBook.zipPath))
        }

        return extractor.getFileStream(href)
    }

    companion object {
        fun fromFile(epubFile: File, cachedSpine: EpubSpine? = null): EpubBook {
            return EpubParser().parse(epubFile, cachedSpine)
        }

        fun parse(file: File, cachedSpine: EpubSpine? = null): EpubBook {
            return EpubParser().parse(file, cachedSpine)
        }
    }
}
