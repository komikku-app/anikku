package eu.kanade.test

import android.graphics.Color
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackAnimeMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR

data class DummyTracker(
    override val id: Long,
    override val name: String,
    override val supportsWatchingDates: Boolean = false,
    override val isLoggedIn: Boolean = false,
    override val isLoggedInFlow: Flow<Boolean> = flowOf(false),
    val valLogoColor: Int = Color.rgb(18, 25, 35),
    val valLogo: Int = R.drawable.ic_tracker_anilist,
    val valStatuses: List<Long> = (1L..6L).toList(),
    val valWatchingStatus: Long = 1L,
    val valRewatchingStatus: Long = 1L,
    val valCompletionStatus: Long = 2L,
    val valScoreList: ImmutableList<String> = (0..10).map(Int::toString).toImmutableList(),
    val val10PointScore: Double = 5.4,
    val valSearchResults: List<TrackSearch> = listOf(),
) : Tracker {

    override val client: OkHttpClient
        get() = TODO("Not yet implemented")

    override fun getLogoColor(): Int = valLogoColor

    override fun getLogo(): Int = valLogo

    override fun getStatusListAnime(): List<Long> = valStatuses

    override fun getStatusForAnime(status: Long): StringResource? = when (status) {
        1L -> MR.strings.watching
        2L -> MR.strings.plan_to_watch
        3L -> MR.strings.completed
        4L -> MR.strings.on_hold
        5L -> MR.strings.dropped
        6L -> MR.strings.repeating_anime
        else -> null
    }

    override fun getWatchingStatus(): Long = valWatchingStatus

    override fun getRewatchingStatus(): Long = valWatchingStatus

    override fun getCompletionStatus(): Long = valCompletionStatus

    override fun getScoreList(): ImmutableList<String> = valScoreList

    override fun get10PointScore(track: Track): Double = val10PointScore

    override fun indexToScore(index: Int): Double = getScoreList()[index].toDouble()

    override fun displayScore(track: Track): String =
        track.score.toString()

    override suspend fun update(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        didWatchEpisode: Boolean,
    ): eu.kanade.tachiyomi.data.database.models.Track = track

    override suspend fun bind(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        hasSeenEpisodes: Boolean,
    ): eu.kanade.tachiyomi.data.database.models.Track = track

    override suspend fun search(query: String): List<TrackSearch> = valSearchResults

    override suspend fun refresh(
        track: eu.kanade.tachiyomi.data.database.models.Track,
    ): eu.kanade.tachiyomi.data.database.models.Track = track

    override suspend fun login(username: String, password: String) = Unit

    override fun logout() = Unit

    override fun getUsername(): String = "username"

    override fun getPassword(): String = "passw0rd"

    override fun saveCredentials(username: String, password: String) = Unit

    override suspend fun register(
        item: eu.kanade.tachiyomi.data.database.models.Track,
        animeId: Long,
    ) = Unit

    override suspend fun setRemoteAnimeStatus(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        status: Long,
    ) = Unit

    override suspend fun setRemoteLastEpisodeSeen(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        episodeNumber: Int,
    ) = Unit

    override suspend fun setRemoteScore(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        scoreString: String,
    ) = Unit

    override suspend fun setRemoteStartDate(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        epochMillis: Long,
    ) = Unit

    override suspend fun setRemoteFinishDate(
        track: eu.kanade.tachiyomi.data.database.models.Track,
        epochMillis: Long,
    ) = Unit

    override suspend fun getAnimeMetadata(
        track: Track,
    ) = TrackAnimeMetadata(
        0, "test", "test", "test", "test", "test",
    )

    // KMK -->
    override fun hasNotStartedWatching(status: Long): Boolean = status == 2L
    // KMK <--
}
