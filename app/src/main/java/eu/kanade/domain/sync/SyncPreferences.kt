package eu.kanade.domain.sync

import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.models.SyncTriggerOptions
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import java.util.UUID

/** Made by @kaiserbh. Huge thx for making this feature. **/

@Suppress("TooManyFunctions")
class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun clientHost() = preferenceStore.getString("sync_client_host", "https://sync.tachiyomi.org")
    fun clientAPIKey() = preferenceStore.getString("sync_client_api_key", "")
    fun lastSyncTimestamp() = preferenceStore.getLong(Preference.appStateKey("last_sync_timestamp"), 0L)

    fun lastSyncEtag() = preferenceStore.getString("sync_etag", "")

    fun syncInterval() = preferenceStore.getInt("sync_interval", 0)
    fun syncService() = preferenceStore.getInt("sync_service", 0)

    // KMK -->
    fun webDavUrl() = preferenceStore.getString("webdav_url", "")
    fun webDavUsername() = preferenceStore.getString("webdav_username", "")
    fun webDavPassword() = preferenceStore.getString("webdav_password", "")
    fun webDavFolder() = preferenceStore.getString("webdav_folder", "komikku")
    // KMK <--

    fun googleDriveAccessToken() = preferenceStore.getString(
        Preference.appStateKey("google_drive_access_token"),
        "",
    )

    fun googleDriveRefreshToken() = preferenceStore.getString(
        Preference.appStateKey("google_drive_refresh_token"),
        "",
    )

    fun uniqueDeviceID(): String {
        val uniqueIDPreference = preferenceStore.getString(Preference.appStateKey("unique_device_id"), "")

        // Retrieve the current value of the preference
        var uniqueID = uniqueIDPreference.get()
        if (uniqueID.isBlank()) {
            uniqueID = UUID.randomUUID().toString()
            uniqueIDPreference.set(uniqueID)
        }

        return uniqueID
    }

    fun isSyncEnabled(): Boolean {
        return syncService().get() != SyncManager.SyncService.NONE.value
    }

    fun getSyncSettings(): SyncSettings {
        return SyncSettings(
            libraryEntries = preferenceStore.getBoolean("anime_lib_entries", true).get(),
            categories = preferenceStore.getBoolean("anime_categories", true).get(),
            episodes = preferenceStore.getBoolean("episodes", true).get(),
            tracking = preferenceStore.getBoolean("anime_tracking", true).get(),
            history = preferenceStore.getBoolean("anime_history", true).get(),
            appSettings = preferenceStore.getBoolean("appSettings", true).get(),
            extensionRepoSettings = preferenceStore.getBoolean("extensionRepoSettings", true).get(),
            sourceSettings = preferenceStore.getBoolean("sourceSettings", true).get(),
            privateSettings = preferenceStore.getBoolean("privateSettings", true).get(),

            // SY -->
            customInfo = preferenceStore.getBoolean("customInfo", true).get(),
            seenEntries = preferenceStore.getBoolean("seenEntries", true).get(),
            savedSearchesFeeds = preferenceStore.getBoolean("savedSearchesFeeds", true).get(),
            // SY <--
        )
    }

    fun setSyncSettings(syncSettings: SyncSettings) {
        preferenceStore.getBoolean("anime_lib_entries", true).set(syncSettings.libraryEntries)
        preferenceStore.getBoolean("anime_categories", true).set(syncSettings.categories)
        preferenceStore.getBoolean("episodes", true).set(syncSettings.episodes)
        preferenceStore.getBoolean("anime_tracking", true).set(syncSettings.tracking)
        preferenceStore.getBoolean("anime_history", true).set(syncSettings.history)
        preferenceStore.getBoolean("appSettings", true).set(syncSettings.appSettings)
        preferenceStore.getBoolean("extensionRepoSettings", true).set(syncSettings.extensionRepoSettings)
        preferenceStore.getBoolean("sourceSettings", true).set(syncSettings.sourceSettings)
        preferenceStore.getBoolean("privateSettings", true).set(syncSettings.privateSettings)

        // SY -->
        preferenceStore.getBoolean("customInfo", true).set(syncSettings.customInfo)
        preferenceStore.getBoolean("seenEntries", true).set(syncSettings.seenEntries)
        preferenceStore.getBoolean("savedSearchesFeeds", true).set(syncSettings.savedSearchesFeeds)
        // SY <--
    }

    fun getSyncTriggerOptions(): SyncTriggerOptions {
        return SyncTriggerOptions(
            syncOnEpisodeSeen = preferenceStore.getBoolean("sync_on_episode_seen", false).get(),
            syncOnEpisodeOpen = preferenceStore.getBoolean("sync_on_episode_open", false).get(),
            syncOnAppStart = preferenceStore.getBoolean("sync_on_app_start", false).get(),
            syncOnAppResume = preferenceStore.getBoolean("sync_on_app_resume", false).get(),
        )
    }

    fun setSyncTriggerOptions(syncTriggerOptions: SyncTriggerOptions) {
        preferenceStore.getBoolean("sync_on_episode_seen", false)
            .set(syncTriggerOptions.syncOnEpisodeSeen)
        preferenceStore.getBoolean("sync_on_episode_open", false)
            .set(syncTriggerOptions.syncOnEpisodeOpen)
        preferenceStore.getBoolean("sync_on_app_start", false)
            .set(syncTriggerOptions.syncOnAppStart)
        preferenceStore.getBoolean("sync_on_app_resume", false)
            .set(syncTriggerOptions.syncOnAppResume)
    }

    // KMK -->
    fun showSyncingProgressBanner() = preferenceStore.getBoolean(
        Preference.appStateKey("pref_show_syncing_progress_banner_key"),
        true,
    )
    // KMK <--
}
