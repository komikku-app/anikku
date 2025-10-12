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
        it.chapter_number = chapterNumber.toFloat()
        // AY -->
        it.fillermark = fillermark
        // <-- AY
        it.scanlator = scanlator
        // AY -->
        it.summary = summary
        it.preview_url = previewUrl
        // <-- AY
    }
}

fun Chapter.copyFromSChapter(sChapter: SChapter): Chapter {
    return this.copy(
        name = sChapter.name,
        url = sChapter.url,
        dateUpload = sChapter.date_upload,
        chapterNumber = sChapter.chapter_number.toDouble(),
        // AY -->
        fillermark = sChapter.fillermark,
        // <-- AY
        scanlator = sChapter.scanlator?.ifBlank { null }?.trim(),
        // AY -->
        summary = sChapter.summary?.ifBlank { null },
        previewUrl = sChapter.preview_url?.ifBlank { null },
        // <-- AY
    )
}

fun Chapter.toDbChapter(): DbChapter = ChapterImpl().also {
    it.id = id
    it.manga_id = mangaId
    it.url = url
    it.name = name
    it.scanlator = scanlator
    // AY -->
    it.summary = summary
    it.preview_url = previewUrl
    // <-- AY
    it.read = read
    it.bookmark = bookmark
    // AY -->
    it.fillermark = fillermark
    // <-- AY
    it.last_page_read = lastPageRead
    it.total_pages = totalPages
    it.date_fetch = dateFetch
    it.date_upload = dateUpload
    it.chapter_number = chapterNumber.toFloat()
    it.source_order = sourceOrder.toInt()
    it.last_modified = lastModifiedAt
}
