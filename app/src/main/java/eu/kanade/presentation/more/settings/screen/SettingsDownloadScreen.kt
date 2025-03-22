package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastMap
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.OutlinedNumericChooser
import tachiyomi.presentation.core.components.material.padding
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
        val allAnimeCategories by getCategories.subscribe().collectAsState(initial = emptyList())
        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val speedLimit by downloadPreferences.downloadSpeedLimit().collectAsState()
        var currentSpeedLimit by remember { mutableIntStateOf(speedLimit) }
        var showDownloadLimitDialog by rememberSaveable { mutableStateOf(false) }
        if (showDownloadLimitDialog) {
            DownloadLimitDialog(
                initialValue = currentSpeedLimit,
                onDismissRequest = { showDownloadLimitDialog = false },
                onValueChanged = {
                    currentSpeedLimit = it
                },
                onConfirm = {
                    downloadPreferences.downloadSpeedLimit().set(currentSpeedLimit)
                    showDownloadLimitDialog = false
                },
            )
        }
        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = downloadPreferences.downloadOnlyOverWifi(),
                title = stringResource(MR.strings.connected_to_wifi),
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.download_speed_limit),
                subtitle = if (speedLimit == 0) {
                    stringResource(MR.strings.off)
                } else {
                    "$speedLimit KiB/s"
                },
                onClick = { showDownloadLimitDialog = true },
            ),
            Preference.PreferenceItem.ListPreference(
                pref = downloadPreferences.numberOfDownloads(),
                title = stringResource(MR.strings.pref_download_slots),
                entries = (1..5).associateWith { it.toString() }.toImmutableMap(),
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.download_slots_info)),
            getDeleteChaptersGroup(
                downloadPreferences = downloadPreferences,
                animeCategories = allAnimeCategories.toImmutableList(),
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allAnimeCategories = allAnimeCategories.toImmutableList(),
            ),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
            getExternalDownloaderGroup(
                downloadPreferences = downloadPreferences,
                basePreferences = basePreferences,
            ),
        )
    }

    @Composable
    private fun getDeleteChaptersGroup(
        downloadPreferences: DownloadPreferences,
        animeCategories: ImmutableList<Category>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_delete_chapters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadPreferences.removeAfterMarkedAsSeen(),
                    title = stringResource(MR.strings.pref_remove_after_marked_as_read),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = downloadPreferences.removeAfterReadSlots(),
                    title = stringResource(MR.strings.pref_remove_after_read),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        0 to stringResource(MR.strings.last_read_chapter),
                        1 to stringResource(MR.strings.second_to_last),
                        2 to stringResource(MR.strings.third_to_last),
                        3 to stringResource(MR.strings.fourth_to_last),
                        4 to stringResource(MR.strings.fifth_to_last),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadPreferences.removeBookmarkedChapters(),
                    title = stringResource(MR.strings.pref_remove_bookmarked_chapters),
                ),
                getExcludedAnimeCategoriesPreference(
                    downloadPreferences = downloadPreferences,
                    categories = { animeCategories },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedAnimeCategoriesPreference(
        downloadPreferences: DownloadPreferences,
        categories: () -> List<Category>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            pref = downloadPreferences.removeExcludeCategories(),
            title = stringResource(MR.strings.pref_remove_exclude_categories_anime),
            entries = categories()
                .associate { it.id.toString() to it.visualName }
                .toImmutableMap(),
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allAnimeCategories: ImmutableList<Category>,
    ): Preference.PreferenceGroup {
        val downloadNewEpisodesPref = downloadPreferences.downloadNewChapters()
        val downloadNewUnseenEpisodesOnlyPref = downloadPreferences.downloadNewUnreadChaptersOnly()
        val downloadNewEpisodeCategoriesPref = downloadPreferences.downloadNewChapterCategories()
        val downloadNewEpisodeCategoriesExcludePref = downloadPreferences.downloadNewChapterCategoriesExclude()

        val downloadNewEpisodes by downloadNewEpisodesPref.collectAsState()

        val includedAnime by downloadNewEpisodeCategoriesPref.collectAsState()
        val excludedAnime by downloadNewEpisodeCategoriesExcludePref.collectAsState()
        var showAnimeDialog by rememberSaveable { mutableStateOf(false) }
        if (showAnimeDialog) {
            TriStateListDialog(
                title = stringResource(MR.strings.anime_categories),
                message = stringResource(MR.strings.pref_download_new_categories_details),
                items = allAnimeCategories,
                initialChecked = includedAnime.mapNotNull { id -> allAnimeCategories.find { it.id.toString() == id } },
                initialInversed = excludedAnime.mapNotNull { id -> allAnimeCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showAnimeDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewEpisodeCategoriesPref.set(
                        newIncluded.fastMap { it.id.toString() }.toSet(),
                    )
                    downloadNewEpisodeCategoriesExcludePref.set(
                        newExcluded.fastMap { it.id.toString() }.toSet(),
                    )
                    showAnimeDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadNewEpisodesPref,
                    title = stringResource(MR.strings.pref_download_new_episodes),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = downloadNewUnseenEpisodesOnlyPref,
                    title = stringResource(MR.strings.pref_download_new_unseen_episodes_only),
                    enabled = downloadNewEpisodes,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.anime_categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allAnimeCategories,
                        included = includedAnime,
                        excluded = excludedAnime,
                    ),
                    onClick = { showAnimeDialog = true },
                    enabled = downloadNewEpisodes,
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
                Preference.PreferenceItem.InfoPreference(
                    stringResource(MR.strings.download_ahead_info),
                ),
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

    @Composable
    private fun DownloadLimitDialog(
        initialValue: Int,
        onDismissRequest: () -> Unit,
        onValueChanged: (newValue: Int) -> Unit,
        onConfirm: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(MR.strings.download_speed_limit)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .padding(bottom = MaterialTheme.padding.medium)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        OutlinedNumericChooser(
                            label = stringResource(MR.strings.download_speed_limit),
                            placeholder = "0",
                            suffix = "KiB/s",
                            value = initialValue,
                            step = 100,
                            min = 0,
                            onValueChanged = onValueChanged,
                        )
                    }
                    Text(text = stringResource(MR.strings.download_speed_limit_hint))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
        )
    }
}
