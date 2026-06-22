package eu.kanade.presentation.entries

enum class DownloadAction {
    NEXT_1_EPISODE,
    NEXT_5_EPISODES,
    NEXT_10_EPISODES,
    NEXT_25_EPISODES,
    UNSEEN_EPISODES,
}

enum class EditCoverAction {
    EDIT,
    DELETE,
}

enum class AnimeScreenItem {
    INFO_BOX,
    ACTION_ROW,
    DESCRIPTION_WITH_TAG,
    RELATED_ANIME,
    ITEM_HEADER,
    EPISODE,
    AIRING_TIME,
}
