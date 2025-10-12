package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import exh.source.MERGED_SOURCE_ID
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.FetchTypeColumnAdapter
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
import kotlin.math.min

class MangaRestorer(
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

    suspend fun sortByNew(backupMangas: List<BackupManga>): List<BackupManga> {
        val urlsBySource = handler.awaitList { animesQueries.getAllAnimeSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupMangas
            .sortedWith(
                // KMK -->
                compareBy<BackupManga> { it.source == MERGED_SOURCE_ID }
                    // KMK <--
                    .then(compareBy { it.url in urlsBySource[it.source].orEmpty() })
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    /**
     * Restore a single manga
     */
    suspend fun restore(
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
        // AY -->
        backupSeasons: List<BackupManga>,
        // <-- AY
    ) {
        handler.await(inTransaction = true) {
            val dbManga = findExistingManga(backupManga)
            val manga = backupManga.getMangaImpl()
            val restoredManga = if (dbManga == null) {
                restoreNewManga(manga)
            } else {
                restoreExistingManga(manga, dbManga)
            }

            // AY -->
            backupSeasons.forEach { bs ->
                val dbAnime = findExistingManga(bs)
                val anime = bs.getMangaImpl().copy(
                    parentId = restoredManga.id,
                )
                if (dbAnime == null) {
                    restoreNewManga(anime)
                } else {
                    restoreExistingManga(anime, dbAnime)
                }
            }
            // <-- AY

            restoreMangaDetails(
                manga = restoredManga,
                chapters = backupManga.episodes,
                categories = backupManga.categories,
                backupCategories = backupCategories,
                history = backupManga.history,
                tracks = backupManga.tracking,
                excludedScanlators = backupManga.excludedScanlators,
                // SY -->
                mergedMangaReferences = backupManga.mergedMangaReferences,
                customManga = backupManga.getCustomMangaInfo(),
                // SY <--
            )

            if (isSync) {
                animesQueries.resetIsSyncing()
                episodesQueries.resetIsSyncing()
            }
        }
    }

    private suspend fun findExistingManga(backupManga: BackupManga): Manga? {
        return getMangaByUrlAndSourceId.await(backupManga.url, backupManga.source)
    }

    private suspend fun restoreExistingManga(manga: Manga, dbManga: Manga): Manga {
        return if (manga.version > dbManga.version) {
            updateManga(
                dbManga.copyFrom(manga).copy(id = dbManga.id, /* AY --> */ parentId = manga.parentId /* <-- AY */),
            )
        } else {
            updateManga(
                manga.copyFrom(dbManga).copy(id = dbManga.id, /* AY --> */ parentId = manga.parentId /* <-- AY */),
            )
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
            // AY -->
            fetchType = newer.fetchType,
            parentId = newer.parentId,
            // <-- AY
        )
    }

    suspend fun updateManga(manga: Manga): Manga {
        handler.await(true) {
            animesQueries.update(
                source = manga.source,
                url = manga.url,
                // SY -->
                artist = manga.ogArtist,
                author = manga.ogAuthor,
                description = manga.ogDescription,
                genre = manga.ogGenre?.joinToString(),
                title = manga.ogTitle,
                status = manga.ogStatus,
                thumbnailUrl = manga.ogThumbnailUrl,
                // SY <--
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = manga.initialized,
                viewer = manga.viewerFlags,
                episodeFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                animeId = manga.id,
                updateStrategy = manga.updateStrategy.let(UpdateStrategyColumnAdapter::encode),
                version = manga.version,
                isSyncing = 1,
                notes = manga.notes,
                // AY -->
                fetchType = manga.fetchType.let(FetchTypeColumnAdapter::encode),
                parentId = manga.parentId,
                seasonFlags = manga.seasonFlags,
                seasonNumber = manga.seasonNumber,
                seasonSourceOrder = manga.seasonSourceOrder,
                backgroundUrl = manga.backgroundUrl,
                backgroundLastModified = manga.backgroundLastModified,
                // <-- AY
            )
        }
        return manga
    }

    private suspend fun restoreNewManga(
        manga: Manga,
    ): Manga {
        return manga.copy(
            id = insertManga(manga),
        )
    }

    private suspend fun restoreChapters(manga: Manga, backupChapters: List<BackupChapter>) {
        val dbChaptersByUrl = getChaptersByMangaId.await(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .mapNotNull { backupChapter ->
                val chapter = backupChapter.toChapterImpl().copy(mangaId = manga.id)
                val dbChapter = dbChaptersByUrl[chapter.url]

                when {
                    dbChapter == null -> chapter // New chapter
                    chapter.forComparison() == dbChapter.forComparison() -> null // Same state; skip
                    else -> updateChapterBasedOnSyncState(chapter, dbChapter) // Update existed chapter
                }
            }
            .partition { it.id > 0 }

        insertNewChapters(newChapters)
        updateExistingChapters(existingChapters)
    }

    private fun updateChapterBasedOnSyncState(chapter: Chapter, dbChapter: Chapter): Chapter {
        return if (isSync) {
            chapter.copy(
                id = dbChapter.id,
                bookmark = chapter.bookmark || dbChapter.bookmark,
                // AY -->
                fillermark = chapter.fillermark || dbChapter.fillermark,
                // <-- AY
                read = chapter.read,
                lastPageRead = chapter.lastPageRead,
                // KMK -->
                sourceOrder = max(chapter.sourceOrder, dbChapter.sourceOrder),
                dateUpload = min(chapter.dateUpload, dbChapter.dateUpload),
                // KMK <--
            )
        } else {
            chapter.copyFrom(dbChapter)
                // KMK -->
                .copy(
                    id = dbChapter.id,
                    bookmark = chapter.bookmark || dbChapter.bookmark,
                    sourceOrder = max(chapter.sourceOrder, dbChapter.sourceOrder),
                    dateUpload = min(chapter.dateUpload, dbChapter.dateUpload),
                    // AY -->
                    fillermark = chapter.fillermark || dbChapter.fillermark,
                    // <-- AY
                )
                // KMK <--
                .let {
                    when {
                        dbChapter.read && !it.read -> it.copy(read = true, lastPageRead = dbChapter.lastPageRead)
                        it.lastPageRead == 0L && dbChapter.lastPageRead != 0L -> it.copy(
                            lastPageRead = dbChapter.lastPageRead,
                        )
                        else -> it
                    }
                }
        }
    }

    private fun Chapter.forComparison() =
        this.copy(
            id = 0L,
            mangaId = 0L,
            dateFetch = 0L,
            // KMK -->
            // dateUpload = 0L, some time source loses dateUpload so we overwrite with backup
            // sourceOrder = 0L, although sourceOrder will be updated on refresh, we want to avoid order mixed up anyway
            // KMK <--
            lastModifiedAt = 0L,
            version = 0L,
        )

    private suspend fun insertNewChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                episodesQueries.insert(
                    chapter.mangaId,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read,
                    chapter.bookmark,
                    // AY -->
                    chapter.fillermark,
                    // <-- AY
                    chapter.lastPageRead,
                    chapter.totalPages,
                    chapter.chapterNumber,
                    chapter.sourceOrder,
                    chapter.dateFetch,
                    chapter.dateUpload,
                    chapter.version,
                    // AY -->
                    chapter.summary,
                    chapter.previewUrl,
                    // <-- AY
                )
            }
        }
    }

    private suspend fun updateExistingChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                episodesQueries.update(
                    animeId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    // AY -->
                    summary = null,
                    previewUrl = null,
                    // <-- AY
                    seen = chapter.read,
                    bookmark = chapter.bookmark,
                    // AY -->
                    fillermark = chapter.fillermark,
                    // <-- AY
                    lastSecondSeen = chapter.lastPageRead,
                    totalSeconds = chapter.totalPages,
                    episodeNumber = null,
                    dateFetch = null,
                    // KMK -->
                    sourceOrder = chapter.sourceOrder,
                    dateUpload = chapter.dateUpload,
                    // KMK <--
                    episodeId = chapter.id,
                    version = chapter.version,
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
    private suspend fun insertManga(manga: Manga): Long {
        return handler.awaitOneExecutable(true) {
            animesQueries.insert(
                source = manga.source,
                url = manga.url,
                // SY -->
                artist = manga.ogArtist,
                author = manga.ogAuthor,
                description = manga.ogDescription,
                genre = manga.ogGenre,
                title = manga.ogTitle,
                status = manga.ogStatus,
                thumbnailUrl = manga.ogThumbnailUrl,
                // SY <--
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                episodeFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
                notes = manga.notes,
                // AY -->
                fetchType = manga.fetchType,
                parentId = manga.parentId,
                seasonFlags = manga.seasonFlags,
                seasonNumber = manga.seasonNumber,
                seasonSourceOrder = manga.seasonSourceOrder,
                backgroundUrl = manga.backgroundUrl,
                backgroundLastModified = manga.backgroundLastModified,
                // <-- AY
            )
            animesQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreMangaDetails(
        manga: Manga,
        chapters: List<BackupChapter>,
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
        restoreChapters(manga, chapters)
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
     * Only if [backupCategories] is provided and user chooses to restore it.
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

        val mangaCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.let { dbCategory ->
                    Pair(manga.id, dbCategory.id)
                }
            }
        }

        if (mangaCategoriesToUpdate.isNotEmpty()) {
            handler.await(true) {
                animes_categoriesQueries.deleteAnimeCategoryByAnimeId(manga.id)
                mangaCategoriesToUpdate.forEach { (mangaId, categoryId) ->
                    animes_categoriesQueries.insert(mangaId, categoryId)
                }
            }
        }
    }

    private suspend fun restoreHistory(manga: Manga, backupHistory: List<BackupHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            // KMK -->
            val dbHistory = handler.awaitList { historyQueries.getHistoryByEpisodeUrl(manga.id, history.url) }
                .firstOrNull()
            // KMK <--
            val item = history.getHistoryImpl()

            if (dbHistory == null) {
                // KMK -->
                val chapter = handler.awaitList { episodesQueries.getEpisodeByUrlAndAnimeId(history.url, manga.id) }
                    .firstOrNull()
                // KMK <--
                return@mapNotNull if (chapter == null) {
                    // Chapter doesn't exist; skip
                    null
                } else {
                    // New history entry
                    item.copy(chapterId = chapter._id)
                }
            }

            // Update history entry
            item.copy(
                id = dbHistory._id,
                chapterId = dbHistory.episode_id,
                readAt = max(item.readAt?.time ?: 0L, dbHistory.last_seen?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let { Date(it) },
                readDuration = max(item.readDuration, dbHistory.time_watch) - dbHistory.time_watch,
            )
        }

        if (toUpdate.isNotEmpty()) {
            handler.await(true) {
                toUpdate.forEach {
                    historyQueries.upsert(
                        it.chapterId,
                        it.readAt,
                        it.readDuration,
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
                        mangaId = manga.id,
                    )

                if (track.forComparison() == dbTrack.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing track
                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastChapterRead = max(dbTrack.lastChapterRead, track.lastChapterRead),
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
                        track.mangaId,
                        track.trackerId,
                        track.remoteId,
                        track.libraryId,
                        track.title,
                        track.lastChapterRead,
                        track.totalChapters,
                        track.status,
                        track.score,
                        track.remoteUrl,
                        track.startDate,
                        track.finishDate,
                        track.private,
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
                        backupMergedMangaReference.mangaUrl == it.mangaUrl
                }
            ) {
                // Let the db assign the id
                // KMK -->
                val mergedManga = handler.awaitList {
                    // KMK <--
                    animesQueries.getAnimeByUrlAndSource(
                        backupMergedMangaReference.mangaUrl,
                        backupMergedMangaReference.mangaSourceId,
                        MangaMapper::mapManga,
                    )
                    // KMK -->
                }.firstOrNull()
                    // KMK <--
                    ?: return@forEach
                backupMergedMangaReference.getMergedMangaReference().run {
                    handler.await {
                        mergedQueries.insert(
                            infoAnime = isInfoManga,
                            getEpisodeUpdates = getChapterUpdates,
                            episodeSortMode = chapterSortMode.toLong(),
                            episodePriority = chapterPriority.toLong(),
                            downloadEpisodes = downloadChapters,
                            mergeId = mergeMangaId,
                            mergeUrl = mergeUrl,
                            animeId = mergedManga.id,
                            animeUrl = mangaUrl,
                            animeSource = mangaSourceId,
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

    private fun BackupManga.getCustomMangaInfo(): CustomMangaInfo? {
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

    private fun Track.forComparison() = this.copy(id = 0L, mangaId = 0L)

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
            // KMK -->
        }.toSet()
        val toInsert = excludedScanlators.toSet().subtract(existingExcludedScanlators)
        if (toInsert.isNotEmpty()) {
            handler.await(inTransaction = true) {
                // KMK <--
                toInsert.forEach {
                    excluded_scanlatorsQueries.insert(manga.id, it)
                }
            }
        }
    }
}
