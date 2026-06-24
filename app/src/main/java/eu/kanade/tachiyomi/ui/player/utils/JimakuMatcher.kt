package eu.kanade.tachiyomi.ui.player.utils

data class JimakuMediaGuess(
    val title: String,
    val episode: Int?,
    val season: Int? = null,
    val episodeCandidates: Set<Int> = episode?.let { setOf(it) }.orEmpty(),
)

private data class JimakuEpisodeNumbers(
    val primary: Int?,
    val candidates: Set<Int>,
)

private val jimakuAbsoluteEpisodeRegexes = listOf(
    Regex("""\u7b2c\s*(\d{1,4})\s*[\u8a71\u96c6\u56de]"""),
    Regex("""(\d{1,4})\s*[\u8a71\u96c6\u56de]"""),
    Regex("""(?i)(?:\uc81c\s*)?(\d{1,4})\s*(?:\ud654|\ud68c)"""),
)
private val jimakuEpisodeRegexes = listOf(
    Regex("""(?i)\bS\s*\d{1,3}\s*[._ -]*E\s*(\d{1,4})\b"""),
    Regex("""(?i)\bS\s*\d{1,3}\s*[._ -]*x\s*E?\s*(\d{1,4})\b"""),
    Regex("""(?i)\b\d{1,3}\s*x\s*(\d{1,4})\b"""),
    Regex("""(?i)\b(?:ep|eps|episode|episodes|episodio|episodios|capitulo|capitulos|cap|e)\.?\s*[-_ ]?(\d{1,4})(?:v\d+)?\b"""),
    Regex("""(?i)\b(\d{1,4})(?:st|nd|rd|th)?\s*(?:ep|episode|episodes)\b"""),
    Regex("""[#\uff03]\s*(\d{1,4})\b"""),
)
private val jimakuStandaloneEpisodeRegex = Regex("""(?<![A-Za-z0-9])(\d{1,4})(?![A-Za-z0-9]|p|P)""")
private val jimakuEpisodeCleanupRegex = Regex(
    """(?i)\bS\s*\d{1,3}\s*[._ -]*(?:E|xE?|x)\s*\d{1,4}\b|\b\d{1,3}\s*x\s*\d{1,4}\b|\b(?:ep|eps|episode|episodes|episodio|episodios|capitulo|capitulos|cap|e)\.?\s*[-_ ]?\d{1,4}(?:v\d+)?\b|[#\uff03]\s*\d{1,4}\b|\u7b2c\s*\d{1,4}\s*[\u8a71\u96c6\u56de]|\d{1,4}\s*[\u8a71\u96c6\u56de]|(?:\uc81c\s*)?\d{1,4}\s*(?:\ud654|\ud68c)""",
)
private val jimakuSeasonRegexes = listOf(
    Regex("""(?i)\bS\s*(\d{1,3})\s*[._ -]*(?:E|xE?|x)\s*\d{1,4}\b"""),
    Regex("""(?i)\b(\d{1,3})\s*x\s*\d{1,4}\b"""),
    Regex("""(?i)\b(?:season|seasons|saison|saisons|seizoen|temporada|temporadas|stagione|temp|s)\.?\s*(\d{1,3})\b"""),
)
private val jimakuSeasonCleanupRegex = Regex("""(?i)\bseason[ ._-]*\d{1,3}\b""")
private val jimakuKnownExtensionRegex = Regex(
    """(?i)\.(?:3g2|3gp|avi|divx|flv|m2ts|m4v|mkv|mov|mp4|mpeg|mpg|ogm|ogv|rmvb|ts|vob|webm|wmv|ass|idx|smi|srt|ssa|sub|vtt)$""",
)
private val jimakuReleaseInfoPattern = listOf(
    "144p",
    "240p",
    "360p",
    "368p",
    "480p",
    "540p",
    "576p",
    "720p",
    "900p",
    "960p",
    "1080[pi]",
    "1440p",
    "2160p",
    "4320p",
    "4k",
    "8k",
    "uhd",
    "fhd",
    "vhs(?:rip)?",
    "cam(?:rip)?",
    "hdcam",
    "telesync",
    "hdts",
    "ts",
    "workprint",
    "wp",
    "telecine",
    "hdtc",
    "tc",
    "ppv",
    "sdtv",
    "pdtv",
    "hdtv",
    "tvrip",
    "dvb",
    "dsr",
    "dth",
    "satrip",
    "vod(?:rip)?",
    "web(?:rip|dl|cap|uhd)?",
    "webrip",
    "web-dl",
    "webdl",
    "webcap",
    "dlweb",
    "dvd(?:rip|r|5|9)?",
    "dvdrip",
    "hddvd",
    "blu-?ray",
    "b[dr](?:rip|remux)?",
    "brrip",
    "bdrip",
    "remux",
    "xvid",
    "divx",
    "x264",
    "x265",
    "h[._-]?264",
    "h[._-]?265",
    "hevc",
    "avc",
    "av1",
    "vp9",
    "vc-?1",
    "mpeg-?2",
    "hi10p",
    "10bit",
    "8bit",
    "mp3",
    "mp2",
    "aac",
    "ac-?3",
    "e-?ac-?3",
    "ddp?",
    "dd\\+",
    "dts(?:-?hd|-?ma|-?x)?",
    "true-?hd",
    "atmos",
    "flac",
    "opus",
    "vorbis",
    "pcm",
    "lpcm",
    "[257]\\.?1(?:ch)?",
    "[12678]ch",
    "dual[ ._-]?audio",
    "multi[ ._-]?audio",
    "fansub",
    "fastsub",
    "hardsub",
    "softsub",
    "subbed",
    "dubbed",
    "vostfr",
    "vost",
    "pal",
    "ntsc",
    "secam",
    "hdr(?:10\\+?)?",
    "dv",
    "dolby[ ._-]?vision",
    "sdr",
    "bt[ ._-]?2020",
    "proper",
    "repack",
    "rerip",
    "internal",
    "limited",
    "extended",
    "uncensored",
    "uncut",
    "remastered",
    "directors?[ ._-]?cut",
    "hybrid",
    "complete",
    "amzn",
    "amazon",
    "nf",
    "netflix",
    "hulu",
    "dsnp",
    "disney\\+?",
    "cr",
    "crunchyroll",
    "hidive",
    "hbo",
    "hmax",
    "atvp",
    "baha",
    "bilibili",
    "funi",
    "yye?ts",
).joinToString("|")
private val jimakuFilenameReleaseTailRegex = Regex(
    """(?i)(?<=\S)(?:[\s._-]+|[\[(]\s*)(?:$jimakuReleaseInfoPattern)(?=${'$'}|[\s._-]|\]|\)|}).*${'$'}""",
)
private val jimakuFilenameJunkRegex = Regex(
    """(?i)(?:^|[\s._-])(?:$jimakuReleaseInfoPattern)(?=${'$'}|[\s._-])|\[[^\]]*]|\([^)]*\)|\{[^}]*}""",
)
private val jimakuTrailingEpisodeCleanupRegex = Regex(
    """(?i)(?<=\S)\s*(?:[-._]+\s*(?:ep|episode|e)?\.?|(?:ep|episode|e)\.?\s*)\d{1,4}\s*${'$'}""",
)
private val jimakuLeadingReleaseGroupRegex = Regex("""^\s*(?:\[[^\]]{1,80}]|\([^)]{1,80}\)|\{[^}]{1,80}})\s*""")
private val jimakuTrailingReleaseGroupRegex = Regex("""(?i)(?<=\S)-[A-Za-z0-9][A-Za-z0-9._-]{1,40}${'$'}""")
private val jimakuHashRegex = Regex("""(?i)[\[(]?[A-F0-9]{8}(?:[A-F0-9]{8})?[\])]?(?=${'$'}|[\s._-])""")
private val jimakuWebsiteRegex = Regex("""(?i)\b(?:www\.)?[A-Za-z0-9-]+\.(?:com|net|org|ru|cc|tv)\b""")
private val jimakuSubtitleLanguageSuffixRegex = Regex(
    """(?i)(?:[\s._-]+(?:sub|subs|subtitle|subtitles|dub|dual|multi|eng|english|en|en-us|en-gb|fre|french|fr|spa|spanish|es|ger|deu|de|ita|it|por|pt|rus|ru|jpn|japanese|ja|jp|ja-jp|kor|korean|ko|chi|chs|cht|zho|zh|ara|ar))+\s*${'$'}""",
)
private val jimakuTitleBeforeEpisodeRegexes = listOf(
    Regex("""(?i)^(.+?)(?=[\s._-]+S\s*\d{1,3}\s*[._ -]*(?:E|xE?|x)\s*\d{1,4}\b)"""),
    Regex("""(?i)^(.+?)(?=[\s._-]+\d{1,3}\s*x\s*\d{1,4}\b)"""),
    Regex("""(?i)^(.+?)(?=\s*[-._]\s*(?:ep|episode|e)?\.?\s*\d{1,4}\b)"""),
    Regex("""^(.+?)(?=[\s._-]+\u7b2c\s*\d{1,4}\s*[\u8a71\u96c6\u56de])"""),
)
private val jimakuTitleAfterLeadingEpisodeRegexes = listOf(
    Regex("""(?i)^\s*(?:\[[^\]]{1,80}]\s*)?(?:[#\uff03]\s*)?\d{1,4}(?:v\d+)?\s*[-._ ]+\s*(.+)$"""),
    Regex("""(?i)^\s*(?:\[[^\]]{1,80}]\s*)?(?:ep|episode|e)\.?\s*\d{1,4}(?:v\d+)?\s*[-._ ]+\s*(.+)$"""),
)
private val jimakuIgnoredNumbers = setOf(480, 720, 1080, 2160, 264, 265, 10)

