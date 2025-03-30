package tachiyomi.domain.chapter.service

import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

fun getChapterSort(
    manga: Manga,
    sortDescending: Boolean = manga.sortDescending(),
): (
    Chapter,
    Chapter,
) -> Int {
    return when (manga.sorting) {
        Manga.EPISODE_SORTING_SOURCE -> when (sortDescending) {
            true -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
            false -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
        }
        Manga.EPISODE_SORTING_NUMBER -> when (sortDescending) {
            true -> { c1, c2 -> c2.episodeNumber.compareTo(c1.episodeNumber) }
            false -> { c1, c2 -> c1.episodeNumber.compareTo(c2.episodeNumber) }
        }
        Manga.EPISODE_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c1, c2 -> c2.dateUpload.compareTo(c1.dateUpload) }
            false -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
        }
        Manga.EPISODE_SORTING_ALPHABET -> when (sortDescending) {
            true -> { c1, c2 -> c2.name.compareToWithCollator(c1.name) }
            false -> { c1, c2 -> c1.name.compareToWithCollator(c2.name) }
        }
        else -> throw NotImplementedError("Invalid episode sorting method: ${manga.sorting}")
    }
}
