package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.anime.interactor.FetchInterval
import tachiyomi.domain.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.anime.interactor.SetCustomAnimeInfo
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max

class AnimeRestorer(
    private var isSync: Boolean = false,

    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getAnimeByUrlAndSourceId: GetAnimeByUrlAndSourceId = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    fetchInterval: FetchInterval = Injekt.get(),
    // SY -->
    private val setCustomAnimeInfo: SetCustomAnimeInfo = Injekt.get(),
    // SY <--
) {
    private var now = ZonedDateTime.now()
    private var currentFetchWindow = fetchInterval.getWindow(now)

    init {
        now = ZonedDateTime.now()
        currentFetchWindow = fetchInterval.getWindow(now)
    }

    suspend fun sortByNew(backupAnimes: List<BackupAnime>): List<BackupAnime> {
        val urlsBySource = handler.awaitList { animesQueries.getAllAnimeSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupAnimes
            .sortedWith(
                compareBy<BackupAnime> { it.url in urlsBySource[it.source].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restore(
        backupAnime: BackupAnime,
        backupCategories: List<BackupCategory>,
    ) {
        handler.await(inTransaction = true) {
            val dbAnime = findExistingAnime(backupAnime)
            val anime = backupAnime.getAnimeImpl()
            val restoredAnime = if (dbAnime == null) {
                restoreNewAnime(anime)
            } else {
                restoreExistingAnime(anime, dbAnime)
            }

            restoreAnimeDetails(
                anime = restoredAnime,
                episodes = backupAnime.episodes,
                categories = backupAnime.categories,
                backupCategories = backupCategories,
                history = backupAnime.history,
                tracks = backupAnime.tracking,
            )

            if (isSync) {
                animesQueries.resetIsSyncing()
                episodesQueries.resetIsSyncing()
            }
        }
    }

    private suspend fun findExistingAnime(backupAnime: BackupAnime): Anime? {
        return getAnimeByUrlAndSourceId.await(backupAnime.url, backupAnime.source)
    }

    private suspend fun restoreExistingAnime(anime: Anime, dbAnime: Anime): Anime {
        return if (anime.version > dbAnime.version) {
            updateAnime(dbAnime.copyFrom(anime).copy(id = dbAnime.id))
        } else {
            updateAnime(anime.copyFrom(dbAnime).copy(id = dbAnime.id))
        }
    }

    private fun Anime.copyFrom(newer: Anime): Anime {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            // SY -->
            ogAuthor = newer.author,
            ogArtist = newer.artist,
            ogDescription = newer.description,
            ogGenre = newer.genre,
            ogThumbnailUrl = newer.thumbnailUrl,
            ogStatus = newer.status,
            // SY <--
            initialized = this.initialized || newer.initialized,
            version = newer.version,
        )
    }

    suspend fun updateAnime(anime: Anime): Anime {
        handler.await(true) {
            animesQueries.update(
                source = anime.source,
                url = anime.url,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                genre = anime.genre?.joinToString(),
                title = anime.title,
                status = anime.status,
                thumbnailUrl = anime.thumbnailUrl,
                favorite = anime.favorite,
                lastUpdate = anime.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = anime.initialized,
                viewer = anime.viewerFlags,
                episodeFlags = anime.episodeFlags,
                coverLastModified = anime.coverLastModified,
                dateAdded = anime.dateAdded,
                animeId = anime.id,
                updateStrategy = anime.updateStrategy.let(UpdateStrategyColumnAdapter::encode),
                version = anime.version,
                isSyncing = 1,
            )
        }
        return anime
    }

    private suspend fun restoreNewAnime(
        anime: Anime,
    ): Anime {
        return anime.copy(
            initialized = anime.description != null,
            id = insertAnime(anime),
            version = anime.version,
        )
    }

    private suspend fun restoreEpisodes(anime: Anime, backupEpisodes: List<BackupEpisode>) {
        val dbEpisodesByUrl = getEpisodesByAnimeId.await(anime.id)
            .associateBy { it.url }

        val (existingEpisodes, newEpisodes) = backupEpisodes
            .mapNotNull { backupEpisode ->
                val episode = backupEpisode.toEpisodeImpl().copy(animeId = anime.id)

                val dbEpisode = dbEpisodesByUrl[episode.url]

                when {
                    dbEpisode == null -> episode // New episode
                    episode.forComparison() == dbEpisode.forComparison() -> null // Same state; skip
                    else -> updateEpisodeBasedOnSyncState(episode, dbEpisode)
                }
            }
            .partition { it.id > 0 }

        insertNewEpisodes(newEpisodes)
        updateExistingEpisodes(existingEpisodes)
    }

    private fun updateEpisodeBasedOnSyncState(episode: Episode, dbEpisode: Episode): Episode {
        return if (isSync) {
            episode.copy(
                id = dbEpisode.id,
                bookmark = episode.bookmark || dbEpisode.bookmark,
                // AM (FILLERMARK) -->
                fillermark = episode.fillermark || dbEpisode.fillermark,
                // <-- AM (FILLERMARK)
                seen = episode.seen,
                lastSecondSeen = episode.lastSecondSeen,
            )
        } else {
            episode.copyFrom(dbEpisode).let {
                when {
                    dbEpisode.seen && !it.seen -> it.copy(seen = true, lastSecondSeen = dbEpisode.lastSecondSeen)
                    it.lastSecondSeen == 0L && dbEpisode.lastSecondSeen != 0L -> it.copy(
                        lastSecondSeen = dbEpisode.lastSecondSeen,
                    )
                    else -> it
                }
            }
                // KMK -->
                .copy(
                    id = dbEpisode.id,
                    bookmark = episode.bookmark || dbEpisode.bookmark,
                    // AM (FILLERMARK) -->
                    fillermark = episode.fillermark || dbEpisode.fillermark,
                    // <-- AM (FILLERMARK)
                )
            // KMK <--
        }
    }

    private fun Episode.forComparison() =
        this.copy(id = 0L, animeId = 0L, dateFetch = 0L, dateUpload = 0L, lastModifiedAt = 0L, version = 0L)

    private suspend fun insertNewEpisodes(episodes: List<Episode>) {
        handler.await(true) {
            episodes.forEach { episode ->
                episodesQueries.insert(
                    episode.animeId,
                    episode.url,
                    episode.name,
                    episode.scanlator,
                    episode.seen,
                    episode.bookmark,
                    // AM (FILLERMARK) -->
                    episode.fillermark,
                    // <-- AM (FILLERMARK)
                    episode.lastSecondSeen,
                    episode.totalSeconds,
                    episode.episodeNumber,
                    episode.sourceOrder,
                    episode.dateFetch,
                    episode.dateUpload,
                    episode.version,
                )
            }
        }
    }

    private suspend fun updateExistingEpisodes(episodes: List<Episode>) {
        handler.await(true) {
            episodes.forEach { episode ->
                episodesQueries.update(
                    animeId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    seen = episode.seen,
                    bookmark = episode.bookmark,
                    // AM (FILLERMARK) -->
                    fillermark = episode.fillermark,
                    // <-- AM (FILLERMARK)
                    lastSecondSeen = episode.lastSecondSeen,
                    totalSeconds = episode.totalSeconds,
                    episodeNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    episodeId = episode.id,
                    version = episode.version,
                    isSyncing = 0,
                )
            }
        }
    }

    /**
     * Inserts anime and returns id
     *
     * @return id of [Anime], null if not found
     */
    private suspend fun insertAnime(anime: Anime): Long {
        return handler.awaitOneExecutable(true) {
            animesQueries.insert(
                source = anime.source,
                url = anime.url,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                genre = anime.genre,
                title = anime.title,
                status = anime.status,
                thumbnailUrl = anime.thumbnailUrl,
                favorite = anime.favorite,
                lastUpdate = anime.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = anime.initialized,
                viewerFlags = anime.viewerFlags,
                episodeFlags = anime.episodeFlags,
                coverLastModified = anime.coverLastModified,
                dateAdded = anime.dateAdded,
                updateStrategy = anime.updateStrategy,
                version = anime.version,
            )
            animesQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreAnimeDetails(
        anime: Anime,
        episodes: List<BackupEpisode>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
    ): Anime {
        restoreCategories(anime, categories, backupCategories)
        restoreEpisodes(anime, episodes)
        restoreTracking(anime, tracks)
        restoreHistory(anime, history)
        updateAnime.awaitUpdateFetchInterval(anime, now, currentFetchWindow)
        return anime
    }

    /**
     * Restores the categories a anime is in.
     *
     * @param anime the anime whose categories have to be restored.
     * @param categories the categories to restore.
     */
    private suspend fun restoreCategories(
        anime: Anime,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }

        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val animeCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.let { dbCategory ->
                    Pair(anime.id, dbCategory.id)
                }
            }
        }

        if (animeCategoriesToUpdate.isNotEmpty()) {
            handler.await(true) {
                animes_categoriesQueries.deleteAnimeCategoryByAnimeId(anime.id)
                animeCategoriesToUpdate.forEach { (animeId, categoryId) ->
                    animes_categoriesQueries.insert(animeId, categoryId)
                }
            }
        }
    }

    private suspend fun restoreHistory(anime: Anime, backupHistory: List<BackupHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            val dbHistory = handler.awaitOneOrNull { historyQueries.getHistoryByEpisodeUrl(anime.id, history.url) }
            val item = history.getHistoryImpl()

            if (dbHistory == null) {
                val episode = handler.awaitOneOrNull { episodesQueries.getEpisodeByUrl(history.url) }
                return@mapNotNull if (episode == null) {
                    // Episode doesn't exist; skip
                    null
                } else {
                    // New history entry
                    item.copy(episodeId = episode._id)
                }
            }

            // Update history entry
            item.copy(
                id = dbHistory._id,
                episodeId = dbHistory.episode_id,
                seenAt = max(item.seenAt?.time ?: 0L, dbHistory.last_seen?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let { Date(it) },
                watchDuration = max(item.watchDuration, dbHistory.time_watch) - dbHistory.time_watch,
            )
        }

        if (toUpdate.isNotEmpty()) {
            handler.await(true) {
                toUpdate.forEach {
                    historyQueries.upsert(
                        it.episodeId,
                        it.seenAt,
                        it.watchDuration,
                    )
                }
            }
        }
    }

    private suspend fun restoreTracking(anime: Anime, backupTracks: List<BackupTracking>) {
        val dbTrackByTrackerId = getTracks.await(anime.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        animeId = anime.id,
                    )

                if (track.forComparison() == dbTrack.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing track
                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastEpisodeSeen = max(dbTrack.lastEpisodeSeen, track.lastEpisodeSeen),
                )
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            insertTrack.awaitAll(newTracks)
        }
        if (existingTracks.isNotEmpty()) {
            handler.await(true) {
                existingTracks.forEach { track ->
                    anime_syncQueries.update(
                        track.animeId,
                        track.trackerId,
                        track.remoteId,
                        track.libraryId,
                        track.title,
                        track.lastEpisodeSeen,
                        track.totalEpisodes,
                        track.status,
                        track.score,
                        track.remoteUrl,
                        track.startDate,
                        track.finishDate,
                        track.id,
                    )
                }
            }
        }
    }

    private fun Track.forComparison() = this.copy(id = 0L, animeId = 0L)
}
