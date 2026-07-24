package chimahon.ocr

enum class OcrLanguage(
    /** BCP-47 code sent to the Lens API (LocaleContext.language). */
    val bcp47: String,
) {
    JAPANESE("ja"),
    ENGLISH("en"),
    CHINESE("zh"),
    KOREAN("ko"),
    ARABIC("ar"),
    SPANISH("es"),
    FRENCH("fr"),
    GERMAN("de"),
    PORTUGUESE("pt"),
    BULGARIAN("bg"),
    CZECH("cs"),
    DANISH("da"),
    GREEK("el"),
    ESTONIAN("et"),
    PERSIAN("fa"),
    FINNISH("fi"),
    HEBREW("he"),
    HINDI("hi"),
    HUNGARIAN("hu"),
    INDONESIAN("id"),
    ITALIAN("it"),
    LATIN("la"),
    LAO("lo"),
    LATVIAN("lv"),
    GEORGIAN("ka"),
    KANNADA("kn"),
    KHMER("km"),
    MONGOLIAN("mn"),
    MALTESE("mt"),
    DUTCH("nl"),
    NORWEGIAN("no"),
    POLISH("pl"),
    ROMANIAN("ro"),
    RUSSIAN("ru"),
    SWEDISH("sv"),
    THAI("th"),
    TAGALOG("tl"),
    TURKISH("tr"),
    UKRAINIAN("uk"),
    VIETNAMESE("vi"),
    WELSH("cy"),
    CANTONESE("yue"),
    ;

    val prefersVertical: Boolean
        get() = this == JAPANESE || this == CHINESE || this == CANTONESE

    val prefersNoSpace: Boolean
        get() = this == JAPANESE || this == CHINESE || this == CANTONESE

    val isJapanese: Boolean
        get() = this == JAPANESE

    val isRtl: Boolean
        get() = this == ARABIC || this == HEBREW
}
