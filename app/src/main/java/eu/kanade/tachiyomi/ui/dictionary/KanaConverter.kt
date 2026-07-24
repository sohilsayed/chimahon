package eu.kanade.tachiyomi.ui.dictionary

import kotlin.math.max
import kotlin.math.min

object KanaConverter {

    // Ported from Yomitan ref/yomitan/ext/js/language/ja/japanese-kana-romaji-dicts.js
    // Mozc romaji→hiragana mapping, ordered by source length descending so replaceAll
    // matches the longest possible sequence first.
    private val ROMAJI_TO_HIRAGANA: List<Pair<String, String>> = listOf(
        // Double letters – must be matched first
        "qq" to "っq",
        "vv" to "っv",
        "ll" to "っl",
        "xx" to "っx",
        "kk" to "っk",
        "gg" to "っg",
        "ss" to "っs",
        "zz" to "っz",
        "jj" to "っj",
        "tt" to "っt",
        "dd" to "っd",
        "hh" to "っh",
        "ff" to "っf",
        "bb" to "っb",
        "pp" to "っp",
        "mm" to "っm",
        "yy" to "っy",
        "rr" to "っr",
        "ww" to "っw",
        "cc" to "っc",

        // Length 4
        "hwyu" to "ふゅ",

        // Length 3
        "vya" to "ゔゃ",
        "vyi" to "ゔぃ",
        "vyu" to "ゔゅ",
        "vye" to "ゔぇ",
        "vyo" to "ゔょ",
        "kya" to "きゃ",
        "kyi" to "きぃ",
        "kyu" to "きゅ",
        "kye" to "きぇ",
        "kyo" to "きょ",
        "gya" to "ぎゃ",
        "gyi" to "ぎぃ",
        "gyu" to "ぎゅ",
        "gye" to "ぎぇ",
        "gyo" to "ぎょ",
        "sya" to "しゃ",
        "syi" to "しぃ",
        "syu" to "しゅ",
        "sye" to "しぇ",
        "syo" to "しょ",
        "sha" to "しゃ",
        "shi" to "し",
        "shu" to "しゅ",
        "she" to "しぇ",
        "sho" to "しょ",
        "zya" to "じゃ",
        "zyi" to "じぃ",
        "zyu" to "じゅ",
        "zye" to "じぇ",
        "zyo" to "じょ",
        "tya" to "ちゃ",
        "tyi" to "ちぃ",
        "tyu" to "ちゅ",
        "tye" to "ちぇ",
        "tyo" to "ちょ",
        "cha" to "ちゃ",
        "chi" to "ち",
        "chu" to "ちゅ",
        "che" to "ちぇ",
        "cho" to "ちょ",
        "cya" to "ちゃ",
        "cyi" to "ちぃ",
        "cyu" to "ちゅ",
        "cye" to "ちぇ",
        "cyo" to "ちょ",
        "dya" to "ぢゃ",
        "dyi" to "ぢぃ",
        "dyu" to "ぢゅ",
        "dye" to "ぢぇ",
        "dyo" to "ぢょ",
        "tsa" to "つぁ",
        "tsi" to "つぃ",
        "tse" to "つぇ",
        "tso" to "つぉ",
        "tha" to "てゃ",
        "thi" to "てぃ",
        "thu" to "てゅ",
        "the" to "てぇ",
        "tho" to "てょ",
        "dha" to "でゃ",
        "dhi" to "でぃ",
        "dhu" to "でゅ",
        "dhe" to "でぇ",
        "dho" to "でょ",
        "twa" to "とぁ",
        "twi" to "とぃ",
        "twu" to "とぅ",
        "twe" to "とぇ",
        "two" to "とぉ",
        "dwa" to "どぁ",
        "dwi" to "どぃ",
        "dwu" to "どぅ",
        "dwe" to "どぇ",
        "dwo" to "どぉ",
        "nya" to "にゃ",
        "nyi" to "にぃ",
        "nyu" to "にゅ",
        "nye" to "にぇ",
        "nyo" to "にょ",
        "hya" to "ひゃ",
        "hyi" to "ひぃ",
        "hyu" to "ひゅ",
        "hye" to "ひぇ",
        "hyo" to "ひょ",
        "bya" to "びゃ",
        "byi" to "びぃ",
        "byu" to "びゅ",
        "bye" to "びぇ",
        "byo" to "びょ",
        "pya" to "ぴゃ",
        "pyi" to "ぴぃ",
        "pyu" to "ぴゅ",
        "pye" to "ぴぇ",
        "pyo" to "ぴょ",
        "fya" to "ふゃ",
        "fyu" to "ふゅ",
        "fyo" to "ふょ",
        "hwa" to "ふぁ",
        "hwi" to "ふぃ",
        "hwe" to "ふぇ",
        "hwo" to "ふぉ",
        "mya" to "みゃ",
        "myi" to "みぃ",
        "myu" to "みゅ",
        "mye" to "みぇ",
        "myo" to "みょ",
        "rya" to "りゃ",
        "ryi" to "りぃ",
        "ryu" to "りゅ",
        "rye" to "りぇ",
        "ryo" to "りょ",
        "lyi" to "ぃ",
        "xyi" to "ぃ",
        "lye" to "ぇ",
        "xye" to "ぇ",
        "xka" to "ヵ",
        "xke" to "ヶ",
        "lka" to "ヵ",
        "lke" to "ヶ",
        "kwa" to "くぁ",
        "kwi" to "くぃ",
        "kwu" to "くぅ",
        "kwe" to "くぇ",
        "kwo" to "くぉ",
        "gwa" to "ぐぁ",
        "gwi" to "ぐぃ",
        "gwu" to "ぐぅ",
        "gwe" to "ぐぇ",
        "gwo" to "ぐぉ",
        "swa" to "すぁ",
        "swi" to "すぃ",
        "swu" to "すぅ",
        "swe" to "すぇ",
        "swo" to "すぉ",
        "zwa" to "ずぁ",
        "zwi" to "ずぃ",
        "zwu" to "ずぅ",
        "zwe" to "ずぇ",
        "zwo" to "ずぉ",
        "jya" to "じゃ",
        "jyi" to "じぃ",
        "jyu" to "じゅ",
        "jye" to "じぇ",
        "jyo" to "じょ",
        "xtsu" to "っ",
        "ltsu" to "っ",
        "tsu" to "つ",
        "xtu" to "っ",
        "ltu" to "っ",
        "xya" to "ゃ",
        "lya" to "ゃ",
        "wyi" to "ゐ",
        "xyu" to "ゅ",
        "lyu" to "ゅ",
        "wye" to "ゑ",
        "xyo" to "ょ",
        "lyo" to "ょ",
        "xwa" to "ゎ",
        "lwa" to "ゎ",
        "wha" to "うぁ",
        "whi" to "うぃ",
        "whu" to "う",
        "whe" to "うぇ",
        "who" to "うぉ",

        // Length 2
        "nn" to "ん",
        "n'" to "ん",
        "va" to "ゔぁ",
        "vi" to "ゔぃ",
        "vu" to "ゔ",
        "ve" to "ゔぇ",
        "vo" to "ゔぉ",
        "fa" to "ふぁ",
        "fi" to "ふぃ",
        "fe" to "ふぇ",
        "fo" to "ふぉ",
        "xn" to "ん",
        "wu" to "う",
        "xa" to "ぁ",
        "xi" to "ぃ",
        "xu" to "ぅ",
        "xe" to "ぇ",
        "xo" to "ぉ",
        "la" to "ぁ",
        "li" to "ぃ",
        "lu" to "ぅ",
        "le" to "ぇ",
        "lo" to "ぉ",
        "ye" to "いぇ",
        "ka" to "か",
        "ki" to "き",
        "ku" to "く",
        "ke" to "け",
        "ko" to "こ",
        "ga" to "が",
        "gi" to "ぎ",
        "gu" to "ぐ",
        "ge" to "げ",
        "go" to "ご",
        "sa" to "さ",
        "si" to "し",
        "su" to "す",
        "se" to "せ",
        "so" to "そ",
        "ca" to "か",
        "ci" to "し",
        "cu" to "く",
        "ce" to "せ",
        "co" to "こ",
        "qa" to "くぁ",
        "qi" to "くぃ",
        "qu" to "く",
        "qe" to "くぇ",
        "qo" to "くぉ",
        "za" to "ざ",
        "zi" to "じ",
        "zu" to "ず",
        "ze" to "ぜ",
        "zo" to "ぞ",
        "ja" to "じゃ",
        "ji" to "じ",
        "ju" to "じゅ",
        "je" to "じぇ",
        "jo" to "じょ",
        "ta" to "た",
        "ti" to "ち",
        "tu" to "つ",
        "te" to "て",
        "to" to "と",
        "da" to "だ",
        "di" to "ぢ",
        "du" to "づ",
        "de" to "で",
        "do" to "ど",
        "na" to "な",
        "ni" to "に",
        "nu" to "ぬ",
        "ne" to "ね",
        "no" to "の",
        "ha" to "は",
        "hi" to "ひ",
        "hu" to "ふ",
        "fu" to "ふ",
        "he" to "へ",
        "ho" to "ほ",
        "ba" to "ば",
        "bi" to "び",
        "bu" to "ぶ",
        "be" to "べ",
        "bo" to "ぼ",
        "pa" to "ぱ",
        "pi" to "ぴ",
        "pu" to "ぷ",
        "pe" to "ぺ",
        "po" to "ぽ",
        "ma" to "ま",
        "mi" to "み",
        "mu" to "む",
        "me" to "め",
        "mo" to "も",
        "ya" to "や",
        "yu" to "ゆ",
        "yo" to "よ",
        "ra" to "ら",
        "ri" to "り",
        "ru" to "る",
        "re" to "れ",
        "ro" to "ろ",
        "wa" to "わ",
        "wi" to "うぃ",
        "we" to "うぇ",
        "wo" to "を",

        // Length 1
        "a" to "あ",
        "i" to "い",
        "u" to "う",
        "e" to "え",
        "o" to "お",

        // Special / symbols
        "." to "。",
        "," to "、",
        ":" to "：",
        "/" to "・",
        "!" to "！",
        "?" to "？",
        "~" to "〜",
        "-" to "ー",
        "‘" to "「",
        "’" to "」",
        "“" to "『",
        "”" to "』",
        "[" to "［",
        "]" to "］",
        "(" to "（",
        ")" to "）",
        "{" to "｛",
        "}" to "｝",
        " " to "　",

        // n → ん is last (special case for IME mode)
        "n" to "ん",
    )

