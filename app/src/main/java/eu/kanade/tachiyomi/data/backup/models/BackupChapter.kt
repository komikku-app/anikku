package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.chapter.model.Chapter

@Serializable
data class BackupChapter(
    // in 1.x some of these values have different names
    // url is called key in 1.x
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var seen: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    // AM (FILLERMARK) -->
    @ProtoNumber(15) var fillermark: Boolean = false,
    // <-- AM (FILLERMARK)
    // lastPageRead is called progress in 1.x
    @ProtoNumber(6) var lastSecondSeen: Long = 0,
    @ProtoNumber(16) var totalSeconds: Long = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    // episodeNumber is called number is 1.x
    @ProtoNumber(9) var episodeNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Long = 0,
    @ProtoNumber(11) var lastModifiedAt: Long = 0,
    @ProtoNumber(12) var version: Long = 0,
) {
    fun toChapterImpl(): Chapter {
        return Chapter.create().copy(
            url = this@BackupChapter.url,
            name = this@BackupChapter.name,
            episodeNumber = this@BackupChapter.episodeNumber.toDouble(),
            scanlator = this@BackupChapter.scanlator,
            seen = this@BackupChapter.seen,
            bookmark = this@BackupChapter.bookmark,
            // AM (FILLERMARK) -->
            fillermark = this@BackupChapter.fillermark,
            // <-- AM (FILLERMARK)
            lastSecondSeen = this@BackupChapter.lastSecondSeen,
            totalSeconds = this@BackupChapter.totalSeconds,
            dateFetch = this@BackupChapter.dateFetch,
            dateUpload = this@BackupChapter.dateUpload,
            sourceOrder = this@BackupChapter.sourceOrder,
            lastModifiedAt = this@BackupChapter.lastModifiedAt,
            version = this@BackupChapter.version,
        )
    }
}

val backupChapterMapper = {
        _: Long,
        _: Long,
        url: String,
        name: String,
        scanlator: String?,
        seen: Boolean,
        bookmark: Boolean,
        // AM (FILLERMARK) -->
        fillermark: Boolean,
        // <-- AM (FILLERMARK)
        lastSecondSeen: Long,
        totalSeconds: Long,
        episodeNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        _: Long,
    ->
    BackupChapter(
        url = url,
        name = name,
        episodeNumber = episodeNumber.toFloat(),
        scanlator = scanlator,
        seen = seen,
        bookmark = bookmark,
        // AM (FILLERMARK) -->
        fillermark = fillermark,
        // <-- AM (FILLERMARK)
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        sourceOrder = sourceOrder,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}
