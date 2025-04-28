package tachiyomi.domain.download.service

import tachiyomi.core.common.preference.PreferenceStore

class DownloadPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun downloadOnlyOverWifi() = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    fun useExternalDownloader() = preferenceStore.getBoolean("use_external_downloader", false)

    fun externalDownloaderSelection() = preferenceStore.getString(
        "external_downloader_selection",
        "",
    )

    fun autoDownloadWhileReading() = preferenceStore.getInt("auto_download_while_watching", 0)

    fun removeAfterReadSlots() = preferenceStore.getInt("remove_after_read_slots", -1)

    fun removeAfterMarkedAsSeen() = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_read_key",
        false,
    )

    fun removeBookmarkedChapters() = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    // AM (FILLERMARK) -->
    fun notDownloadFillermarkedItems() = preferenceStore.getBoolean("pref_no_download_fillermarked", false)
    // <-- AM (FILLERMARK)

    fun removeExcludeCategories() = preferenceStore.getStringSet(
        "remove_exclude_anime_categories",
        emptySet(),
    )

    fun downloadNewChapters() = preferenceStore.getBoolean("download_new_episode", false)

    fun downloadNewChapterCategories() = preferenceStore.getStringSet(
        "download_new_anime_categories",
        emptySet(),
    )

    fun downloadNewChapterCategoriesExclude() = preferenceStore.getStringSet(
        "download_new_anime_categories_exclude",
        emptySet(),
    )

    fun numberOfDownloads() = preferenceStore.getInt("download_slots", 1)
    fun downloadSpeedLimit() = preferenceStore.getInt("download_speed_limit", 0)

    fun downloadNewUnreadChaptersOnly() = preferenceStore.getBoolean("download_new_unread_episodes_only", false)

    // KMK -->
    fun downloadCacheRenewInterval() = preferenceStore.getInt("download_cache_renew_interval", 1)
    // KMK <--
}
