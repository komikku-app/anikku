package tachiyomi.domain

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.service.AppUpdatePolicy

class UnsortedPreferences(
    private val preferenceStore: PreferenceStore,
) {
    // KMK -->
    fun appShouldAutoUpdate() = preferenceStore.getStringSet(
        "should_auto_update",
        setOf(
            AppUpdatePolicy.DEVICE_ONLY_ON_WIFI,
        ),
    )
    // KMK <--

    // SY -->

    fun migrateFlags() = preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)

    fun defaultMangaOrder() = preferenceStore.getString("default_manga_order", "")

    fun migrationSources() = preferenceStore.getString("migrate_sources", "")

    fun smartMigration() = preferenceStore.getBoolean("smart_migrate", false)

    fun useSourceWithMost() = preferenceStore.getBoolean("use_source_with_most", false)

    fun skipPreMigration() = preferenceStore.getBoolean(Preference.appStateKey("skip_pre_migration"), false)

    fun hideNotFoundMigration() = preferenceStore.getBoolean("hide_not_found_migration", false)

    fun showOnlyUpdatesMigration() = preferenceStore.getBoolean("show_only_updates_migration", false)

    fun allowLocalSourceHiddenFolders() = preferenceStore.getBoolean("allow_local_source_hidden_folders", false)
}
