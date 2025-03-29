package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat

@Serializable
data class SyncData(
    val deviceId: String = "",
    val backup: Backup? = null,
)

abstract class SyncService(
    val context: Context,
    val json: Json,
    val syncPreferences: SyncPreferences,
) {
    abstract suspend fun doSync(syncData: SyncData): Backup?

    /**
     * Merges the local and remote sync data into a single JSON string.
     *
     * @param localSyncData The SData containing the local sync data.
     * @param remoteSyncData The SData containing the remote sync data.
     * @return The JSON string containing the merged sync data.
     */
    protected fun mergeSyncData(localSyncData: SyncData, remoteSyncData: SyncData): SyncData {
        val mergedAnimeCategoriesList =
            mergeCategoriesLists(
                localSyncData.backup?.backupCategories,
                remoteSyncData.backup?.backupCategories,
            )

        val mergedAnimeList = mergeAnimeLists(
            localSyncData.backup?.backupAnime,
            remoteSyncData.backup?.backupAnime,
            localSyncData.backup?.backupCategories ?: emptyList(),
            remoteSyncData.backup?.backupCategories ?: emptyList(),
            mergedAnimeCategoriesList,
        )

        val mergedAnimeSourcesList =
            mergeAnimeSourcesLists(localSyncData.backup?.backupSources, remoteSyncData.backup?.backupSources)
        val mergedPreferencesList =
            mergePreferencesLists(localSyncData.backup?.backupPreferences, remoteSyncData.backup?.backupPreferences)
        val mergedSourcePreferencesList = mergeSourcePreferencesLists(
            localSyncData.backup?.backupSourcePreferences,
            remoteSyncData.backup?.backupSourcePreferences,
        )

        // SY -->
        val mergedSavedSearchesList = mergeSavedSearchesLists(
            localSyncData.backup?.backupSavedSearches,
            remoteSyncData.backup?.backupSavedSearches,
        )
        // SY <--

        // Create the merged Backup object
        val mergedBackup = Backup(
            backupAnime = mergedAnimeList,
            backupCategories = mergedAnimeCategoriesList,
            backupSources = mergedAnimeSourcesList,
            backupPreferences = mergedPreferencesList,
            backupSourcePreferences = mergedSourcePreferencesList,

            // SY -->
            backupSavedSearches = mergedSavedSearchesList,
            // SY <--
        )

        // Create the merged SData object
        return SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = mergedBackup,
        )
    }

    /**
     * Merges two lists of BackupAnime objects, selecting the most recent manga based on the lastModifiedAt value.
     * If lastModifiedAt is null for a manga, it treats that manga as the oldest possible for comparison purposes.
     * This function is designed to reconcile local and remote manga lists, ensuring the most up-to-date manga is retained.
     *
     * @param localAnimeList The list of local BackupAnime objects or null.
     * @param remoteAnimeList The list of remote BackupAnime objects or null.
     * @return A list of BackupAnime objects, each representing the most recent version of the manga from either local or remote sources.
     */
    private fun mergeAnimeLists(
        localAnimeList: List<BackupAnime>?,
        remoteAnimeList: List<BackupAnime>?,
        localCategories: List<BackupCategory>,
        remoteCategories: List<BackupCategory>,
        mergedCategories: List<BackupCategory>,
    ): List<BackupAnime> {
        val logTag = "MergeAnimeLists"

        val localAnimeListSafe = localAnimeList.orEmpty()
        val remoteAnimeListSafe = remoteAnimeList.orEmpty()

        logcat(LogPriority.DEBUG, logTag) {
            "Starting merge. Local list size: ${localAnimeListSafe.size}, Remote list size: ${remoteAnimeListSafe.size}"
        }

        fun animeCompositeKey(anime: BackupAnime): String {
            return "${anime.source}|${anime.url}|${anime.title.lowercase().trim()}|${anime.author?.lowercase()?.trim()}"
        }

        // Create maps using composite keys
        val localAnimeMap = localAnimeListSafe.associateBy { animeCompositeKey(it) }
        val remoteAnimeMap = remoteAnimeListSafe.associateBy { animeCompositeKey(it) }

        val localCategoriesMapByOrder = localCategories.associateBy { it.order }
        val remoteCategoriesMapByOrder = remoteCategories.associateBy { it.order }
        val mergedCategoriesMapByName = mergedCategories.associateBy { it.name }

        fun updateCategories(theAnime: BackupAnime, theMap: Map<Long, BackupCategory>): BackupAnime {
            return theAnime.copy(
                categories = theAnime.categories.mapNotNull {
                    theMap[it]?.let { category ->
                        mergedCategoriesMapByName[category.name]?.order
                    }
                },
            )
        }

        logcat(LogPriority.DEBUG, logTag) {
            "Starting merge. Local list size: ${localAnimeListSafe.size}, Remote list size: ${remoteAnimeListSafe.size}"
        }

        val mergedList = (localAnimeMap.keys + remoteAnimeMap.keys).distinct().mapNotNull { compositeKey ->
            val local = localAnimeMap[compositeKey]
            val remote = remoteAnimeMap[compositeKey]

            // New version comparison logic
            when {
                local != null && remote == null -> updateCategories(local, localCategoriesMapByOrder)
                local == null && remote != null -> updateCategories(remote, remoteCategoriesMapByOrder)
                local != null && remote != null -> {
                    // Compare versions to decide which manga to keep
                    if (local.version >= remote.version) {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Keeping local version of ${local.title} with merged episodes."
                        }
                        updateCategories(
                            local.copy(episodes = mergeEpisodes(local.episodes, remote.episodes)),
                            localCategoriesMapByOrder,
                        )
                    } else {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Keeping remote version of ${remote.title} with merged episodes."
                        }
                        updateCategories(
                            remote.copy(episodes = mergeEpisodes(local.episodes, remote.episodes)),
                            remoteCategoriesMapByOrder,
                        )
                    }
                }
                else -> null // No manga found for key
            }
        }

        // Counting favorites and non-favorites
        val (favorites, nonFavorites) = mergedList.partition { it.favorite }

        logcat(LogPriority.DEBUG, logTag) {
            "Merge completed. Total merged anime: ${mergedList.size}, Favorites: ${favorites.size}, " +
                "Non-Favorites: ${nonFavorites.size}"
        }

        return mergedList
    }