    /**
     * Converts romaji text to kana (hiragana for lowercase, katakana for uppercase).
     * Ported from Yomitan's convertToKana() in japanese-wanakana.js
     */
    fun toKana(text: String): String {
        var result = text
        for ((romaji, hiragana) in ROMAJI_TO_HIRAGANA) {
            result = result.replace(romaji, hiragana, ignoreCase = false)
            val upperRomaji = romaji.uppercase()
            if (upperRomaji != romaji) {
                val katakana = hiraganaToKatakana(hiragana)
                result = result.replace(upperRomaji, katakana, ignoreCase = false)
            }
        }
        return fillSokuonGaps(result)
    }

    /**
     * IME-aware conversion that hides a single 'n' or 'ny' immediately before the
     * cursor so the user can continue typing to form na/ni/nu/ne/no or nya/nyu/nyo.
     * Ported from Yomitan's convertToKanaIME() in japanese-wanakana.js
     *
     * @return Pair(convertedText, newCursorPosition)
     */
    fun toKanaIME(text: String, cursorPos: Int): Pair<String, Int> {
        val prevLength = text.length
        val clamped = clampCursor(cursorPos, prevLength)

        val lowered = text.lowercase()
        val kanaString = when {
            // Single 'n' before cursor
            clamped > 0 &&
                lowered[clamped - 1] == 'n' &&
                lowered.slice(0 until clamped - 1).replace("nn", "").lastOrNull() != 'n' -> {
                val n = text.substring(clamped - 1, clamped)
                val beforeN = text.substring(0, clamped - 1)
                val afterN = text.substring(clamped)
                toKana(beforeN) + n + toKana(afterN)
            }
            // 'ny' before cursor
            clamped > 1 &&
                lowered.substring(clamped - 2, clamped) == "ny" -> {
                val ny = text.substring(clamped - 2, clamped)
                val beforeNy = text.substring(0, clamped - 2)
                val afterNy = text.substring(clamped)
                toKana(beforeNy) + ny + toKana(afterNy)
            }
            else -> toKana(text)
        }

        val selectionOffset = kanaString.length - prevLength
        return kanaString to clampCursor(clamped + selectionOffset, kanaString.length)
    }

