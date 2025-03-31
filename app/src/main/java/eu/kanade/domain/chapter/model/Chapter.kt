package eu.kanade.domain.chapter.model

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.source.model.SChapter
import tachiyomi.domain.chapter.model.Chapter
import eu.kanade.tachiyomi.data.database.models.Chapter as DbChapter

// TODO: Remove when all deps are migrated
fun Chapter.toSChapter(): SChapter {
    return SChapter.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload
        it.episode_number = episodeNumber.toFloat()
        it.scanlator = scanlator
    }
}

fun Chapter.copyFromSChapter(sChapter: SChapter): Chapter {
    return this.copy(
        name = sChapter.name,
        url = sChapter.url,
        dateUpload = sChapter.date_upload,
        episodeNumber = sChapter.episode_number.toDouble(),
        scanlator = sChapter.scanlator?.ifBlank { null }?.trim(),
    )
}

fun Chapter.toDbChapter(): DbChapter = ChapterImpl().also {
    it.id = id
    it.anime_id = animeId
    it.url = url
    it.name = name
    it.scanlator = scanlator
    it.seen = seen
    it.bookmark = bookmark
    // AM (FILLERMARK) -->
    it.fillermark = fillermark
    // <-- AM (FILLERMARK)
    it.last_second_seen = lastSecondSeen
    it.total_seconds = totalSeconds
    it.date_fetch = dateFetch
    it.date_upload = dateUpload
    it.episode_number = episodeNumber.toFloat()
    it.source_order = sourceOrder.toInt()
    it.last_modified = lastModifiedAt
}