/**
     * Merges two lists of BackupEpisode objects, selecting the most recent episode based on the lastModifiedAt value.
     * If lastModifiedAt is null for a episode, it treats that episode as the oldest possible for comparison purposes.
     * This function is designed to reconcile local and remote episode lists, ensuring the most up-to-date episode is retained.
     *
     * @param localEpisodes The list of local BackupEpisode objects.
     * @param remoteEpisodes The list of remote BackupEpisode objects.
     * @return A list of BackupEpisode objects, each representing the most recent version of the episode from either local or remote sources.
     *
     * - This function is used in scenarios where local and remote episode lists need to be synchronized.
     * - It iterates over the union of the URLs from both local and remote episodes.
     * - For each URL, it compares the corresponding local and remote episodes based on the lastModifiedAt value.
     * - If only one source (local or remote) has the episode for a URL, that episode is used.
     * - If both sources have the episode, the one with the more recent lastModifiedAt value is chosen.
     * - If lastModifiedAt is null or missing, the episode is considered the oldest for safety, ensuring that any episode with a valid timestamp is preferred.
     * - The resulting list contains the most recent episodes from the combined set of local and remote episodes.
     */
    private fun mergeEpisodes(
        localEpisodes: List<BackupEpisode>,
        remoteEpisodes: List<BackupEpisode>,
    ): List<BackupEpisode> {
        val logTag = "MergeEpisodes"

        fun episodeCompositeKey(episode: BackupEpisode): String {
            return "${episode.url}|${episode.name}|${episode.episodeNumber}"
        }

        val localEpisodeMap = localEpisodes.associateBy { episodeCompositeKey(it) }
        val remoteEpisdodeMap = remoteEpisodes.associateBy { episodeCompositeKey(it) }

        logcat(LogPriority.DEBUG, logTag) {
            "Starting episode merge. Local episodes: ${localEpisodes.size}, Remote episodes: ${remoteEpisodes.size}"
        }

        // Merge both chapter maps based on version numbers
        val mergedEpisodes = (localEpisodeMap.keys + remoteEpisdodeMap.keys).distinct().mapNotNull { compositeKey ->
            val localEpisode = localEpisodeMap[compositeKey]
            val remoteEpisode = remoteEpisdodeMap[compositeKey]

            logcat(LogPriority.DEBUG, logTag) {
                "Processing episode key: $compositeKey. Local episode: ${localEpisode != null}, " +
                    "Remote episode: ${remoteEpisode != null}"
            }

            when {
                localEpisode != null && remoteEpisode == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Keeping local episode: ${localEpisode.name}." }
                    localEpisode
                }
                localEpisode == null && remoteEpisode != null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Taking remote episode: ${remoteEpisode.name}." }
                    remoteEpisode
                }
                localEpisode != null && remoteEpisode != null -> {
                    // Use version number to decide which episode to keep
                    val chosenChapter = if (localEpisode.version >= remoteEpisode.version) {
                        // If there mare more episode on remote, local sourceOrder will need to be updated to maintain correct source order.
                        if (localEpisodes.size < remoteEpisodes.size) {
                            localEpisode.copy(sourceOrder = remoteEpisode.sourceOrder)
                        } else {
                            localEpisode
                        }
                    } else {
                        remoteEpisode
                    }
                    logcat(LogPriority.DEBUG, logTag) {
                        "Merging episode: ${chosenChapter.name}. Chosen version from: ${
                            if (localEpisode.version >= remoteEpisode.version) "Local" else "Remote"
                        }, Local version: ${localEpisode.version}, Remote version: ${remoteEpisode.version}."
                    }
                    chosenChapter
                }
                else -> {
                    logcat(LogPriority.DEBUG, logTag) {
                        "No episode found for composite key: $compositeKey. Skipping."
                    }
                    null
                }
            }
        }

        logcat(LogPriority.DEBUG, logTag) { "Episode merge completed. Total merged episodes: ${mergedEpisodes.size}" }

        return mergedEpisodes
    }

    /**
     * Merges two lists of SyncCategory objects, prioritizing the category with the most recent order value.
     *
     * @param localCategoriesList The list of local SyncCategory objects.
     * @param remoteCategoriesList The list of remote SyncCategory objects.
     * @return The merged list of SyncCategory objects.
     */
    private fun mergeCategoriesLists(
        localCategoriesList: List<BackupCategory>?,
        remoteCategoriesList: List<BackupCategory>?,
    ): List<BackupCategory> {
        if (localCategoriesList == null) return remoteCategoriesList ?: emptyList()
        if (remoteCategoriesList == null) return localCategoriesList
        val localCategoriesMap = localCategoriesList.associateBy { it.name }
        val remoteCategoriesMap = remoteCategoriesList.associateBy { it.name }

        val mergedCategoriesMap = mutableMapOf<String, BackupCategory>()

        localCategoriesMap.forEach { (name, localCategory) ->
            val remoteCategory = remoteCategoriesMap[name]
            if (remoteCategory != null) {
                // Compare and merge local and remote categories
                val mergedCategory = if (localCategory.order > remoteCategory.order) {
                    localCategory
                } else {
                    remoteCategory
                }
                mergedCategoriesMap[name] = mergedCategory
            } else {
                // If the category is only in the local list, add it to the merged list
                mergedCategoriesMap[name] = localCategory
            }
        }

        // Add any categories from the remote list that are not in the local list
        remoteCategoriesMap.forEach { (name, remoteCategory) ->
            if (!mergedCategoriesMap.containsKey(name)) {
                mergedCategoriesMap[name] = remoteCategory
            }
        }

        return mergedCategoriesMap.values.toList()
    }

    private fun mergeAnimeSourcesLists(
        localSources: List<BackupSource>?,
        remoteSources: List<BackupSource>?,
    ): List<BackupSource> {
        val logTag = "MergeSources"

        // Create maps using sourceId as key
        val localSourceMap = localSources?.associateBy { it.sourceId } ?: emptyMap()
        val remoteSourceMap = remoteSources?.associateBy { it.sourceId } ?: emptyMap()

        logcat(LogPriority.DEBUG, logTag) {
            "Starting source merge. Local sources: ${localSources?.size}, Remote sources: ${remoteSources?.size}"
        }

        // Merge both source maps
        val mergedSources = (localSourceMap.keys + remoteSourceMap.keys).distinct().mapNotNull { sourceId ->
            val localSource = localSourceMap[sourceId]
            val remoteSource = remoteSourceMap[sourceId]

            logcat(LogPriority.DEBUG, logTag) {
                "Processing source ID: $sourceId. Local source: ${localSource != null}, " +
                    "Remote source: ${remoteSource != null}"
            }

            when {
                localSource != null && remoteSource == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using local source: ${localSource.name}." }
                    localSource
                }
                remoteSource != null && localSource == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using remote source: ${remoteSource.name}." }
                    remoteSource
                }
                else -> {
                    logcat(LogPriority.DEBUG, logTag) { "Remote and local is not empty: $sourceId. Skipping." }
                    null
                }
            }
        }

        logcat(LogPriority.DEBUG, logTag) { "Source merge completed. Total merged sources: ${mergedSources.size}" }

        return mergedSources
    }

    private fun mergePreferencesLists(
        localPreferences: List<BackupPreference>?,
        remotePreferences: List<BackupPreference>?,
    ): List<BackupPreference> {
        val logTag = "MergePreferences"

        // Create maps using key as the unique identifier
        val localPreferencesMap = localPreferences?.associateBy { it.key } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.key } ?: emptyMap()

        logcat(LogPriority.DEBUG, logTag) {
            "Starting preferences merge. Local preferences: ${localPreferences?.size}, " +
                "Remote preferences: ${remotePreferences?.size}"
        }

        // Merge both preferences maps
        val mergedPreferences = (localPreferencesMap.keys + remotePreferencesMap.keys).distinct().mapNotNull { key ->
            val localPreference = localPreferencesMap[key]
            val remotePreference = remotePreferencesMap[key]

            logcat(LogPriority.DEBUG, logTag) {
                "Processing preference key: $key. Local preference: ${localPreference != null}, " +
                    "Remote preference: ${remotePreference != null}"
            }

            when {
                localPreference != null && remotePreference == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using local preference: ${localPreference.key}." }
                    localPreference
                }
                remotePreference != null && localPreference == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using remote preference: ${remotePreference.key}." }
                    remotePreference
                }
                else -> {
                    logcat(LogPriority.DEBUG, logTag) { "Both remote and local have keys. Skipping: $key" }
                    null
                }
            }
        }

        logcat(LogPriority.DEBUG, logTag) {
            "Preferences merge completed. Total merged preferences: ${mergedPreferences.size}"
        }

        return mergedPreferences
    }

    private fun mergeSourcePreferencesLists(
        localPreferences: List<BackupSourcePreferences>?,
        remotePreferences: List<BackupSourcePreferences>?,
    ): List<BackupSourcePreferences> {
        val logTag = "MergeSourcePreferences"

        // Create maps using sourceKey as the unique identifier
        val localPreferencesMap = localPreferences?.associateBy { it.sourceKey } ?: emptyMap()
        val remotePreferencesMap = remotePreferences?.associateBy { it.sourceKey } ?: emptyMap()

        logcat(LogPriority.DEBUG, logTag) {
            "Starting source preferences merge. Local source preferences: ${localPreferences?.size}, " +
                "Remote source preferences: ${remotePreferences?.size}"
        }

        // Merge both source preferences maps
        val mergedSourcePreferences = (localPreferencesMap.keys + remotePreferencesMap.keys).distinct()
            .mapNotNull { sourceKey ->
                val localSourcePreference = localPreferencesMap[sourceKey]
                val remoteSourcePreference = remotePreferencesMap[sourceKey]

                logcat(LogPriority.DEBUG, logTag) {
                    "Processing source preference key: $sourceKey. " +
                        "Local source preference: ${localSourcePreference != null}, " +
                        "Remote source preference: ${remoteSourcePreference != null}"
                }

                when {
                    localSourcePreference != null && remoteSourcePreference == null -> {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Using local source preference: ${localSourcePreference.sourceKey}."
                        }
                        localSourcePreference
                    }
                    remoteSourcePreference != null && localSourcePreference == null -> {
                        logcat(LogPriority.DEBUG, logTag) {
                            "Using remote source preference: ${remoteSourcePreference.sourceKey}."
                        }
                        remoteSourcePreference
                    }
                    localSourcePreference != null && remoteSourcePreference != null -> {
                        // Merge the individual preferences within the source preferences
                        val mergedPrefs =
                            mergeIndividualPreferences(localSourcePreference.prefs, remoteSourcePreference.prefs)
                        BackupSourcePreferences(sourceKey, mergedPrefs)
                    }
                    else -> null
                }
            }

        logcat(LogPriority.DEBUG, logTag) {
            "Source preferences merge completed. Total merged source preferences: ${mergedSourcePreferences.size}"
        }

        return mergedSourcePreferences
    }

    private fun mergeIndividualPreferences(
        localPrefs: List<BackupPreference>,
        remotePrefs: List<BackupPreference>,
    ): List<BackupPreference> {
        val mergedPrefsMap = (localPrefs + remotePrefs).associateBy { it.key }
        return mergedPrefsMap.values.toList()
    }

    // SY -->
    private fun mergeSavedSearchesLists(
        localSearches: List<BackupSavedSearch>?,
        remoteSearches: List<BackupSavedSearch>?,
    ): List<BackupSavedSearch> {
        val logTag = "MergeSavedSearches"

        // Define a function to create a composite key from a BackupSavedSearch
        fun searchCompositeKey(search: BackupSavedSearch): String {
            return "${search.name}|${search.source}"
        }

        // Create maps using the composite key
        val localSearchMap = localSearches?.associateBy { searchCompositeKey(it) } ?: emptyMap()
        val remoteSearchMap = remoteSearches?.associateBy { searchCompositeKey(it) } ?: emptyMap()

        logcat(LogPriority.DEBUG, logTag) {
            "Starting saved searches merge. Local saved searches: ${localSearches?.size}, " +
                "Remote saved searches: ${remoteSearches?.size}"
        }

        // Merge both saved searches maps
        val mergedSearches = (localSearchMap.keys + remoteSearchMap.keys).distinct().mapNotNull { compositeKey ->
            val localSearch = localSearchMap[compositeKey]
            val remoteSearch = remoteSearchMap[compositeKey]

            logcat(LogPriority.DEBUG, logTag) {
                "Processing saved search key: $compositeKey. Local search: ${localSearch != null}, " +
                    "Remote search: ${remoteSearch != null}"
            }

            when {
                localSearch != null && remoteSearch == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using local saved search: ${localSearch.name}." }
                    localSearch
                }
                remoteSearch != null && localSearch == null -> {
                    logcat(LogPriority.DEBUG, logTag) { "Using remote saved search: ${remoteSearch.name}." }
                    remoteSearch
                }

                else -> {
                    logcat(LogPriority.DEBUG, logTag) {
                        "No saved search found for composite key: $compositeKey. Skipping."
                    }
                    null
                }
            }
        }

        logcat(LogPriority.DEBUG, logTag) {
            "Saved searches merge completed. Total merged saved searches: ${mergedSearches.size}"
        }

        return mergedSearches
    }
    // SY <--
}
