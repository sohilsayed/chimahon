package chimahon.dictionary.ko

import chimahon.LookupResult
import chimahon.TermResult

object KoreanTextProcessors {

    // Hangul algorithm constants
    private const val SBase = 0xAC00
    private const val LBase = 0x1100
    private const val VBase = 0x1161
    private const val TBase = 0x11A7
    private const val LCount = 19
    private const val VCount = 21
    private const val TCount = 28
    private const val NCount = VCount * TCount

    // Standard Jamo (U+1100+) to Compatibility Jamo (U+3131+) mapping
    private val lToCompat = mapOf(
        0x1100 to 0x3131, 0x1101 to 0x3132, 0x1102 to 0x3134,
        0x1103 to 0x3137, 0x1104 to 0x3138, 0x1105 to 0x3139,
        0x1106 to 0x3141, 0x1107 to 0x3142, 0x1108 to 0x3143,
        0x1109 to 0x3145, 0x110A to 0x3146, 0x110B to 0x3147,
        0x110C to 0x3148, 0x110D to 0x3149, 0x110E to 0x314A,
        0x110F to 0x314B, 0x1110 to 0x314C, 0x1111 to 0x314D,
        0x1112 to 0x314E,
    )

    private val vToCompat = mapOf(
        0x1161 to 0x314F, 0x1162 to 0x3150, 0x1163 to 0x3151,
        0x1164 to 0x3152, 0x1165 to 0x3153, 0x1166 to 0x3154,
        0x1167 to 0x3155, 0x1168 to 0x3156, 0x1169 to 0x3157,
        0x116A to 0x3158, 0x116B to 0x3159, 0x116C to 0x315A,
        0x116D to 0x315B, 0x116E to 0x315C, 0x116F to 0x315D,
        0x1170 to 0x315E, 0x1171 to 0x315F, 0x1172 to 0x3160,
        0x1173 to 0x3161, 0x1174 to 0x3162, 0x1175 to 0x3163,
    )

    // Final jamo (U+11A8+) to compatibility jamo
    private val tToCompat = mapOf(
        0x11A8 to 0x3131, 0x11A9 to 0x3132, 0x11AA to 0x3133,
        0x11AB to 0x3134, 0x11AC to 0x3135, 0x11AD to 0x3136,
        0x11AE to 0x3137, 0x11AF to 0x3139, 0x11B0 to 0x313A,
        0x11B1 to 0x313B, 0x11B2 to 0x313C, 0x11B3 to 0x313D,
        0x11B4 to 0x313E, 0x11B5 to 0x313F, 0x11B6 to 0x3140,
        0x11B7 to 0x3141, 0x11B8 to 0x3142, 0x11B9 to 0x3144,
        0x11BA to 0x3145, 0x11BB to 0x3146, 0x11BC to 0x3147,
        0x11BD to 0x3148, 0x11BE to 0x314A, 0x11BF to 0x314B,
        0x11C0 to 0x314C, 0x11C1 to 0x314D, 0x11C2 to 0x314E,
    )

    // Reverse mapping: compatibility jamo -> standard jamo (for assembly)
    private val compatToL = lToCompat.entries.associate { (k, v) -> v to k }
    private val compatToV = vToCompat.entries.associate { (k, v) -> v to k }
    private val compatToT = tToCompat.entries.associate { (k, v) -> v to k }

    // Compatibility jamo ranges
    private const val COMPAT_L_START = 0x3131
    private const val COMPAT_L_END = 0x314E
    private const val COMPAT_V_START = 0x314F
    private const val COMPAT_V_END = 0x3163

    fun isJamo(ch: Char): Boolean {
        val code = ch.code
        return (code in COMPAT_L_START..COMPAT_L_END) || (code in COMPAT_V_START..COMPAT_V_END) || code in 0x3164..0x318E
    }

    fun isHangulSyllable(ch: Char): Boolean {
        val code = ch.code
        return code in SBase..(SBase + LCount * NCount - 1)
    }

    fun disassemble(text: String): String {
        val result = StringBuilder()
        for (ch in text) {
            val code = ch.code
            if (isHangulSyllable(ch)) {
                val sIndex = code - SBase
                val lIndex = sIndex / NCount
                val vIndex = (sIndex % NCount) / TCount
                val tIndex = sIndex % TCount
                result.appendCodePoint(lToCompat[LBase + lIndex] ?: (LBase + lIndex))
                result.appendCodePoint(vToCompat[VBase + vIndex] ?: (VBase + vIndex))
                if (tIndex > 0) {
                    val tCompat = tToCompat[TBase + tIndex]
                    if (tCompat != null) {
                        result.appendCodePoint(tCompat)
                    }
                }
            } else {
                result.append(ch)
            }
        }
        return result.toString()
    }

    fun assemble(text: String): String {
        val result = StringBuilder()
        val chars = text.toList()
        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            val code = c.code
            // Check if this starts a syllable block: initial + vowel + optional final
            if (code in COMPAT_L_START..COMPAT_L_END) {
                val lCompat = code
                val lStandard = compatToL[lCompat]
                if (lStandard != null && i + 1 < chars.size) {
                    val vCode = chars[i + 1].code
                    if (vCode in COMPAT_V_START..COMPAT_V_END) {
                        val vStandard = compatToV[vCode]
                        if (vStandard != null) {
                            var tStandard = 0
                            var consumed = 2
                            if (i + 2 < chars.size) {
                                val tCode = chars[i + 2].code
                                val t = compatToT[tCode]
                                if (t != null) {
                                    tStandard = t - TBase
                                    consumed = 3
                                }
                            }
                            val lIndex = lStandard - LBase
                            val vIndex = vStandard - VBase
                            val sCode = SBase + lIndex * NCount + vIndex * TCount + tStandard
                            result.appendCodePoint(sCode)
                            i += consumed
                            continue
                        }
                    }
                }
            }
            result.append(c)
            i++
        }
        return result.toString()
    }

    fun allVariants(text: String): List<String> {
        val disassembled = disassemble(text)
        return if (disassembled != text) listOf(text, disassembled) else listOf(text)
    }

    fun wrapResults(
        originalQuery: String,
        candidates: List<String>,
        terms: List<TermResult>,
    ): List<LookupResult> {
        // Convert jamo candidates back to Hangul before matching
        val assembledCandidates = candidates.map { assemble(it) }
        val termsByText = terms.groupBy { it.expression }
        return assembledCandidates.flatMap { candidate ->
            val termList = termsByText[candidate]
            if (termList != null && termList.isNotEmpty()) {
                termList.map { term ->
                    LookupResult(
                        matched = originalQuery,
                        deinflected = candidate,
                        process = emptyArray(),
                        term = term,
                        preprocessorSteps = 0,
                    )
                }
            } else {
                // Try original candidates too
                val termList2 = termsByText[candidate]
                if (termList2 != null && termList2.isNotEmpty()) {
                    termList2.map { term ->
                        LookupResult(
                            matched = originalQuery,
                            deinflected = candidate,
                            process = emptyArray(),
                            term = term,
                            preprocessorSteps = 0,
                        )
                    }
                } else {
                    emptyList()
                }
            }
        }
    }
}
