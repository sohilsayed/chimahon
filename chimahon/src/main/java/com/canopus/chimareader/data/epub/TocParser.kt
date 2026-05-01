package com.canopus.chimareader.data.epub

import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

class TocParser {

    fun parse(tocContent: String, manifest: EpubManifest): List<TocEntry> {
        val doc = Parser.xmlParser().parseInput(tocContent, "")

        return if (doc.select("navMap").isNotEmpty()) {
            parseNcx(doc)
        } else {
            parseXhtmlNav(doc)
        }
    }

    private fun parseNcx(doc: Document): List<TocEntry> {
        val navMap = doc.select("navMap").first() ?: return emptyList()
        return parseNavPoints(navMap.select("navPoint"))
    }

    private fun parseNavPoints(navPoints: org.jsoup.select.Elements): List<TocEntry> {
        val entries = mutableListOf<TocEntry>()

        navPoints.forEach { point ->
            val id = point.attr("id").ifBlank { java.util.UUID.randomUUID().toString() }
            val label = point.select("navLabel > text").first()?.text() ?: "Unknown"
            val href = point.select("content").first()?.attr("src") ?: ""
            val children = parseNavPoints(point.select("navPoint"))

            entries.add(
                TocEntry(
                    id = id,
                    label = label,
                    href = href,
                    children = children,
                ),
            )
        }

        return entries
    }

    private fun parseXhtmlNav(doc: Document): List<TocEntry> {
        val nav = findTocNav(doc) ?: return emptyList()
        val ol = nav.select("ol").first() ?: return emptyList()
        return parseOl(ol)
    }

    private fun findTocNav(doc: Document): org.jsoup.nodes.Element? {
        doc.select("nav").forEach { nav ->
            val type = nav.attr("epub:type")
            if (type.contains("toc")) {
                return nav
            }
        }
        return null
    }

    private fun parseOl(ol: org.jsoup.nodes.Element): List<TocEntry> {
        val entries = mutableListOf<TocEntry>()

        ol.select("> li").forEach { li ->
            val a = li.select("> a").first()
            if (a != null) {
                val label = a.text().ifBlank { "Unknown" }
                val href = a.attr("href")
                val nestedOl = li.select("> ol").first()
                val children = if (nestedOl != null) parseOl(nestedOl) else emptyList()

                entries.add(
                    TocEntry(
                        id = java.util.UUID.randomUUID().toString(),
                        label = label,
                        href = href,
                        children = children,
                    ),
                )
            }
        }

        return entries
    }

    fun parseTocFromManifest(manifest: EpubManifest, extractor: EpubExtractorBase, contentDir: String): List<TocEntry> {
        val tocId = manifest.items.entries.find { it.value.properties?.contains("nav") == true }?.key
            ?: return emptyList()

        val tocItem = manifest.items[tocId] ?: return emptyList()
        val tocPath = tocItem.href

        val tocContent = extractor.getFileContent(tocPath) ?: return emptyList()

        return parse(tocContent, manifest)
    }
}
