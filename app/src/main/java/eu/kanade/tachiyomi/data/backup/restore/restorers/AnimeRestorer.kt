package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MergedMangaMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
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
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    fetchInterval: FetchInterval = Injekt.get(),
    // SY -->
    private val setCustomMangaInfo: SetCustomMangaInfo = Injekt.get(),
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

    /**
     * Restore a single manga
     */
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
                manga = restoredAnime,
                episodes = backupAnime.episodes,
                categories = backupAnime.categories,
                backupCategories = backupCategories,
                history = backupAnime.history,
                tracks = backupAnime.tracking,
                excludedScanlators = backupAnime.excludedScanlators,
                // SY -->
                mergedMangaReferences = backupAnime.mergedMangaReferences,
                customManga = backupAnime.getCustomMangaInfo(),
                // SY <--
            )

            if (isSync) {
                animesQueries.resetIsSyncing()
                episodesQueries.resetIsSyncing()
            }
        }
    }

    private suspend fun findExistingAnime(backupAnime: BackupAnime): Manga? {
        return getMangaByUrlAndSourceId.await(backupAnime.url, backupAnime.source)
    }

    private suspend fun restoreExistingAnime(manga: Manga, dbManga: Manga): Manga {
        return if (manga.version > dbManga.version) {
            updateAnime(dbManga.copyFrom(manga).copy(id = dbManga.id))
        } else {
            updateAnime(manga.copyFrom(dbManga).copy(id = dbManga.id))
        }
    }

    private fun Manga.copyFrom(newer: Manga): Manga {
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

    suspend fun updateAnime(manga: Manga): Manga {
        handler.await(true) {
            animesQueries.update(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre?.joinToString(),
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = manga.initialized,
                viewer = manga.viewerFlags,
                episodeFlags = manga.episodeFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                animeId = manga.id,
                updateStrategy = manga.updateStrategy.let(UpdateStrategyColumnAdapter::encode),
                version = manga.version,
                isSyncing = 1,
            )
        }
        return manga
    }

    private suspend fun restoreNewAnime(
        manga: Manga,
    ): Manga {
        return manga.copy(
            initialized = manga.description != null,
            id = insertAnime(manga),
            version = manga.version,
        )
    }

    private suspend fun restoreEpisodes(manga: Manga, backupEpisodes: List<BackupEpisode>) {
        val dbEpisodesByUrl = getChaptersByMangaId.await(manga.id)
            .associateBy { it.url }

        val (existingEpisodes, newEpisodes) = backupEpisodes
            .mapNotNull { backupEpisode ->
                val episode = backupEpisode.toEpisodeImpl().copy(animeId = manga.id)
                val dbEpisode = dbEpisodesByUrl[episode.url]

                when {
                    dbEpisode == null -> episode // New episode
                    episode.forComparison() == dbEpisode.forComparison() -> null // Same state; skip
                    else -> updateEpisodeBasedOnSyncState(episode, dbEpisode) // Update existed episode
                }
            }
            .partition { it.id > 0 }

        insertNewEpisodes(newEpisodes)
        updateExistingEpisodes(existingEpisodes)
    }

    private fun updateEpisodeBasedOnSyncState(chapter: Chapter, dbChapter: Chapter): Chapter {
        return if (isSync) {
            chapter.copy(
                id = dbChapter.id,
                bookmark = chapter.bookmark || dbChapter.bookmark,
                // AM (FILLERMARK) -->
                fillermark = chapter.fillermark || dbChapter.fillermark,
                // <-- AM (FILLERMARK)
                seen = chapter.seen,
                lastSecondSeen = chapter.lastSecondSeen,
                sourceOrder = chapter.sourceOrder,
            )
        } else {
            chapter.copyFrom(dbChapter).let {
                when {
                    dbChapter.seen && !it.seen -> it.copy(seen = true, lastSecondSeen = dbChapter.lastSecondSeen)
                    it.lastSecondSeen == 0L && dbChapter.lastSecondSeen != 0L -> it.copy(
                        lastSecondSeen = dbChapter.lastSecondSeen,
                    )
                    else -> it
                }
            }
                // KMK -->
                .copy(
                    id = dbChapter.id,
                    bookmark = chapter.bookmark || dbChapter.bookmark,
                    // AM (FILLERMARK) -->
                    fillermark = chapter.fillermark || dbChapter.fillermark,
                    // <-- AM (FILLERMARK)
                )
            // KMK <--
        }
    }

    private fun Chapter.forComparison() =
        this.copy(
            id = 0L,
            animeId = 0L,
            dateFetch = 0L,
            // KMK -->
            // dateUpload = 0L, some time source loses dateUpload so we overwrite with backup
            sourceOrder = 0L, // ignore sourceOrder since it will be updated on refresh
            // KMK <--
            lastModifiedAt = 0L,
            version = 0L,
        )

    private suspend fun insertNewEpisodes(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { episode ->
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

    private suspend fun updateExistingEpisodes(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { episode ->
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
                    sourceOrder = if (isSync) episode.sourceOrder else null,
                    dateFetch = null,
                    // KMK -->
                    dateUpload = episode.dateUpload,
                    // KMK <--
                    episodeId = episode.id,
                    version = episode.version,
                    isSyncing = 1,
                )
            }
        }
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    private suspend fun insertAnime(manga: Manga): Long {
        return handler.awaitOneExecutable(true) {
            animesQueries.insert(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                episodeFlags = manga.episodeFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
            )
            animesQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreAnimeDetails(
        manga: Manga,
        episodes: List<BackupEpisode>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
        excludedScanlators: List<String>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        customManga: CustomMangaInfo?,
        // SY <--
    ): Manga {
        restoreCategories(manga, categories, backupCategories)
        restoreEpisodes(manga, episodes)
        restoreTracking(manga, tracks)
        restoreHistory(manga, history)
        restoreExcludedScanlators(manga, excludedScanlators)
        updateManga.awaitUpdateFetchInterval(manga, now, currentFetchWindow)
        // SY -->
        restoreMergedMangaReferencesForManga(manga.id, mergedMangaReferences)
        restoreEditedInfo(customManga?.copy(id = manga.id))
        // SY <--

        return manga
    }

    /**
     * Restores the categories a manga is in.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    private suspend fun restoreCategories(
        manga: Manga,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }

        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val animeCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.let { dbCategory ->
                    Pair(manga.id, dbCategory.id)
                }
            }
        }

        if (animeCategoriesToUpdate.isNotEmpty()) {
            handler.await(true) {
                animes_categoriesQueries.deleteAnimeCategoryByAnimeId(manga.id)
                animeCategoriesToUpdate.forEach { (animeId, categoryId) ->
                    animes_categoriesQueries.insert(animeId, categoryId)
                }
            }
        }
    }

    private suspend fun restoreHistory(manga: Manga, backupHistory: List<BackupHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            val dbHistory = handler.awaitOneOrNull { historyQueries.getHistoryByEpisodeUrl(manga.id, history.url) }
            val item = history.getHistoryImpl()

            if (dbHistory == null) {
                val episode = handler.awaitList { episodesQueries.getEpisodeByUrl(history.url) }
                    .find { it.anime_id == manga.id }
                return@mapNotNull if (episode == null) {
                    // Chapter doesn't exist; skip
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

    private suspend fun restoreTracking(manga: Manga, backupTracks: List<BackupTracking>) {
        val dbTrackByTrackerId = getTracks.await(manga.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        animeId = manga.id,
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

    // SY -->
    /**
     * Restore the categories from Json
     *
     * @param mergeMangaId the merge manga for the references
     * @param backupMergedMangaReferences the list of backup manga references for the merged manga
     */
    private suspend fun restoreMergedMangaReferencesForManga(
        mergeMangaId: Long,
        backupMergedMangaReferences: List<BackupMergedMangaReference>,
    ) {
        // Get merged manga references from file and from db
        val dbMergedMangaReferences = handler.awaitList {
            mergedQueries.selectAll(MergedMangaMapper::map)
        }

        // Iterate over them
        backupMergedMangaReferences.forEach { backupMergedMangaReference ->
            // If the backupMergedMangaReference isn't in the db,
            // remove the id and insert a new backupMergedMangaReference
            // Store the inserted id in the backupMergedMangaReference
            if (dbMergedMangaReferences.none {
                    backupMergedMangaReference.mergeUrl == it.mergeUrl &&
                        backupMergedMangaReference.mangaUrl == it.animeUrl
                }
            ) {
                // Let the db assign the id
                val mergedManga = handler.awaitOneOrNull {
                    animesQueries.getAnimeByUrlAndSource(
                        backupMergedMangaReference.mangaUrl,
                        backupMergedMangaReference.mangaSourceId,
                        MangaMapper::mapManga,
                    )
                } ?: return@forEach
                backupMergedMangaReference.getMergedMangaReference().run {
                    handler.await {
                        mergedQueries.insert(
                            infoAnime = isInfoAnime,
                            getEpisodeUpdates = getEpisodeUpdates,
                            episodeSortMode = episodeSortMode.toLong(),
                            episodePriority = episodePriority.toLong(),
                            downloadEpisodes = downloadEpisodes,
                            mergeId = mergeMangaId,
                            mergeUrl = mergeUrl,
                            animeId = mergedManga.id,
                            animeUrl = animeUrl,
                            animeSource = animeSourceId,
                        )
                    }
                }
            }
        }
    }

    private fun restoreEditedInfo(mangaJson: CustomMangaInfo?) {
        mangaJson ?: return
        setCustomMangaInfo.set(mangaJson)
    }

    private fun BackupAnime.getCustomMangaInfo(): CustomMangaInfo? {
        if (customTitle != null ||
            customArtist != null ||
            customAuthor != null ||
            customThumbnailUrl != null ||
            customDescription != null ||
            customGenre != null ||
            customStatus != 0
        ) {
            return CustomMangaInfo(
                id = 0L,
                title = customTitle,
                author = customAuthor,
                artist = customArtist,
                thumbnailUrl = customThumbnailUrl,
                description = customDescription,
                genre = customGenre,
                status = customStatus.takeUnless { it == 0 }?.toLong(),
            )
        }
        return null
    }
    // SY <--

    private fun Track.forComparison() = this.copy(id = 0L, animeId = 0L)

    /**
     * Restores the excluded scanlators for the manga.
     *
     * @param manga the manga whose excluded scanlators have to be restored.
     * @param excludedScanlators the excluded scanlators to restore.
     */
    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existingExcludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByAnimeId(manga.id)
        }
        val toInsert = excludedScanlators.filter { it !in existingExcludedScanlators }
        if (toInsert.isNotEmpty()) {
            handler.await {
                toInsert.forEach {
                    excluded_scanlatorsQueries.insert(manga.id, it)
                }
            }
        }
    }
}
