package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.ui.player.settings.SubtitlePreferences

data class SubtitleRegexFilterOptions(
    val removeSpeakerNames: Boolean,
    val mergeMultiline: Boolean,
    val removeBracketedText: Boolean,
    val removeUppercaseLines: Boolean,
    val removeMusicSymbols: Boolean,
    val removeCurlyBracedText: Boolean,
    val customRegexEnabled: Boolean,
    val customRegexPattern: String,
) {
    val enabled: Boolean
        get() = removeSpeakerNames ||
            mergeMultiline ||
            removeBracketedText ||
            removeUppercaseLines ||
            removeMusicSymbols ||
            removeCurlyBracedText ||
            (customRegexEnabled && customRegexPattern.isNotBlank())
}

fun SubtitlePreferences.subtitleRegexFilterOptions(): SubtitleRegexFilterOptions {
    return SubtitleRegexFilterOptions(
        removeSpeakerNames = subtitleRegexRemoveSpeakerNames().get(),
        mergeMultiline = subtitleRegexMergeMultiline().get(),
        removeBracketedText = subtitleRegexRemoveBracketedText().get(),
        removeUppercaseLines = subtitleRegexRemoveUppercaseLines().get(),
        removeMusicSymbols = subtitleRegexRemoveMusicSymbols().get(),
        removeCurlyBracedText = subtitleRegexRemoveCurlyBracedText().get(),
        customRegexEnabled = subtitleRegexCustomEnabled().get(),
        customRegexPattern = subtitleRegexCustomPattern().get(),
    )
}

fun String.applySubtitleRegexFilters(options: SubtitleRegexFilterOptions): String {
    if (!options.enabled) return this

    var result = this
    if (options.removeSpeakerNames) {
        result = speakerNameInParenthesesRegex.replace(result, "")
    }
    if (options.removeBracketedText) {
        result = bracketedTextRegex.replace(result, "")
    }
    if (options.removeCurlyBracedText) {
        result = curlyBracedTextRegex.replace(result, "")
    }
    if (options.removeMusicSymbols) {
        result = musicSymbolsRegex.replace(result, "")
    }
    if (options.customRegexEnabled && options.customRegexPattern.isNotBlank()) {
        customSubtitleRegex(options.customRegexPattern)?.let { regex ->
            result = regex.replace(result, "")
        }
    }

    val filteredLines = result
        .lines()
        .map { it.trim().collapseSubtitleRegexWhitespace() }
        .filter { it.isNotBlank() && (!options.removeUppercaseLines || !it.isAllUppercaseSubtitleLine()) }

    return if (options.mergeMultiline) {
        filteredLines.joinToString(" ")
    } else {
        filteredLines.joinToString("\n")
    }.cleanSubtitleRegexFilterResult()
}

fun customSubtitleRegex(pattern: String): Regex? {
    return runCatching {
        Regex(pattern, setOf(RegexOption.MULTILINE))
    }.getOrNull()
}

private val speakerNameInParenthesesRegex = Regex("""(?m)^\s*\([^()\n]{1,48}\)\s*:?\s*""")
private val bracketedTextRegex = Regex("""\[[^\[\]\n]*]""")
private val curlyBracedTextRegex = Regex("""\{[^{}\n]*}""")
private val musicSymbolsRegex = Regex("""[♪♫♬♩♭♯#~〜]+""")

private fun String.cleanSubtitleRegexFilterResult(): String {
    return lines()
        .map { it.trim().collapseSubtitleRegexWhitespace() }
        .filter { line -> line.isNotBlank() && line.any { it.isLetterOrDigit() } }
        .joinToString("\n")
}

private fun String.collapseSubtitleRegexWhitespace(): String {
    var lastWasSpace = false
    return buildString(length) {
        for (char in this@collapseSubtitleRegexWhitespace) {
            if (char == ' ' || char == '\t') {
                if (!lastWasSpace) append(' ')
                lastWasSpace = true
            } else {
                append(char)
                lastWasSpace = false
            }
        }
    }.trim()
}

private fun String.isAllUppercaseSubtitleLine(): Boolean {
    val casedLetters = filter { it.isLetter() && (it.isUpperCase() || it.isLowerCase()) }
    return casedLetters.isNotEmpty() && casedLetters.all { it.isUpperCase() }
}
