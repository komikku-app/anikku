package eu.kanade.tachiyomi.data.backup.create

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR

data class BackupOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val episodes: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val seenEntries: Boolean = true,
    val appSettings: Boolean = true,
    val extensionRepoSettings: Boolean = true,
    val customButton: Boolean = true,
    val sourceSettings: Boolean = true,
    val privateSettings: Boolean = false,
    // SY -->
    val customInfo: Boolean = true,
    val savedSearchesFeeds: Boolean = true,
    // SY <--
    val extensions: Boolean = false,
) {

    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        episodes,
        tracking,
        history,
        seenEntries,
        appSettings,
        extensionRepoSettings,
        customButton,
        sourceSettings,
        privateSettings,
        // SY -->
        customInfo,
        savedSearchesFeeds,
        // SY <--
        extensions,
    )

    fun canCreate() = libraryEntries ||
        categories ||
        appSettings ||
        extensionRepoSettings ||
        customButton ||
        sourceSettings ||
        savedSearchesFeeds

    companion object {
        val libraryOptions = persistentListOf(
            Entry(
                label = MR.strings.entries,
                getter = BackupOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.episodes,
                getter = BackupOptions::episodes,
                setter = { options, enabled -> options.copy(episodes = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.track,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = AMR.strings.non_library_settings,
                getter = BackupOptions::seenEntries,
                setter = { options, enabled -> options.copy(seenEntries = enabled) },
                enabled = { it.libraryEntries },
            ),
            // SY -->
            Entry(
                label = SYMR.strings.custom_entry_info,
                getter = BackupOptions::customInfo,
                setter = { options, enabled -> options.copy(customInfo = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                // KMK-->
                label = KMR.strings.saved_searches_feeds,
                // KMK <--
                getter = BackupOptions::savedSearchesFeeds,
                setter = { options, enabled -> options.copy(savedSearchesFeeds = enabled) },
            ),
            // SY <--
        )

        val settingsOptions = persistentListOf(
            Entry(
                label = MR.strings.app_settings,
                getter = BackupOptions::appSettings,
                setter = { options, enabled -> options.copy(appSettings = enabled) },
            ),
            Entry(
                label = MR.strings.extensionRepo_settings,
                getter = BackupOptions::extensionRepoSettings,
                setter = { options, enabled -> options.copy(extensionRepoSettings = enabled) },
            ),
            Entry(
                label = MR.strings.custom_button_settings,
                getter = BackupOptions::customButton,
                setter = { options, enabled -> options.copy(customButton = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = BackupOptions::sourceSettings,
                setter = { options, enabled -> options.copy(sourceSettings = enabled) },
            ),
            Entry(
                label = MR.strings.private_settings,
                getter = BackupOptions::privateSettings,
                setter = { options, enabled -> options.copy(privateSettings = enabled) },
                enabled = { it.appSettings || it.sourceSettings },
            ),
        )

        val extensionOptions = persistentListOf(
            Entry(
                label = MR.strings.label_extensions,
                getter = BackupOptions::extensions,
                setter = { options, enabled -> options.copy(extensions = enabled) },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = BackupOptions(
            libraryEntries = array[0],
            categories = array[1],
            episodes = array[2],
            tracking = array[3],
            history = array[4],
            seenEntries = array[5],
            appSettings = array[6],
            extensionRepoSettings = array[7],
            customButton = array[8],
            sourceSettings = array[9],
            privateSettings = array[10],
            extensions = array[11],
            // SY -->
            customInfo = array[12],
            savedSearchesFeeds = array[13],
            // SY <--
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (BackupOptions) -> Boolean,
        val setter: (BackupOptions, Boolean) -> BackupOptions,
        val enabled: (BackupOptions) -> Boolean = { true },
    )
}
