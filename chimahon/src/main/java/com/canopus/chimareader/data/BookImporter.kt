package com.canopus.chimareader.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.canopus.chimareader.data.epub.EpubParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

data class ImportResult(
    val metadata: BookMetadata? = null,
    val error: String? = null
)

object BookImporter {

    private const val TAG = "BookImporter"
    private const val MAX_DIM = 2048
    private const val MAX_PIXELS = 4_000_000L

    suspend fun importEpub(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting import from URI: $uri")
            
            val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.epub")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ImportResult(error = "Could not read file")

            Log.d(TAG, "Copied to temp: ${tempFile.absolutePath}, size: ${tempFile.length()}")

            try {
                ZipFile(tempFile).use { zip ->
                    val entries = zip.entries().asSequence().toList()
                    Log.d(TAG, "ZIP contains ${entries.size} entries")
                    if (entries.isEmpty()) {
                        return@withContext ImportResult(error = "File is empty")
                    }
                }
            } catch (e: Exception) {
                tempFile.delete()
                return@withContext ImportResult(error = "Not a valid EPUB file: ${e.message}")
            }

            // We'll calculate the stableId after parsing metadata to ensure parity with migration
            val booksDir = BookStorage.getBooksDirectory(context)
            Log.d(TAG, "Books directory: ${booksDir.absolutePath}")
            booksDir.mkdirs()
            
            val tempExtractDir = File(context.cacheDir, "temp_extract_${System.currentTimeMillis()}")
            tempExtractDir.mkdirs()

            ZipFile(tempFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val file = File(tempExtractDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
            
            val extractedBook = EpubParser.parse(tempExtractDir)
            val title = extractedBook.title ?: "Unknown"
            val author = extractedBook.author ?: ""
            
            val stableId = md5Hex("${title.trim().lowercase()}|${author.trim().lowercase()}")
            val bookDir = File(booksDir, stableId)
            
            Log.d(TAG, "Stable ID (Title+Author): $stableId ($title | $author)")
            
            var existingBookmark: Bookmark? = null
            var existingStats: List<Statistics>? = null
            var existingLastAccess: Long? = null

            // Move from temp to final destination
            if (bookDir.exists()) {
                existingBookmark = BookStorage.loadBookmark(bookDir)
                existingStats = BookStorage.loadStatistics(bookDir)
                existingLastAccess = BookStorage.loadMetadata(bookDir)?.lastAccess
                bookDir.deleteRecursively()
            }
            tempExtractDir.renameTo(bookDir)
            
            // Re-run normalisation and pre-wrapping on the final directory
            bookDir.walkTopDown().forEach { file ->
                val ext = file.extension.lowercase()
                if (ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp" || ext == "gif") {
                    normaliseImageInPlace(file)
                }
                if (ext == "html" || ext == "xhtml" || ext == "htm") {
                    preWrapBodyContent(file)
                }
                if (ext == "css") {
                    cleanBookCss(file)
                }
            }
            
            tempFile.delete()

            Log.d(TAG, "Extracted to: ${bookDir.absolutePath}")
            
            val extractedFiles = bookDir.walkTopDown().take(20).map { it.relativeTo(bookDir).path }.toList()
            Log.d(TAG, "Extracted files (first 20): $extractedFiles")

            Log.d(TAG, "Finalized to: ${bookDir.absolutePath}")
            
            val coverAbsPath = extractedBook.coverPath?.let { File(bookDir, it).absolutePath }

            Log.d(TAG, "Parsed EPUB: title=$title, contentDir=${extractedBook.contentDirectory}, chapters=${extractedBook.spine.items.size}")
            

            val metadata = BookMetadata(
                id = stableId,
                title = title,
                cover = coverAbsPath,
                folder = stableId,
                lastAccess = existingLastAccess ?: System.currentTimeMillis(),
                hash = stableId,
                isGhost = false
            )
            BookStorage.saveMetadata(metadata, bookDir)
            BookStorage.saveSpineCache(extractedBook.spine, bookDir)

            existingBookmark?.let { BookStorage.saveBookmark(it, bookDir) }
            existingStats?.let { BookStorage.saveStatistics(it, bookDir) }

            return@withContext ImportResult(metadata = metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            return@withContext ImportResult(error = "Import failed: ${e.message}")
        }
    }

    private fun normaliseImageInPlace(file: File) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) {
                Log.w(TAG, "normalise: could not read bounds for ${file.name} — skipping")
                return
            }

            val needsDownsample = w > MAX_DIM || h > MAX_DIM || (w.toLong() * h) > MAX_PIXELS
            if (!needsDownsample) return  // small enough — leave it alone

            val sampleSize = calculateInSampleSize(w, h)
            Log.w(TAG, "normalise: ${file.name} ${w}x${h} → 1:$sampleSize sample")

            val mimeType = bounds.outMimeType ?: ""
            val mightHaveAlpha = mimeType.contains("png", ignoreCase = true) ||
                                 mimeType.contains("gif", ignoreCase = true) ||
                                 mimeType.contains("webp", ignoreCase = true)

            val decodeOpts = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
                inPreferredConfig = if (mightHaveAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
            }
            val bitmap: Bitmap? = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            if (bitmap == null) {
                Log.e(TAG, "normalise: decode returned null for ${file.name} — skipping")
                return
            }

            try {
                val hasAlpha = bitmap.hasAlpha() && mightHaveAlpha
                @Suppress("DEPRECATION")
                val format: Bitmap.CompressFormat
                val quality: Int
                if (hasAlpha) {
                    // Lossless WebP to preserve transparency
                    format = if (android.os.Build.VERSION.SDK_INT >= 30)
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    else
                        Bitmap.CompressFormat.WEBP
                    quality = 100
                } else {
                    // Lossy WebP — smallest file, no transparency needed
                    format = if (android.os.Build.VERSION.SDK_INT >= 30)
                        Bitmap.CompressFormat.WEBP_LOSSY
                    else
                        Bitmap.CompressFormat.WEBP
                    quality = 82
                }

                val tmp = File(file.parent, "${file.name}.norm.tmp")
                val ok = try {
                    tmp.outputStream().use { out -> bitmap.compress(format, quality, out) }
                } catch (e: Exception) {
                    Log.e(TAG, "normalise: compress failed for ${file.name}", e)
                    tmp.delete()
                    false
                }

                if (ok) {
                    if (!tmp.renameTo(file)) {
                        // renameTo can fail cross-device; fall back to copy + delete
                        tmp.inputStream().use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
                        tmp.delete()
                    }
                    Log.d(TAG, "normalise: ${file.name} compressed OK (alpha=$hasAlpha)")
                } else {
                    Log.e(TAG, "normalise: compress returned false for ${file.name} — keeping original")
                    tmp.delete()
                }
            } finally {
                bitmap.recycle()
            }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "normalise: OOM on ${file.name} — keeping original", e)
        } catch (e: Exception) {
            Log.e(TAG, "normalise: unexpected error for ${file.name} — keeping original", e)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var inSampleSize = 1
        var w = width
        var h = height
        while (w > MAX_DIM || h > MAX_DIM || (w.toLong() * h) > MAX_PIXELS) {
            inSampleSize *= 2
            w = width / inSampleSize
            h = height / inSampleSize
        }
        return inSampleSize
    }

    private fun preWrapBodyContent(file: File) {
        try {
            val text = file.readText(Charsets.UTF_8)

            if (text.contains("hoshi-content-wrapper")) return

            val bodyTagStart = text.indexOf("<body", ignoreCase = true)
            if (bodyTagStart < 0) return  // no <body> — skip (e.g. CSS-only file)
            val bodyTagEnd = text.indexOf('>', bodyTagStart)
            if (bodyTagEnd < 0) return

            val bodyClose = text.lastIndexOf("</body", ignoreCase = true)
            if (bodyClose < 0 || bodyClose <= bodyTagEnd) return

            val result = StringBuilder(text.length + 70)
                .append(text, 0, bodyTagEnd + 1)
                .append("<div id=\"hoshi-content-wrapper\">")
                .append(text, bodyTagEnd + 1, bodyClose)
                .append("</div>")
                .append(text, bodyClose, text.length)
                .toString()

            file.writeText(result, Charsets.UTF_8)
            Log.d(TAG, "preWrap: wrapped ${file.name} (${text.length} → ${result.length} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "preWrap: failed for ${file.name} — skipping", e)
        }
    }

    private fun cleanBookCss(file: File) {
        try {
            var text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return

            // @page blocks
            text = text.replace(Regex("""(?is)@page\s*\{[^}]*\}\s*"""), "")
            // -epub-* property declarations
            text = text.replace(Regex("""[ \t]*-epub-[\w-]+\s*:[^;]+;[ \t]*\n?"""), "")
            // writing-mode / -webkit-writing-mode
            text = text.replace(Regex("""(?i)[ \t]*(?:-webkit-)?writing-mode\s*:[^;]+;[ \t]*\n?"""), "")
            // column-* (column-width, column-count, column-gap, column-fill, etc.)
            text = text.replace(Regex("""(?i)[ \t]*column-[\w-]+\s*:[^;]+;[ \t]*\n?"""), "")
            // overflow / overflow-x / overflow-y
            text = text.replace(Regex("""(?i)[ \t]*overflow(?:-[xy])?\s*:[^;]+;[ \t]*\n?"""), "")

            text = stripBodyHtmlLayoutProps(text)

            file.writeText(text, Charsets.UTF_8)
            Log.d(TAG, "cleanCss: cleaned ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "cleanCss: failed for ${file.name} — skipping", e)
        }
    }

    private fun stripBodyHtmlLayoutProps(css: String): String {
        // Matches a CSS selector + single-level { declarations } block.
        // Leading '@' guard prevents mismatching inside @media bodies.
        val blockRe = Regex("""([^{}@]+)\{([^{}]*)\}""")

        // Detects the words html or body as standalone identifiers.
        val targetSelectorRe = Regex(
            """(?<![.\w#-])(html|body)(?![.\w-])""",
            RegexOption.IGNORE_CASE,
        )

        // Layout properties the reader injection overrides on html/body.
        // Written as a single raw triple-quoted string to avoid escaping issues.
        val layoutPropRe = Regex(
            """(?i)[ \t]*(margin(?:-(?:top|right|bottom|left))?|padding(?:-(?:top|right|bottom|left))?|font-size|line-height|letter-spacing|text-align|background(?:-color)?|(?:min-|max-)?height|(?:min-|max-)?width)\s*:[^;]+;[ \t]*\n?""",
        )

        return blockRe.replace(css) { match ->
            val selector = match.groupValues[1]
            val declarations = match.groupValues[2]
            if (!targetSelectorRe.containsMatchIn(selector)) {
                match.value // not targeting html/body — leave unchanged
            } else {
                val cleaned = layoutPropRe.replace(declarations, "")
                "${selector}{${cleaned}}"
            }
        }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
