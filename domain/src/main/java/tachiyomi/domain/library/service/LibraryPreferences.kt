package tachiyomi.domain.library.service

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort

class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun displayMode() = preferenceStore.getObject(
        "pref_display_mode_library",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun sortingMode() = preferenceStore.getObject(
        "animelib_sorting_mode",
        LibrarySort.default,
        LibrarySort.Serializer::serialize,
        LibrarySort.Serializer::deserialize,
    )

    // Random Sort Seed

    fun randomSortSeed() = preferenceStore.getInt("library_random_anime_sort_seed", 0)

    fun portraitColumns() = preferenceStore.getInt("pref_animelib_columns_portrait_key", 0)

    fun landscapeColumns() = preferenceStore.getInt("pref_animelib_columns_landscape_key", 0)

    fun lastUpdatedTimestamp() = preferenceStore.getLong(Preference.appStateKey("library_update_last_timestamp"), 0L)
    fun autoUpdateInterval() = preferenceStore.getInt("pref_library_update_interval_key", 0)

    // KMK -->
    fun showUpdatingProgressBanner() = preferenceStore.getBoolean(
        Preference.appStateKey("pref_show_updating_progress_banner_key"),
        true,
    )
    // KMK <--

    fun coverRatios() = preferenceStore.getStringSet(
        Preference.appStateKey("pref_library_cover_ratios_key"),
        emptySet(),
    )

    fun coverColors() = preferenceStore.getStringSet(
        Preference.appStateKey("pref_library_cover_colors_key"),
        emptySet(),
    )
    // KMK <--

    fun autoUpdateDeviceRestrictions() = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )
    fun autoUpdateAnimeRestrictions() = preferenceStore.getStringSet(
        "library_update_manga_restriction",
        setOf(
            ANIME_HAS_UNSEEN,
            ANIME_NON_COMPLETED,
            ANIME_NON_SEEN,
            ANIME_OUTSIDE_RELEASE_PERIOD,
        ),
    )

    fun autoUpdateMetadata() = preferenceStore.getBoolean("auto_update_metadata", false)

    fun showContinueWatchingButton() = preferenceStore.getBoolean(
        "display_continue_reading_button",
        false,
    )

    // region Filter

    fun filterDownloaded() = preferenceStore.getEnum(
        "pref_filter_animelib_downloaded_v2",
        TriState.DISABLED,
    )

    fun filterUnseen() = preferenceStore.getEnum("pref_filter_animelib_unread_v2", TriState.DISABLED)

    fun filterStarted() = preferenceStore.getEnum(
        "pref_filter_animelib_started_v2",
        TriState.DISABLED,
    )

    fun filterBookmarked() = preferenceStore.getEnum(
        "pref_filter_animelib_bookmarked_v2",
        TriState.DISABLED,
    )

    // AM (FILLERMARK) -->
    fun filterFillermarkedAnime() =
        preferenceStore.getEnum("pref_filter_animelib_fillermarked_v2", TriState.DISABLED)
    // <-- AM (FILLERMARK)

    fun filterCompleted() = preferenceStore.getEnum(
        "pref_filter_animelib_completed_v2",
        TriState.DISABLED,
    )

    fun filterIntervalCustom() = preferenceStore.getEnum(
        "pref_filter_library_interval_custom",
        TriState.DISABLED,
    )

    // SY -->
    fun filterLewd() = preferenceStore.getEnum(
        "pref_filter_library_lewd_v2",
        TriState.DISABLED,
    )

    fun libraryReadDuplicateChapters() = preferenceStore.getBoolean("pref_library_mark_duplicate_chapters", false)
    // SY <--

    fun filterTracking(id: Int) = preferenceStore.getEnum(
        "pref_filter_animelib_tracked_${id}_v2",
        TriState.DISABLED,
    )

    // endregion

    // Common badges

    fun downloadBadge() = preferenceStore.getBoolean("display_download_badge", false)

    fun localBadge() = preferenceStore.getBoolean("display_local_badge", true)

    fun languageBadge() = preferenceStore.getBoolean("display_language_badge", true)

    // KMK -->
    fun sourceBadge() = preferenceStore.getBoolean("display_source_badge", true)

    fun useLangIcon() = preferenceStore.getBoolean("display_language_text", true)
    // KMK <--

    fun newShowUpdatesCount() = preferenceStore.getBoolean("library_show_updates_count", true)

    fun newMangaUpdatesCount() = preferenceStore.getInt("library_unread_updates_count", 0)
    fun newUpdatesCount() = preferenceStore.getInt(Preference.appStateKey("library_unseen_updates_count"), 0)

    // endregion

    // region Category

    fun defaultCategory() = preferenceStore.getInt("default_anime_category", -1)

    fun lastUsedCategory() = preferenceStore.getInt(Preference.appStateKey("last_used_anime_category"), 0)

    fun categoryTabs() = preferenceStore.getBoolean("display_category_tabs", true)

    fun categoryNumberOfItems() = preferenceStore.getBoolean("display_number_of_items", false)

    fun categorizedDisplaySettings() = preferenceStore.getBoolean("categorized_display", false)

    // KMK -->
    fun showHiddenCategories() = preferenceStore.getBoolean("show_hidden_categories", false)
    // KMK <--

    fun updateCategories() = preferenceStore.getStringSet("animelib_update_categories", emptySet())

    fun updateCategoriesExclude() = preferenceStore.getStringSet(
        "animelib_update_categories_exclude",
        emptySet(),
    )

    // Mixture Item

    fun filterEpisodeBySeen() = preferenceStore.getLong(
        "default_episode_filter_by_seen",
        Anime.SHOW_ALL,
    )

    fun filterEpisodeByDownloaded() = preferenceStore.getLong(
        "default_episode_filter_by_downloaded",
        Anime.SHOW_ALL,
    )

    fun filterEpisodeByBookmarked() = preferenceStore.getLong(
        "default_episode_filter_by_bookmarked",
        Anime.SHOW_ALL,
    )

    // AM (FILLERMARK) -->
    fun filterEpisodeByFillermarked() =
        preferenceStore.getLong("default_episode_filter_by_fillermarked", Anime.SHOW_ALL)
    // <-- AM (FILLERMARK)

    // and upload date
    fun sortEpisodeBySourceOrNumber() = preferenceStore.getLong(
        "default_episode_sort_by_source_or_number",
        Anime.EPISODE_SORTING_SOURCE,
    )

    fun displayEpisodeByNameOrNumber() = preferenceStore.getLong(
        "default_chapter_display_by_name_or_number",
        Anime.EPISODE_DISPLAY_NAME,
    )

    fun sortEpisodeByAscendingOrDescending() = preferenceStore.getLong(
        "default_chapter_sort_by_ascending_or_descending",
        Anime.EPISODE_SORT_DESC,
    )

    fun setEpisodeSettingsDefault(anime: Anime) {
        filterEpisodeBySeen().set(anime.unseenFilterRaw)
        filterEpisodeByDownloaded().set(anime.downloadedFilterRaw)
        filterEpisodeByBookmarked().set(anime.bookmarkedFilterRaw)
        // AM (FILLERMARK) -->
        filterEpisodeByFillermarked().set(anime.fillermarkedFilterRaw)
        // <-- AM (FILLERMARK)
        sortEpisodeBySourceOrNumber().set(anime.sorting)
        displayEpisodeByNameOrNumber().set(anime.displayMode)
        sortEpisodeByAscendingOrDescending().set(
            if (anime.sortDescending()) Anime.EPISODE_SORT_DESC else Anime.EPISODE_SORT_ASC,
        )
    }

    fun autoClearChapterCache() = preferenceStore.getBoolean("auto_clear_chapter_cache", false)

    // region Swipe Actions

    fun swipeEpisodeStartAction() = preferenceStore.getEnum(
        "pref_episode_swipe_end_action",
        EpisodeSwipeAction.ToggleBookmark,
    )

    fun swipeEpisodeEndAction() = preferenceStore.getEnum(
        "pref_episode_swipe_start_action",
        EpisodeSwipeAction.ToggleSeen,
    )

    // endregion

    enum class EpisodeSwipeAction {
        ToggleSeen,
        ToggleBookmark,

        // AM (FILLERMARK) -->
        ToggleFillermark,
        // <-- AM (FILLERMARK)

        Download,
        Disabled,
    }

    // SY -->
    fun sortTagsForLibrary() = preferenceStore.getStringSet("sort_anime_tags_for_library", mutableSetOf())

    fun groupLibraryUpdateType() = preferenceStore.getEnum("group_anime_library_update_type", GroupLibraryMode.GLOBAL)

    fun groupLibraryBy() = preferenceStore.getInt("group_anime_library_by", LibraryGroup.BY_DEFAULT)
    // SY <--

    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"

        const val ANIME_NON_COMPLETED = "anime_ongoing"
        const val ANIME_HAS_UNSEEN = "anime_fully_seen"
        const val ANIME_NON_SEEN = "anime_started"
        const val ANIME_OUTSIDE_RELEASE_PERIOD = "anime_outside_release_period"
    }
}
