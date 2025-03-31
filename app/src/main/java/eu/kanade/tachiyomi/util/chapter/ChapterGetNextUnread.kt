package eu.kanade.tachiyomi.util.chapter

import eu.kanade.domain.chapter.model.applyFilters
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<Chapter>.getNextUnread(
    manga: Manga,
    downloadManager: DownloadManager,
    // SY -->
    mergedManga: Map<Long, Manga>,
    // SY <--
): Chapter? {
    return applyFilters(manga, downloadManager/* SY --> */, mergedManga/* SY <-- */).let { chapters ->
        if (manga.sortDescending()) {
            chapters.findLast { !it.seen }
        } else {
            chapters.find { !it.seen }
        }
    }
}

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(manga: Manga): Chapter? {
    return applyFilters(manga).let { chapters ->
        if (manga.sortDescending()) {
            chapters.findLast { !it.chapter.seen }
        } else {
            chapters.find { !it.chapter.seen }
        }
    }?.chapter
}
