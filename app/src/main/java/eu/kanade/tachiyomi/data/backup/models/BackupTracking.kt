package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.track.model.Track

@Serializable
data class BackupTracking(
    // in 1.x some of these values have different types or names
    @ProtoNumber(1) var syncId: Int,
    // LibraryId is not null in 1.x
    @ProtoNumber(2) var libraryId: Long,
    @Deprecated("Use mediaId instead", level = DeprecationLevel.WARNING)
    @ProtoNumber(3)
    var mediaIdInt: Int = 0,
    // trackingUrl is called mediaUrl in 1.x
    @ProtoNumber(4) var trackingUrl: String = "",
    @ProtoNumber(5) var title: String = "",
    // lastEpisodeSeen is called last seen, and it has been changed to a float in 1.x
    @ProtoNumber(6) var lastEpisodeSeen: Float = 0F,
    @ProtoNumber(7) var totalEpisodes: Int = 0,
    @ProtoNumber(8) var score: Float = 0F,
    @ProtoNumber(9) var status: Int = 0,
    // startedReadingDate is called startReadTime in 1.x
    @ProtoNumber(10) var startedWatchingDate: Long = 0,
    // finishedReadingDate is called endReadTime in 1.x
    @ProtoNumber(11) var finishedWatchingDate: Long = 0,
    @ProtoNumber(100) var mediaId: Long = 0,
) {

    @Suppress("DEPRECATION")
    fun getTrackImpl(): Track {
        return Track(
            id = -1,
            animeId = -1,
            trackerId = this@BackupTracking.syncId.toLong(),
            remoteId = if (this@BackupTracking.mediaIdInt != 0) {
                this@BackupTracking.mediaIdInt.toLong()
            } else {
                this@BackupTracking.mediaId
            },
            libraryId = this@BackupTracking.libraryId,
            title = this@BackupTracking.title,
            lastEpisodeSeen = this@BackupTracking.lastEpisodeSeen.toDouble(),
            totalEpisodes = this@BackupTracking.totalEpisodes.toLong(),
            score = this@BackupTracking.score.toDouble(),
            status = this@BackupTracking.status.toLong(),
            startDate = this@BackupTracking.startedWatchingDate,
            finishDate = this@BackupTracking.finishedWatchingDate,
            remoteUrl = this@BackupTracking.trackingUrl,
        )
    }
}

val backupTrackMapper = {
        _: Long,
        _: Long,
        syncId: Long,
        mediaId: Long,
        libraryId: Long?,
        title: String,
        lastEpisodeSeen: Double,
        totalEpisodes: Long,
        status: Long,
        score: Double,
        remoteUrl: String,
        startDate: Long,
        finishDate: Long,
    ->
    BackupTracking(
        syncId = syncId.toInt(),
        mediaId = mediaId,
        // forced not null so its compatible with 1.x backup system
        libraryId = libraryId ?: 0,
        title = title,
        lastEpisodeSeen = lastEpisodeSeen.toFloat(),
        totalEpisodes = totalEpisodes.toInt(),
        score = score.toFloat(),
        status = status.toInt(),
        startedWatchingDate = startDate,
        finishedWatchingDate = finishDate,
        trackingUrl = remoteUrl,
    )
}
