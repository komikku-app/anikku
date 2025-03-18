package eu.kanade.presentation.anime

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
    EPISODE_HEADER,
    EPISODE,
    AIRING_TIME,

    // KMK -->
    RELATED_ANIMES,
    // KMK <--
}
