package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.net.Uri
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.data.sync.service.SyncData
import eu.kanade.tachiyomi.data.sync.service.SyncYomiSyncService
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.Episodes
import tachiyomi.data.manga.MangaMapper.mapManga
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.system.measureTimeMillis

/**
 * A manager to handle synchronization tasks in the app, such as updating
 * sync preferences and performing synchronization with a remote server.
 *
 * @property context The application context.
 */
class SyncManager(
    private val context: Context,
    private val handler: DatabaseHandler = Injekt.get(),
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private var json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
    private val getCategories: GetCategories = Injekt.get(),
) {
    private val backupCreator: BackupCreator = BackupCreator(context, false)
    private val notifier: SyncNotifier = SyncNotifier(context)
    private val animeRestorer: AnimeRestorer = AnimeRestorer()

    enum class SyncService(val value: Int) {
        NONE(0),
        SYNCYOMI(1),
        GOOGLE_DRIVE(2),
        ;

        companion object {
            fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
        }
    }

    /**
     * Syncs data with a sync service.
     *
     * This function retrieves local data (favorites, anime, extensions, and categories)
     * from the database using the BackupManager, then synchronizes the data with a sync service.
     */
    suspend fun syncData() {
        // Reset isSyncing in case it was left over or failed syncing during restore.
        handler.await(inTransaction = true) {
            animesQueries.resetIsSyncing()
            episodesQueries.resetIsSyncing()
        }

        val syncOptions = syncPreferences.getSyncSettings()
        val databaseAnime = getAllAnimeThatNeedsSync()

        val backupOptions = BackupOptions(
            libraryEntries = syncOptions.libraryEntries,
            categories = syncOptions.categories,
            episodes = syncOptions.chapters,
            tracking = syncOptions.tracking,
            history = syncOptions.history,
            extensionRepoSettings = syncOptions.extensionRepoSettings,
            appSettings = syncOptions.appSettings,
            sourceSettings = syncOptions.sourceSettings,
            privateSettings = syncOptions.privateSettings,

            // SY -->
            customInfo = syncOptions.customInfo,
            seenEntries = syncOptions.seenEntries,
            savedSearchesFeeds = syncOptions.savedSearchesFeeds,
            // SY <--
        )

        logcat(LogPriority.DEBUG) { "Begin create backup" }
        val backupAnime = backupCreator.backupAnimes(databaseAnime, backupOptions)
        val backup = Backup(
            backupAnime = backupAnime,
            backupCategories = backupCreator.backupAnimeCategories(backupOptions),
            backupSources = backupCreator.backupAnimeSources(backupAnime),
            backupPreferences = backupCreator.backupAppPreferences(backupOptions),
            backupExtensionRepo = backupCreator.backupExtensionRepos(backupOptions),
            backupSourcePreferences = backupCreator.backupSourcePreferences(backupOptions),

            // SY -->
            backupSavedSearches = backupCreator.backupSavedSearches(backupOptions),
            // SY <--

            // KMK -->
            backupFeeds = backupCreator.backupFeeds(backupOptions),
            // KMK <--
        )
        logcat(LogPriority.DEBUG) { "End create backup" }

        // Create the SyncData object
        val syncData = SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = backup,
        )

        // Handle sync based on the selected service
        val syncService = when (val syncService = SyncService.fromInt(syncPreferences.syncService().get())) {
            SyncService.SYNCYOMI -> {
                SyncYomiSyncService(
                    context,
                    json,
                    syncPreferences,
                    notifier,
                )
            }

            SyncService.GOOGLE_DRIVE -> {
                GoogleDriveSyncService(context, json, syncPreferences)
            }

            else -> {
                logcat(LogPriority.ERROR) { "Invalid sync service type: $syncService" }
                null
            }
        }

        val remoteBackup = syncService?.doSync(syncData)

        if (remoteBackup == null) {
            logcat(LogPriority.DEBUG) { "Skip restore due to network issues" }
            // should we call showSyncError?
            return
        }

        if (remoteBackup === syncData.backup) {
            // nothing changed
            logcat(LogPriority.DEBUG) { "Skip restore due to remote was overwrite from local" }
            syncPreferences.lastSyncTimestamp().set(Date().time)
            notifier.showSyncSuccess("Sync completed successfully")
            return
        }

        // Stop the sync early if the remote backup is null or empty
        if (remoteBackup.backupAnime.isEmpty()) {
            notifier.showSyncError("No data found on remote server.")
            return
        }

        // Check if it's first sync based on lastSyncTimestamp
        if (syncPreferences.lastSyncTimestamp().get() == 0L && databaseAnime.isNotEmpty()) {
            // It's first sync no need to restore data. (just update remote data)
            syncPreferences.lastSyncTimestamp().set(Date().time)
            notifier.showSyncSuccess("Updated remote data successfully")
            return
        }

        val (animeFilteredFavorites, animeNonFavorites) = aniFilterFavoritesAndNonFavorites(remoteBackup)
        animeUpdateNonFavorites(animeNonFavorites)

        val newSyncData = backup.copy(
            backupAnime = animeFilteredFavorites,
            backupCategories = remoteBackup.backupCategories,
            backupSources = remoteBackup.backupSources,
            backupPreferences = remoteBackup.backupPreferences,
            backupSourcePreferences = remoteBackup.backupSourcePreferences,
            backupExtensionRepo = remoteBackup.backupExtensionRepo,

            // SY -->
            backupSavedSearches = remoteBackup.backupSavedSearches,
            // SY <--

            // KMK -->
            backupFeeds = remoteBackup.backupFeeds,
            // KMK <--
        )

        // It's local sync no need to restore data. (just update remote data)
        if (animeFilteredFavorites.isEmpty()) {
            // update the sync timestamp
            syncPreferences.lastSyncTimestamp().set(Date().time)
            notifier.showSyncSuccess("Sync completed successfully")
            return
        }

        val backupUri = writeSyncDataToCache(context, newSyncData)
        logcat(LogPriority.DEBUG) { "Got Backup Uri: $backupUri" }
        if (backupUri != null) {
            BackupRestoreJob.start(
                context,
                backupUri,
                sync = true,
                options = RestoreOptions(
                    appSettings = true,
                    sourceSettings = true,
                    libraryEntries = true,
                    extensionRepoSettings = true,
                ),
            )

            // update the sync timestamp
            syncPreferences.lastSyncTimestamp().set(Date().time)
        } else {
            logcat(LogPriority.ERROR) { "Failed to write sync data to file" }
        }
    }

    private fun writeSyncDataToCache(context: Context, backup: Backup): Uri? {
        val cacheFile = File(context.cacheDir, "anikku_sync_data.proto.gz")
        return try {
            cacheFile.outputStream().use { output ->
                output.write(ProtoBuf.encodeToByteArray(Backup.serializer(), backup))
                Uri.fromFile(cacheFile)
            }
        } catch (e: IOException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to write sync data to cache" }
            null
        }
    }

    /**
     * Retrieves all anime from the local database.
     *
     * @return a list of all anime stored in the database
     */
    private suspend fun getAllAnimeFromDB(): List<Manga> {
        return handler.awaitList { animesQueries.getAllAnime(::mapManga) }
    }

    private suspend fun getAllAnimeThatNeedsSync(): List<Manga> {
        return handler.awaitList { animesQueries.getAnimesWithFavoriteTimestamp(::mapManga) }
    }

    private suspend fun isAnimeDifferent(localManga: Manga, remoteAnime: BackupAnime): Boolean {
        val localEpisodes = handler.await { episodesQueries.getEpisodesByAnimeId(localManga.id, 0).executeAsList() }
        val localCategories = getCategories.await(localManga.id).map { it.order }

        if (areEpisodesDifferent(localEpisodes, remoteAnime.episodes)) {
            return true
        }

        if (localManga.version != remoteAnime.version) {
            return true
        }

        if (localCategories.toSet() != remoteAnime.categories.toSet()) {
            return true
        }

        return false
    }

    private fun areEpisodesDifferent(localEpisodes: List<Episodes>, remoteEpisodes: List<BackupEpisode>): Boolean {
        val localEpisodeMap = localEpisodes.associateBy { it.url }
        val remoteEpisodeMap = remoteEpisodes.associateBy { it.url }

        if (localEpisodeMap.size != remoteEpisodeMap.size) {
            return true
        }

        for ((url, localEpisode) in localEpisodeMap) {
            val remoteEpisode = remoteEpisodeMap[url]

            // If a matching remote episode doesn't exist, or the version numbers are different, consider them different
            if (remoteEpisode == null || localEpisode.version != remoteEpisode.version) {
                return true
            }
        }

        return false
    }

    /**
     * Filters the favorite and non-favorite anime from the backup and checks
     * if the favorite anime is different from the local database.
     * @param backup the Backup object containing the backup data.
     * @return a Pair of lists, where the first list contains different favorite anime
     * and the second list contains non-favorite anime.
     */
    private suspend fun aniFilterFavoritesAndNonFavorites(backup: Backup): Pair<List<BackupAnime>, List<BackupAnime>> {
        val favorites = mutableListOf<BackupAnime>()
        val nonFavorites = mutableListOf<BackupAnime>()
        val logTag = "filterFavoritesAndNonFavorites"

        val elapsedTimeMillis = measureTimeMillis {
            val databaseAnime = getAllAnimeFromDB()
            val localAnimeMap = databaseAnime.associateBy {
                Triple(it.source, it.url, it.title)
            }

            logcat(LogPriority.DEBUG, logTag) { "Starting to filter favorites and non-favorites from backup data." }

            backup.backupAnime.forEach { remoteAnime ->
                val compositeKey = Triple(remoteAnime.source, remoteAnime.url, remoteAnime.title)
                val localAnime = localAnimeMap[compositeKey]
                when {
                    // Checks if the anime is in favorites and needs updating or adding
                    remoteAnime.favorite -> {
                        if (localAnime == null || isAnimeDifferent(localAnime, remoteAnime)) {
                            logcat(LogPriority.DEBUG, logTag) { "Adding to favorites: ${remoteAnime.title}" }
                            favorites.add(remoteAnime)
                        } else {
                            logcat(LogPriority.DEBUG, logTag) { "Already up-to-date favorite: ${remoteAnime.title}" }
                        }
                    }
                    // Handle non-favorites
                    !remoteAnime.favorite -> {
                        logcat(LogPriority.DEBUG, logTag) { "Adding to non-favorites: ${remoteAnime.title}" }
                        nonFavorites.add(remoteAnime)
                    }
                }
            }
        }

        val minutes = elapsedTimeMillis / 60000
        val seconds = (elapsedTimeMillis % 60000) / 1000
        logcat(LogPriority.DEBUG, logTag) {
            "Filtering completed in ${minutes}m ${seconds}s. Favorites found: ${favorites.size}, " +
                "Non-favorites found: ${nonFavorites.size}"
        }

        return Pair(favorites, nonFavorites)
    }

    /**
     * Updates the non-favorite anime in the local database with their favorite status from the backup.
     * @param nonFavorites the list of non-favorite BackupAnime objects from the backup.
     */
    private suspend fun animeUpdateNonFavorites(nonFavorites: List<BackupAnime>) {
        val localAnimeList = getAllAnimeFromDB()

        val localAnimeMap = localAnimeList.associateBy { Triple(it.source, it.url, it.title) }

        nonFavorites.forEach { nonFavorite ->
            val key = Triple(nonFavorite.source, nonFavorite.url, nonFavorite.title)
            localAnimeMap[key]?.let { localAnime ->
                if (localAnime.favorite != nonFavorite.favorite) {
                    val updatedAnime = localAnime.copy(favorite = nonFavorite.favorite)
                    animeRestorer.updateAnime(updatedAnime)
                }
            }
        }
    }
}
