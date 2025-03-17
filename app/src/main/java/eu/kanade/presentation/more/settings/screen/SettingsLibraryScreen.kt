package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import androidx.core.content.ContextCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.ResetCategoryFlags
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_HAS_UNSEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_SEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsLibraryScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_library

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCategories = remember { Injekt.get<GetCategories>() }
        val allAnimeCategories by getCategories.subscribe().collectAsState(initial = emptyList())
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        // SY -->
        val unsortedPreferences = remember { Injekt.get<UnsortedPreferences>() }
        // SY <--

        return listOf(
            getCategoriesGroup(
                LocalNavigator.currentOrThrow,
                allAnimeCategories,
                libraryPreferences,
            ),
            getGlobalUpdateGroup(allAnimeCategories, libraryPreferences),
            getEpisodeSwipeActionsGroup(libraryPreferences),
            getMigrationCategory(unsortedPreferences),
            // SY <--
        )
    }

    @Composable
    private fun getCategoriesGroup(
        navigator: Navigator,
        allAnimeCategories: List<Category>,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val userAnimeCategoriesCount = allAnimeCategories.filterNot(Category::isSystemCategory).size

        // For default category
        val animeIds = listOf(libraryPreferences.defaultCategory().defaultValue()) +
            allAnimeCategories.fastMap { it.id.toInt() }

        val animeLabels = listOf(stringResource(MR.strings.default_category_summary)) +
            allAnimeCategories.fastMap { it.visualName }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.general_categories),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.action_edit_anime_categories),
                    subtitle = pluralStringResource(
                        MR.plurals.num_categories,
                        count = userAnimeCategoriesCount,
                        userAnimeCategoriesCount,
                    ),
                    onClick = { navigator.push(CategoryScreen) },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = libraryPreferences.defaultCategory(),
                    title = stringResource(MR.strings.default_anime_category),
                    entries = animeIds.zip(animeLabels).toMap().toImmutableMap(),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.categorizedDisplaySettings(),
                    title = stringResource(MR.strings.categorized_display_settings),
                    onValueChanged = {
                        if (!it) {
                            scope.launch {
                                Injekt.get<ResetCategoryFlags>().await()
                            }
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.showHiddenCategories(),
                    title = stringResource(MR.strings.pref_category_hide_hidden),
                ),
            ),
        )
    }

    @Composable
    private fun getGlobalUpdateGroup(
        allAnimeCategories: List<Category>,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val autoUpdateIntervalPref = libraryPreferences.autoUpdateInterval()
        val autoUpdateInterval by autoUpdateIntervalPref.collectAsState()

        val animeAutoUpdateCategoriesPref = libraryPreferences.updateCategories()
        val animeAutoUpdateCategoriesExcludePref =
            libraryPreferences.updateCategoriesExclude()

        val includedAnime by animeAutoUpdateCategoriesPref.collectAsState()
        val excludedAnime by animeAutoUpdateCategoriesExcludePref.collectAsState()
        var showAnimeCategoriesDialog by rememberSaveable { mutableStateOf(false) }
        if (showAnimeCategoriesDialog) {
            TriStateListDialog(
                title = stringResource(MR.strings.anime_categories),
                message = stringResource(MR.strings.pref_anime_library_update_categories_details),
                items = allAnimeCategories,
                initialChecked = includedAnime.mapNotNull { id -> allAnimeCategories.find { it.id.toString() == id } },
                initialInversed = excludedAnime.mapNotNull { id -> allAnimeCategories.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showAnimeCategoriesDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    animeAutoUpdateCategoriesPref.set(newIncluded.map { it.id.toString() }.toSet())
                    animeAutoUpdateCategoriesExcludePref.set(
                        newExcluded.map { it.id.toString() }
                            .toSet(),
                    )
                    showAnimeCategoriesDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_library_update),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = autoUpdateIntervalPref,
                    title = stringResource(MR.strings.pref_library_update_interval),
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.update_never),
                        12 to stringResource(MR.strings.update_12hour),
                        24 to stringResource(MR.strings.update_24hour),
                        48 to stringResource(MR.strings.update_48hour),
                        72 to stringResource(MR.strings.update_72hour),
                        168 to stringResource(MR.strings.update_weekly),
                    ),
                    onValueChanged = {
                        LibraryUpdateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    pref = libraryPreferences.autoUpdateDeviceRestrictions(),
                    enabled = autoUpdateInterval > 0,
                    title = stringResource(MR.strings.pref_library_update_restriction),
                    subtitle = stringResource(MR.strings.restrictions),
                    entries = persistentMapOf(
                        DEVICE_ONLY_ON_WIFI to stringResource(MR.strings.connected_to_wifi),
                        DEVICE_NETWORK_NOT_METERED to stringResource(MR.strings.network_not_metered),
                        DEVICE_CHARGING to stringResource(MR.strings.charging),
                    ),
                    onValueChanged = {
                        // Post to event looper to allow the preference to be updated.
                        ContextCompat.getMainExecutor(context).execute {
                            LibraryUpdateJob.setupTask(context)
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.anime_categories),
                    subtitle = getCategoriesLabel(
                        allCategories = allAnimeCategories,
                        included = includedAnime,
                        excluded = excludedAnime,
                    ),
                    onClick = { showAnimeCategoriesDialog = true },
                ),
                // SY -->
                Preference.PreferenceItem.ListPreference(
                    pref = libraryPreferences.groupLibraryUpdateType(),
                    title = stringResource(MR.strings.anime_library_group_updates),
                    entries = persistentMapOf(
                        GroupLibraryMode.GLOBAL to stringResource(
                            MR.strings.library_group_updates_global,
                        ),
                        GroupLibraryMode.ALL_BUT_UNGROUPED to stringResource(
                            MR.strings.library_group_updates_all_but_ungrouped,
                        ),
                        GroupLibraryMode.ALL to stringResource(
                            MR.strings.library_group_updates_all,
                        ),
                    ),
                ),
                // SY <--
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.autoUpdateMetadata(),
                    title = stringResource(MR.strings.pref_library_update_refresh_metadata),
                    subtitle = stringResource(MR.strings.pref_library_update_refresh_metadata_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    pref = libraryPreferences.autoUpdateAnimeRestrictions(),
                    title = stringResource(MR.strings.pref_library_update_smart_update),
                    entries = persistentMapOf(
                        ANIME_HAS_UNSEEN to stringResource(MR.strings.pref_update_only_completely_read),
                        ANIME_NON_SEEN to stringResource(MR.strings.pref_update_only_started),
                        ANIME_NON_COMPLETED to stringResource(MR.strings.pref_update_only_non_completed),
                        ANIME_OUTSIDE_RELEASE_PERIOD to stringResource(MR.strings.pref_update_only_in_release_period),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.newShowUpdatesCount(),
                    title = stringResource(MR.strings.pref_library_update_show_tab_badge),
                ),
            ),
        )
    }

    @Composable
    private fun getEpisodeSwipeActionsGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_episode_swipe),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    pref = libraryPreferences.swipeEpisodeStartAction(),
                    title = stringResource(MR.strings.pref_episode_swipe_start),
                    entries = persistentMapOf(
                        LibraryPreferences.EpisodeSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.EpisodeSwipeAction.ToggleBookmark to
                            stringResource(MR.strings.action_bookmark_episode),
                        // AM (FILLERMARK) -->
                        LibraryPreferences.EpisodeSwipeAction.ToggleFillermark to stringResource(
                            MR.strings.action_fillermark_episode,
                        ),
                        // <-- AM (FILLERMARK)
                        LibraryPreferences.EpisodeSwipeAction.ToggleSeen to
                            stringResource(MR.strings.action_mark_as_seen),
                        LibraryPreferences.EpisodeSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = libraryPreferences.swipeEpisodeEndAction(),
                    title = stringResource(MR.strings.pref_episode_swipe_end),
                    entries = persistentMapOf(
                        LibraryPreferences.EpisodeSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.EpisodeSwipeAction.ToggleBookmark to
                            stringResource(MR.strings.action_bookmark_episode),
                        // AM (FILLERMARK) -->
                        LibraryPreferences.EpisodeSwipeAction.ToggleFillermark to stringResource(
                            MR.strings.action_fillermark_episode,
                        ),
                        // <-- AM (FILLERMARK)
                        LibraryPreferences.EpisodeSwipeAction.ToggleSeen to
                            stringResource(MR.strings.action_mark_as_seen),
                        LibraryPreferences.EpisodeSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                ),
            ),
        )
    }

    @Composable
    fun getMigrationCategory(unsortedPreferences: UnsortedPreferences): Preference.PreferenceGroup {
        val skipPreMigration by unsortedPreferences.skipPreMigration().collectAsState()
        val migrationSources by unsortedPreferences.migrationSources().collectAsState()
        return Preference.PreferenceGroup(
            stringResource(SYMR.strings.migration),
            enabled = skipPreMigration || migrationSources.isNotEmpty(),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    pref = unsortedPreferences.skipPreMigration(),
                    title = stringResource(SYMR.strings.skip_pre_migration),
                    subtitle = stringResource(SYMR.strings.pref_skip_pre_migration_summary),
                ),
            ),
        )
    }
    // SY <--
}
