package eu.kanade.tachiyomi.data.track.jellyfin

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.Source
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import okhttp3.Dns
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.domain.manga.model.Manga as Anime
import tachiyomi.domain.track.model.Track as DomainTrack

class Jellyfin(id: Long) : BaseTracker(id, "Jellyfin"), EnhancedTracker {

    companion object {
        const val UNSEEN = 1L
        const val WATCHING = 2L
        const val COMPLETED = 3L
    }

    override val client by lazy {
        networkService.client.newBuilder()
            .addInterceptor(JellyfinInterceptor())
            .dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
            .build()
    }

    val api by lazy { JellyfinApi(id, client) }

    override fun getLogo() = R.drawable.brand_jellyfin

    override fun getStatusList(): List<Long> = listOf(UNSEEN, WATCHING, COMPLETED)

    override fun getStatus(status: Long): StringResource? = when (status) {
        UNSEEN -> AYMR.strings.unseen
        WATCHING -> AYMR.strings.watching
        COMPLETED -> MR.strings.completed
        else -> null
    }

    override fun getReadingStatus(): Long = WATCHING

    override fun getRereadingStatus(): Long = -1

    override fun getCompletionStatus(): Long = COMPLETED

    override fun getScoreList(): ImmutableList<String> = persistentListOf()

    override fun displayScore(track: DomainTrack): String = ""

    override suspend fun update(track: Track, didReadChapter: Boolean): Track {
        return api.updateProgress(track)
    }

    override suspend fun bind(track: Track, hasReadChapters: Boolean): Track {
        return track
    }

    override suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata {
        throw NotImplementedError("Not implemented.")
    }

    override suspend fun search(query: String): List<TrackSearch> =
        throw Exception("Not used")

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getTrackSearch(track.tracking_url)
        track.copyPersonalFrom(remoteTrack)
        track.total_episodes = remoteTrack.total_episodes
        return track
    }

    override suspend fun login(username: String, password: String) {
        saveCredentials("user", "pass")
    }

    override fun loginNoop() {
        saveCredentials("user", "pass")
    }

    override fun getAcceptedSources() = listOf("eu.kanade.tachiyomi.animeextension.all.jellyfin.Jellyfin")

    override suspend fun match(manga: Manga): TrackSearch? =
        try {
            api.getTrackSearch(manga.url)
        } catch (_: Exception) {
            null
        }

    // AM -->
    override suspend fun matchSeason(anime: Anime): TrackSearch {
        return TrackSearch.create(id).apply {
            title = anime.title
            tracking_url = anime.url
        }
    }
    // <-- AM

    override fun isTrackFrom(track: DomainTrack, manga: Manga, source: Source?): Boolean =
        track.remoteUrl == manga.url && source?.let { accept(it) } == true

    override fun migrateTrack(track: DomainTrack, manga: Manga, newSource: Source): DomainTrack? {
        return if (accept(newSource)) {
            track.copy(remoteUrl = manga.url)
        } else {
            null
        }
    }

    // KMK -->
    override fun hasNotStartedReading(status: Long): Boolean = status == UNSEEN
    // KMK <--
}
