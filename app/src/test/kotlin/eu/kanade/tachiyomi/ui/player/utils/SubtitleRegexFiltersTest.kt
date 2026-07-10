package eu.kanade.tachiyomi.ui.player.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubtitleRegexFiltersTest {
    @Test
    fun `removes ascii parenthesized speaker prefix`() {
        assertEquals(
            "\u5834\u6240\u306f?",
            "(\u30b8\u30e7\u30f3\u30fb\u30b8\u30e3\u30a4\u30a2\u30f3\u30c8) \u5834\u6240\u306f?"
                .applySubtitleRegexFilters(options(removeSpeakerNames = true)),
        )
    }

    @Test
    fun `removes full width parenthesized speaker prefix`() {
        assertEquals(
            "\u5834\u6240\u306f?",
            "\uff08\u30b8\u30e7\u30f3\u30fb\u30b8\u30e3\u30a4\u30a2\u30f3\u30c8\uff09\u5834\u6240\u306f?"
                .applySubtitleRegexFilters(options(removeSpeakerNames = true)),
        )
    }

    @Test
    fun `removes speaker names after dialogue dashes`() {
        assertEquals(
            "- \u308f\u30fc\u3044\uff01\n- \u5de8\u4eba\u65cf\u306e\u5b50\u4f9b\uff1f",
            (
                "- (\u5b50\u4f9b\u305f\u3061) \u308f\u30fc\u3044\uff01\n" +
                    "- (\u30ca\u30df) \u5de8\u4eba\u65cf\u306e\u5b50\u4f9b\uff1f"
            ).applySubtitleRegexFilters(options(removeSpeakerNames = true)),
        )
    }

    @Test
    fun `removes full width speaker names after unicode dialogue dashes`() {
        assertEquals(
            "\u2014 \u5834\u6240\u306f?",
            "\u2014 \uff08\u30ca\u30df\uff09 \u5834\u6240\u306f?"
                .applySubtitleRegexFilters(options(removeSpeakerNames = true)),
        )
    }

    @Test
    fun `speaker filter keeps parenthesized text outside the speaker prefix`() {
        assertEquals(
            "\u5834\u6240\u306f? (\u6771\u4eac)",
            "\u5834\u6240\u306f? (\u6771\u4eac)".applySubtitleRegexFilters(options(removeSpeakerNames = true)),
        )
    }

    @Test
    fun `music symbol filter removes common subtitle music markers`() {
        assertEquals(
            "hello",
            "\u266a hello \u266b".applySubtitleRegexFilters(options(removeMusicSymbols = true)),
        )
    }

    private fun options(
        removeSpeakerNames: Boolean = false,
        mergeMultiline: Boolean = false,
        removeBracketedText: Boolean = false,
        removeUppercaseLines: Boolean = false,
        removeMusicSymbols: Boolean = false,
        removeCurlyBracedText: Boolean = false,
        customRegexEnabled: Boolean = false,
        customRegexPattern: String = "",
    ): SubtitleRegexFilterOptions {
        return SubtitleRegexFilterOptions(
            removeSpeakerNames = removeSpeakerNames,
            mergeMultiline = mergeMultiline,
            removeBracketedText = removeBracketedText,
            removeUppercaseLines = removeUppercaseLines,
            removeMusicSymbols = removeMusicSymbols,
            removeCurlyBracedText = removeCurlyBracedText,
            customRegexEnabled = customRegexEnabled,
            customRegexPattern = customRegexPattern,
        )
    }
}