    /**
     * Only converts alphabetic (romaji) segments to hiragana, leaving existing
     * kana/kanji/symbols untouched. Ported from Yomitan's convertAlphabeticToKana().
     */
    fun convertAlphabeticToKana(text: String): String {
        val result = StringBuilder()
        val part = StringBuilder()

        for (char in text) {
            val code = char.code
            when {
                // Lowercase a-z (pass through)
                code in 0x61..0x7a -> part.append(char)
                // Uppercase A-Z -> lowercase
                code in 0x41..0x5a -> part.append((code + 0x20).toChar())
                // Fullwidth A-Z (U+FF21-U+FF3A) -> ASCII lowercase
                code in 0xff21..0xff3a -> part.append((code - 0xff21 + 0x61).toChar())
                // Fullwidth a-z (U+FF41-U+FF5A) -> ASCII lowercase
                code in 0xff41..0xff5a -> part.append((code - 0xff41 + 0x61).toChar())
                // Hyphen / fullwidth hyphen
                code == 0x2d || code == 0xff0d -> part.append('-')
                else -> {
                    if (part.isNotEmpty()) {
                        result.append(toKana(part.toString()))
                        part.clear()
                    }
                    result.append(char)
                }
            }
        }

        if (part.isNotEmpty()) {
            result.append(toKana(part.toString()))
        }
        return result.toString()
    }

