package eu.kanade.presentation.manga

enum class DownloadAction {
    NEXT_1_CHAPTER,
    NEXT_5_CHAPTERS,
    NEXT_10_CHAPTERS,
    NEXT_25_CHAPTERS,
    UNSEEN_CHAPTERS,
}

enum class EditCoverAction {
    EDIT,
    DELETE,
}

enum class MangaScreenItem {
    INFO_BOX,
    ACTION_ROW,
    DESCRIPTION_WITH_TAG,

    // SY -->
    INFO_BUTTONS,
    // SY <--

    CHAPTER_HEADER,
    CHAPTER,
    AIRING_TIME,

    // KMK -->
    RELATED_MANGAS,
    // KMK <--
}
