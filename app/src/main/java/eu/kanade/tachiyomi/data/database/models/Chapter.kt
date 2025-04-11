@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.source.model.SChapter
import java.io.Serializable
import tachiyomi.domain.chapter.model.Chapter as DomainChapter

interface Chapter : SChapter, Serializable {

    var id: Long?

    var manga_id: Long?

    var read: Boolean

    var bookmark: Boolean

    // AM (FILLERMARK) -->
    var fillermark: Boolean
    // <-- AM (FILLERMARK)

    var last_page_read: Long

    var total_pages: Long

    var date_fetch: Long

    var source_order: Int

    var last_modified: Long

    var version: Long

    var anime_id: Long?
        get() = manga_id
        set(value) {
            manga_id = value
        }
    var seen: Boolean
        get() = read
        set(value) {
            read = value
        }
    var last_second_seen: Long
        get() = last_page_read
        set(value) {
            last_page_read = value
        }
    var total_seconds: Long
        get() = total_pages
        set(value) {
            total_pages = value
        }
}

fun Chapter.toDomainChapter(): DomainChapter? {
    if (id == null || manga_id == null) return null
    return DomainChapter(
        id = id!!,
        mangaId = manga_id!!,
        read = read,
        bookmark = bookmark,
        // AM (FILLERMARK) -->
        fillermark = fillermark,
        // <-- AM (FILLERMARK)
        lastPageRead = last_page_read,
        totalPages = total_pages,
        dateFetch = date_fetch,
        sourceOrder = source_order.toLong(),
        url = url,
        name = name,
        dateUpload = date_upload,
        chapterNumber = chapter_number.toDouble(),
        scanlator = scanlator,
        lastModifiedAt = last_modified,
        version = version,
    )
}
