@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.source.model.SChapter
import java.io.Serializable
import tachiyomi.domain.chapter.model.Chapter as DomainChapter

interface Chapter : SChapter, Serializable {

    var id: Long?

    var anime_id: Long?

    var seen: Boolean

    var bookmark: Boolean

    // AM (FILLERMARK) -->
    var fillermark: Boolean
    // <-- AM (FILLERMARK)

    var last_second_seen: Long

    var total_seconds: Long

    var date_fetch: Long

    var source_order: Int

    var last_modified: Long

    var version: Long
}

fun Chapter.toDomainEpisode(): DomainChapter? {
    if (id == null || anime_id == null) return null
    return DomainChapter(
        id = id!!,
        animeId = anime_id!!,
        seen = seen,
        bookmark = bookmark,
        // AM (FILLERMARK) -->
        fillermark = fillermark,
        // <-- AM (FILLERMARK)
        lastSecondSeen = last_second_seen,
        totalSeconds = total_seconds,
        dateFetch = date_fetch,
        sourceOrder = source_order.toLong(),
        url = url,
        name = name,
        dateUpload = date_upload,
        episodeNumber = episode_number.toDouble(),
        scanlator = scanlator,
        lastModifiedAt = last_modified,
        version = version,
    )
}
