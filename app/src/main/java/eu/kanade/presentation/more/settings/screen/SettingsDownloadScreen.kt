package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.util.fastMap
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.ank.AMR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDownloadScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsDownloadScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_downloads

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allCategories by getCategories.subscribe().collectAsState(initial = emptyList())

        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val basePreferences = remember { Injekt.get<BasePreferences>() }
        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.downloadOnlyOverWifi(),
                title = stringResource(MR.strings.connected_to_wifi),
            ),
            getDeleteChaptersGroup(
                downloadPreferences = downloadPreferences,
                categories = allCategories,
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allCategories = allCategories,
            ),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
            // KMK -->
            getDownloadCacheRenewInterval(downloadPreferences = downloadPreferences),
            // KMK <--
            getExternalDownloaderGroup(
                downloadPreferences = downloadPreferences,
                basePreferences = basePreferences,
            ),
        )
    }

    @Composable
    private fun getDeleteChaptersGroup(
        downloadPreferences: DownloadPreferences,
        categories: List<Category>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AMR.strings.pref_category_delete_episodes),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadPreferences.removeAfterMarkedAsRead(),
                    title = stringResource(AMR.strings.pref_remove_after_marked_as_seen),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.removeAfterReadSlots(),
                    title = stringResource(AMR.strings.pref_remove_after_watching),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        0 to stringResource(AMR.strings.last_seen_episode),
                        1 to stringResource(AMR.strings.second_to_last_episode),
                        2 to stringResource(AMR.strings.third_to_last_episode),
                        3 to stringResource(AMR.strings.fourth_to_last_episode),
                        4 to stringResource(AMR.strings.fifth_to_last_episode),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadPreferences.removeBookmarkedChapters(),
                    title = stringResource(AMR.strings.pref_remove_bookmarked_episodes),
                ),
                getExcludedCategoriesPreference(
                    downloadPreferences = downloadPreferences,
                    categories = { categories },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedCategoriesPreference(
        downloadPreferences: DownloadPreferences,
        categories: () -> List<Category>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            pref = downloadPreferences.removeExcludeCategories(),
            title = stringResource(MR.strings.pref_remove_exclude_categories),
            subtitleProvider = { v, e ->
                val combined = remember(v, e) {
                    v.map { e[it] }
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString()
                } ?: stringResource(MR.strings.none)
                "%s".format(combined)
            },
            entries = categories()
                .associate { it.id.toString() to it.visualName }
                .toImmutableMap(),
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allCategories: List<Category>,
    ): Preference.PreferenceGroup {
        val downloadNewChaptersPref = downloadPreferences.downloadNewChapters()
        val downloadNewUnreadChaptersOnlyPref = downloadPreferences.downloadNewUnreadChaptersOnly()
        val downloadNewChapterCategoriesPref = downloadPreferences.downloadNewChapterCategories()
        val downloadNewChapterCategoriesExcludePref = downloadPreferences.downloadNewChapterCategoriesExclude()

        val downloadNewChapters by downloadNewChaptersPref.collectAsState()

        val included by downloadNewChapterCategoriesPref.collectAsState()
        val excluded by downloadNewChapterCategoriesExcludePref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            TriStateListDialog(
                title = stringResource(MR.strings.categories),
                message = stringResource(MR.strings.pref_download_new_categories_details),
                items = allCategories,
                initialChecked = included.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                initialInversed = excluded.mapNotNull { id -> allCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewChapterCategoriesPref.set(newIncluded.fastMap { it.id.toString() }.toSet())
                    downloadNewChapterCategoriesExcludePref.set(newExcluded.fastMap { it.id.toString() }.toSet())
                    showDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadNewChaptersPref,
                    title = stringResource(MR.strings.pref_download_new_episodes),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadNewUnreadChaptersOnlyPref,
                    title = stringResource(MR.strings.pref_download_new_unseen_episodes_only),
                    enabled = downloadNewChapters,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allCategories,
                        included = included,
                        excluded = excluded,
                    ),
                    onClick = { showDialog = true },
                    enabled = downloadNewChapters,
                ),
            ),
        )
    }

    @Composable
    private fun getDownloadAheadGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.download_ahead),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.autoDownloadWhileReading(),
                    title = stringResource(MR.strings.auto_download_while_watching),
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith {
                            if (it == 0) {
                                stringResource(MR.strings.disabled)
                            } else {
                                pluralStringResource(MR.plurals.next_unseen_episodes, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.download_ahead_info_anime)),
            ),
        )
    }

    @Composable
    private fun getExternalDownloaderGroup(
        downloadPreferences: DownloadPreferences,
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val useExternalDownloader = downloadPreferences.useExternalDownloader()
        val externalDownloaderPreference = downloadPreferences.externalDownloaderSelection()

        val pm = basePreferences.context.packageManager
        val installedPackages = pm.getInstalledPackages(0)
        val supportedDownloaders = installedPackages.filter {
            when (it.packageName) {
                "idm.internet.download.manager" -> true
                "idm.internet.download.manager.plus" -> true
                "idm.internet.download.manager.adm.lite" -> true
                "com.dv.adm" -> true
                else -> false
            }
        }
        val packageNames = supportedDownloaders.map { it.packageName }
        val packageNamesReadable = supportedDownloaders
            .map { pm.getApplicationLabel(it.applicationInfo!!).toString() }

        val packageNamesMap: Map<String, String> =
            mapOf("" to "None") + packageNames.zip(packageNamesReadable).toMap()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_external_downloader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = useExternalDownloader,
                    title = stringResource(MR.strings.pref_use_external_downloader),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = externalDownloaderPreference,
                    title = stringResource(MR.strings.pref_external_downloader_selection),
                    entries = packageNamesMap.toPersistentMap(),
                ),
            ),
        )
    }

    // KMK -->
    @Composable
    private fun getDownloadCacheRenewInterval(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.download_cache_renew_interval),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.downloadCacheRenewInterval(),
                    title = stringResource(KMR.strings.download_cache_renew_interval),
                    entries = persistentMapOf(
                        -1 to stringResource(KMR.strings.download_cache_renew_interval_manual),
                        1 to stringResource(KMR.strings.download_cache_renew_interval_1hour),
                        2 to stringResource(KMR.strings.download_cache_renew_interval_2hour),
                        6 to stringResource(KMR.strings.download_cache_renew_interval_6hour),
                        12 to stringResource(KMR.strings.download_cache_renew_interval_12hour),
                        24 to stringResource(KMR.strings.download_cache_renew_interval_24hour),
                    ),
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.download_cache_renew_interval_info)),
            ),
        )
    }
    // KMK <--
}
