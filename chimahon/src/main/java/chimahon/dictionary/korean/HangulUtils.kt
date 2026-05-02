package chimahon.dictionary.korean

object HangulUtils {
    private const val HANGUL_BASE = 0xAC00
    private const val CHOSUNG_BASE = 0x1100
    private const val JUNGSUNG_BASE = 0x1161
    private const val JONGSUNG_BASE = 0x11A7

    private val CHOSUNG_LIST = listOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )
    private val JUNGSUNG_LIST = listOf(
        'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
        'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    )
    private val JONGSUNG_LIST = listOf(
        ' ', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄴㅈ', 'ㄴㅎ', 'ㄷ', 'ㄹ', 'ㄹㄱ',
        'ㄹㅁ', 'ㄹㅂ', 'ㄹㅅ', 'ㄹㅌ', 'ㄹㅍ', 'ㄹㅎ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    )

    // Jamo to disassembled components (e.g. ㄳ -> ㄱㅅ)
    private val JONGSUNG_COMPLEX = mapOf(
        'ㄳ' to "ㄱㅅ",
        'ㄴㅈ' to "ㄴㅈ",
        'ㄴㅎ' to "ㄴㅎ",
        'ㄹㄱ' to "ㄹㄱ",
        'ㄹㅁ' to "ㄹㅁ",
        'ㄹㅂ' to "ㄹㅂ",
        'ㄹㅅ' to "ㄹㅅ",
        'ㄹㅌ' to "ㄹㅌ",
        'ㄹㅍ' to "ㄹㅍ",
        'ㄹㅎ' to "ㄹㅎ",
        'ㅄ' to "ㅂㅅ"
    )

    private val JUNGSUNG_COMPLEX = mapOf(
        'ㅘ' to "ㅗㅏ",
        'ㅙ' to "ㅗㅐ",
        'ㅚ' to "ㅗㅣ",
        'ㅝ' to "ㅜㅓ",
        'ㅞ' to "ㅜㅔ",
        'ㅟ' to "ㅜㅣ",
        'ㅢ' to "ㅡㅣ"
    )

    fun disassemble(text: String): String {
        val sb = StringBuilder()
        for (c in text) {
            val code = c.code
            if (code in 0xAC00..0xD7A3) {
                val base = code - HANGUL_BASE
                val cho = base / (21 * 28)
                val jung = (base % (21 * 28)) / 28
                val jong = base % 28

                sb.append(CHOSUNG_LIST[cho])
                
                val jungChar = JUNGSUNG_LIST[jung]
                sb.append(JUNGSUNG_COMPLEX[jungChar] ?: jungChar)
                
                if (jong > 0) {
                    val jongChar = JONGSUNG_LIST[jong]
                    sb.append(JONGSUNG_COMPLEX[jongChar] ?: jongChar)
                }
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    // Reassembly is harder because we need to group jamo back into syllables.
    // For deinflection, we might only need disassembly to apply rules, 
    // and then reassemble the result to look it up in the dictionary.
    // However, the dictionary usually stores fully assembled Hangul.
    
    fun assemble(jamo: String): String {
        // Basic reassembly logic (simplified)
        // In a real implementation, we'd need a full state machine to handle various jamo sequences.
        // For now, let's just implement a basic version that handles common cases.
        val sb = StringBuilder()
        var i = 0
        while (i < jamo.length) {
            val choIdx = CHOSUNG_LIST.indexOf(jamo[i])
            if (choIdx != -1 && i + 1 < jamo.length) {
                // Potential syllable
                var jungStr = ""
                var jungIdx = -1
                
                // Try complex jungsung (2 chars)
                if (i + 2 < jamo.length) {
                    val candidate = jamo.substring(i + 1, i + 3)
                    val entry = JUNGSUNG_COMPLEX.entries.find { it.value == candidate }
                    if (entry != null) {
                        jungStr = candidate
                        jungIdx = JUNGSUNG_LIST.indexOf(entry.key)
                    }
                }
                
                if (jungIdx == -1) {
                    // Try simple jungsung (1 char)
                    val candidate = jamo[i + 1]
                    jungIdx = JUNGSUNG_LIST.indexOf(candidate)
                    if (jungIdx != -1) jungStr = candidate.toString()
                }

                if (jungIdx != -1) {
                    i += 1 + jungStr.length
                    
                    var jongIdx = 0
                    var jongStr = ""
                    
                    // Try complex jongsung (2 chars)
                    if (i + 1 < jamo.length) {
                        val candidate = jamo.substring(i, i + 2)
                        val nextIsVowel = if (i + 2 < jamo.length) JUNGSUNG_LIST.contains(jamo[i + 2]) else false
                        
                        if (!nextIsVowel) {
                            val entry = JONGSUNG_COMPLEX.entries.find { it.value == candidate }
                            if (entry != null) {
                                jongIdx = JONGSUNG_LIST.indexOf(entry.key)
                                if (jongIdx != -1) {
                                    jongStr = candidate
                                }
                            }
                        }
                    }
                    
                    if (jongIdx == 0 && i < jamo.length) {
                        // Try simple jongsung
                        val candidate = jamo[i]
                        val nextIsVowel = if (i + 1 < jamo.length) JUNGSUNG_LIST.contains(jamo[i + 1]) else false
                        
                        if (!nextIsVowel) {
                            jongIdx = JONGSUNG_LIST.indexOf(candidate)
                            if (jongIdx != -1) {
                                jongStr = candidate.toString()
                            }
                        }
                    }
                    
                    i += jongStr.length
                    val syllable = (choIdx * 21 + jungIdx) * 28 + jongIdx + HANGUL_BASE
                    sb.append(syllable.toChar())
                    continue
                }
            }
            sb.append(jamo[i])
            i++
        }
        return sb.toString()
    }
}
