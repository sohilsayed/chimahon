package eu.kanade.tachiyomi.ui.player

enum class VideoAspect {
    Fit,
    Crop,
    Stretch,
}

enum class SingleActionGesture {
    None,
    Seek,
    PlayPause,
}

enum class Decoder(val title: String, val value: String) {
    AutoCopy("Auto", "auto-copy"),
    Auto("Auto", "auto"),
    SW("SW", "no"),
    HW("HW", "mediacodec-copy"),
    HWPlus("HW+", "mediacodec"),
}

enum class Debanding {
    None,
    CPU,
    GPU,
}

enum class Sheets {
    None,
    PlaybackSpeed,
    SubtitleTracks,
    AudioTracks,
    QualityTracks,
    More,
}

enum class Panels {
    None,
    SubtitleSettings,
    SubtitleDelay,
    AudioDelay,
    VideoFilters,
}

sealed class Dialogs {
    data object None : Dialogs()
    data object EpisodeList : Dialogs()
}

enum class VideoFilters(
    val mpvProperty: String,
) {
    BRIGHTNESS("brightness"),
    SATURATION("saturation"),
    CONTRAST("contrast"),
    GAMMA("gamma"),
    HUE("hue"),
}