fun guessJimakuMedia(value: String): JimakuMediaGuess? {
    val withoutExtension = value
        .toJimakuFilename()
        .replace(jimakuKnownExtensionRegex, "")
    val season = jimakuSeasonRegexes.firstNotNullOfOrNull { regex ->
        regex.find(withoutExtension)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
    val episodeNumbers = withoutExtension.jimakuEpisodeNumbers()
    val title = withoutExtension
        .preferredJimakuTitleSeed()
        .cleanJimakuTitleCandidate()

    return title.takeIf { it.isNotBlank() }?.let {
        JimakuMediaGuess(
            title = it,
            episode = episodeNumbers.primary,
            season = season,
            episodeCandidates = episodeNumbers.candidates,
        )
    }
}

fun selectBestJimakuEntry(entries: List<JimakuEntry>, title: String): JimakuEntry? {
    if (entries.size == 1) return entries.first()

    val target = title.normalizedJimakuText()
    entries.firstOrNull { entry ->
        listOf(entry.name, entry.englishName.orEmpty(), entry.japaneseName.orEmpty())
            .any { it.normalizedJimakuText() == target }
    }?.let { return it }

    val ranked = entries
        .map { it to jimakuEntryScore(it, title) }
        .sortedByDescending { it.second }

    return ranked.firstOrNull()?.first
}

fun List<JimakuFile>.matchedSrtFiles(guess: JimakuMediaGuess, episodeFiltered: Boolean): List<JimakuFile> {
    return filter { file -> file.name.lowercase().endsWith(".srt") }
        .filter { file ->
            val parsed = guessJimakuMedia(file.name)
            val parsedEpisode = parsed?.episode
            val parsedSeason = parsed?.season
            val seasonMatches = guess.season == null ||
                parsedSeason == null ||
                parsedSeason == guess.season
            seasonMatches && (
                guess.episode == null ||
                    parsed?.matchesJimakuEpisode(guess.episode) == true ||
                    (episodeFiltered && parsed?.hasJimakuEpisodeCandidates() != true && parsedEpisode == null)
                )
        }
        .sortedWith(
            compareByDescending<JimakuFile> { jimakuFileScore(it, guess) }
                .thenBy { it.name },
        )
}

fun JimakuMediaGuess.displayName(): String {
    val seasonText = season?.let { " season $it" }.orEmpty()
    val episodeText = episode?.let { " episode $it" }.orEmpty()
    return "$title$seasonText$episodeText"
}

private fun jimakuEntryScore(entry: JimakuEntry, title: String): Int {
    val target = title.normalizedJimakuText()
    return listOf(entry.name, entry.englishName.orEmpty(), entry.japaneseName.orEmpty())
        .filter { it.isNotBlank() }
        .maxOfOrNull { candidate ->
            val normalized = candidate.normalizedJimakuText()
            when {
                normalized == target -> 100
                normalized.contains(target) || target.contains(normalized) -> 90
                else -> {
                    val targetTokens = target.split(" ").filter { it.isNotBlank() }.toSet()
                    val candidateTokens = normalized.split(" ").filter { it.isNotBlank() }.toSet()
                    if (targetTokens.isEmpty() || candidateTokens.isEmpty()) {
                        0
                    } else {
                        (targetTokens.intersect(candidateTokens).size * 100) /
                            maxOf(targetTokens.size, candidateTokens.size)
                    }
                }
            }
        } ?: 0
}

private fun jimakuFileScore(file: JimakuFile, guess: JimakuMediaGuess): Int {
    val parsed = guessJimakuMedia(file.name)
    val parsedEpisode = parsed?.episode
    val parsedSeason = parsed?.season
    var score = 0

    if (guess.season != null) {
        score += when (parsedSeason) {
            guess.season -> 60
            null -> 0
            else -> -160
        }
    }

    if (guess.episode != null) {
        score += when {
            parsed?.matchesJimakuEpisode(guess.episode) == true -> 100
            parsed?.hasJimakuEpisodeCandidates() == true -> -80
            parsedEpisode == null -> 20
            else -> -80
        }
    }

    val fileTitle = parsed?.title?.normalizedJimakuText().orEmpty()
    val targetTitle = guess.title.normalizedJimakuText()
    if (fileTitle.isNotBlank() && targetTitle.isNotBlank()) {
        if (fileTitle.contains(targetTitle) || targetTitle.contains(fileTitle)) {
            score += 25
        }
    }

    val lowerName = file.name.lowercase()
    if (lowerName.contains("ja-jp") || lowerName.contains("japanese") || lowerName.contains(".ja.")) {
        score += 25
    }
    if (lowerName.endsWith(".ass") || lowerName.endsWith(".srt") || lowerName.endsWith(".ssa")) {
        score += 10
    }

    return score
}

private fun String.toJimakuFilename(): String {
    return substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
}

private fun String.jimakuEpisodeNumbers(): JimakuEpisodeNumbers {
    val absoluteEpisodes = jimakuAbsoluteEpisodeRegexes.jimakuEpisodeNumbersFrom(this)
    val markedEpisodes = jimakuEpisodeRegexes.jimakuEpisodeNumbersFrom(this)
    val standaloneEpisodes = jimakuStandaloneEpisodeRegex.findAll(this)
        .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
        .filter { it.isValidJimakuEpisodeNumber() && it !in jimakuIgnoredNumbers }
        .toList()
    val weakEpisodes = if (absoluteEpisodes.isEmpty() && markedEpisodes.isEmpty()) {
        standaloneEpisodes
    } else {
        emptyList()
    }
    val primary = absoluteEpisodes.firstOrNull()
        ?: markedEpisodes.firstOrNull()
        ?: weakEpisodes.lastOrNull()
    val candidates = (absoluteEpisodes + markedEpisodes + weakEpisodes)
        .filter { it.isValidJimakuEpisodeNumber() }
        .toCollection(LinkedHashSet())

    return JimakuEpisodeNumbers(primary, candidates)
}

private fun List<Regex>.jimakuEpisodeNumbersFrom(value: String): List<Int> {
    return flatMap { regex ->
        regex.findAll(value).mapNotNull { match ->
            match.groupValues.lastOrNull()?.toIntOrNull()
        }
    }.filter { it.isValidJimakuEpisodeNumber() }
}

private fun Int.isValidJimakuEpisodeNumber(): Boolean {
    return this in 0..9999
}

private fun String.preferredJimakuTitleSeed(): String {
    for (regex in jimakuTitleBeforeEpisodeRegexes) {
        val candidate = regex.find(this)?.groupValues?.getOrNull(1).orEmpty()
        val cleaned = candidate.cleanJimakuTitleCandidate()
        if (cleaned.normalizedJimakuText().length >= 2) {
            return candidate
        }
    }
    for (regex in jimakuTitleAfterLeadingEpisodeRegexes) {
        val candidate = regex.find(this)?.groupValues?.getOrNull(1).orEmpty()
        val cleaned = candidate.cleanJimakuTitleCandidate()
        if (cleaned.normalizedJimakuText().length >= 2) {
            return candidate
        }
    }

    return this
}

private fun String.cleanJimakuTitleCandidate(): String {
    return replace(jimakuKnownExtensionRegex, "")
        .replace(jimakuLeadingReleaseGroupRegex, " ")
        .replace(jimakuHashRegex, " ")
        .replace(jimakuWebsiteRegex, " ")
        .replace(jimakuEpisodeCleanupRegex, " ")
        .replace(jimakuSeasonCleanupRegex, " ")
        .replace(jimakuFilenameReleaseTailRegex, " ")
        .replace(jimakuFilenameJunkRegex, " ")
        .replace(jimakuSubtitleLanguageSuffixRegex, " ")
        .replace(jimakuTrailingEpisodeCleanupRegex, " ")
        .replace(jimakuTrailingReleaseGroupRegex, " ")
        .replace(Regex("""[._-]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun JimakuMediaGuess.matchesJimakuEpisode(episode: Int): Boolean {
    return episode == this.episode || episode in episodeCandidates
}

private fun JimakuMediaGuess.hasJimakuEpisodeCandidates(): Boolean {
    return episode != null || episodeCandidates.isNotEmpty()
}

private fun String.normalizedJimakuText(): String {
    var lastWasSpace = true
    return buildString {
        for (char in lowercase()) {
            if (char.isLetterOrDigit()) {
                append(char)
                lastWasSpace = false
            } else if (!lastWasSpace) {
                append(' ')
                lastWasSpace = true
            }
        }
    }.trim()
}