    // ── helpers ────────────────────────────────────────────────────────────

    // Hiragana: U+3041 (ぁ) .. U+3096 (ゖ)
    // Katakana: U+30A1 (ァ) .. U+30F6 (ヶ)
    // Katakana offset = U+30A1 - U+3041 = 0x60
    private const val HIRAGANA_START = '\u3041'
    private const val HIRAGANA_END = '\u3096'
    private val HIRAGANA_RANGE = HIRAGANA_START..HIRAGANA_END
    private const val KATAKANA_OFFSET = 0x30a1 - 0x3041 // 0x60

    private fun hiraganaToKatakana(text: String): String {
        return text.map { c ->
            if (c in HIRAGANA_RANGE) c + KATAKANA_OFFSET else c
        }.joinToString("")
    }

    /**
     * Fills gaps in sokuon (っ) that replaceAll misses when run non-iteratively.
     * Ported from Yomitan's fillSokuonGaps().
     */
    private fun fillSokuonGaps(text: String): String {
        return text
            .replace(Regex("っ[a-z](?=っ)"), "っっ")
            .replace(Regex("ッ[A-Z](?=ッ)"), "ッッ")
    }

    private fun clampCursor(cursor: Int, length: Int): Int {
        return max(0, min(cursor, length))
    }
}
