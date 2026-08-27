package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import mihon.feature.airingschedule.ScheduleDataRefreshWorker
import mihon.feature.airingschedule.SchedulePreferences
import mihon.feature.airingschedule.ScheduleRefreshWorker
import mihon.feature.airingschedule.notification.ScheduleNotifications
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsScheduleScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_schedule

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val schedulePreferences = remember { Injekt.get<SchedulePreferences>() }
        val getExtensionsByType = remember { Injekt.get<GetExtensionsByType>() }
        val extensionsState by getExtensionsByType.subscribe().collectAsState(initial = null)

        val autoRefreshEnabledPref = schedulePreferences.scheduleAutoRefreshEnabled()
        val autoRefreshEnabled by autoRefreshEnabledPref.changes().collectAsState(initial = autoRefreshEnabledPref.get())
        val autoRefreshFrequencyPref = schedulePreferences.scheduleAutoRefreshFrequency()
        val autoRefreshFrequency by autoRefreshFrequencyPref.changes().collectAsState(initial = autoRefreshFrequencyPref.get())

        val uploadDelayEnabledPref = schedulePreferences.uploadDelayEnabled()
        val uploadDelayEnabled by uploadDelayEnabledPref.changes().collectAsState(initial = uploadDelayEnabledPref.get())
        val uploadDelayIntervalPref = schedulePreferences.uploadDelayRefreshInterval()
        val uploadDelayInterval by uploadDelayIntervalPref.changes().collectAsState(initial = uploadDelayIntervalPref.get())
        val customUploadDelayPref = schedulePreferences.customUploadDelayMinutes()
        val customUploadDelay by customUploadDelayPref.changes().collectAsState(initial = customUploadDelayPref.get())

        LaunchedEffect(autoRefreshEnabled, autoRefreshFrequency) {
            if (autoRefreshEnabled) {
                ScheduleDataRefreshWorker.schedule(context, autoRefreshFrequency)
            } else {
                ScheduleDataRefreshWorker.cancel(context)
            }
        }

        LaunchedEffect(uploadDelayEnabled, uploadDelayInterval) {
            if (uploadDelayEnabled) {
                ScheduleRefreshWorker.schedule(context, uploadDelayInterval)
            } else {
                ScheduleRefreshWorker.cancel(context)
            }
        }

        var exactAlarmsAllowed by remember { mutableStateOf(ScheduleNotifications.canScheduleExactAlarms(context)) }
        LaunchedEffect(Unit) {
            exactAlarmsAllowed = ScheduleNotifications.canScheduleExactAlarms(context)
        }

        val installedSourceOptions = remember(extensionsState) {
            extensionsState?.installed
                ?.flatMap { ext -> ext.sources.map { src -> src.id.toString() to "${ext.name} › ${src.name}" } }
                ?.distinctBy { it.first }
                ?.sortedBy { it.second }
                ?.toMap()
                ?.toImmutableMap()
                ?: emptyMap<String, String>().toImmutableMap()
        }

        val titleLanguageOptions = mapOf(
            SchedulePreferences.TitleLanguage.USER_PREFERRED to "User Preferred (AniList default)",
            SchedulePreferences.TitleLanguage.ENGLISH to "English",
            SchedulePreferences.TitleLanguage.ROMAJI to "Romaji",
            SchedulePreferences.TitleLanguage.NATIVE to "Native",
        ).toImmutableMap()

        val intervalOptions = mapOf(
            SchedulePreferences.UploadDelayInterval.THIRTY_MIN to "Every 30 minutes",
            SchedulePreferences.UploadDelayInterval.ONE_HOUR to "Every 1 hour",
            SchedulePreferences.UploadDelayInterval.TWO_HOURS to "Every 2 hours",
            SchedulePreferences.UploadDelayInterval.SIX_HOURS to "Every 6 hours",
            SchedulePreferences.UploadDelayInterval.TWELVE_HOURS to "Every 12 hours",
            SchedulePreferences.UploadDelayInterval.CUSTOM to "Custom delay (set your own minutes)",
            SchedulePreferences.UploadDelayInterval.NEVER to "Never (manual only)",
        ).toImmutableMap()

        val autoRefreshFrequencyOptions = mapOf(
            SchedulePreferences.AutoRefreshFrequency.EVERY_1_DAY to "Every 1 day",
            SchedulePreferences.AutoRefreshFrequency.EVERY_2_DAYS to "Every 2 days",
            SchedulePreferences.AutoRefreshFrequency.EVERY_3_DAYS to "Every 3 days",
            SchedulePreferences.AutoRefreshFrequency.EVERY_4_DAYS to "Every 4 days",
            SchedulePreferences.AutoRefreshFrequency.EVERY_5_DAYS to "Every 5 days",
            SchedulePreferences.AutoRefreshFrequency.EVERY_6_DAYS to "Every 6 days",
            SchedulePreferences.AutoRefreshFrequency.EVERY_7_DAYS to "Every 7 days",
        ).toImmutableMap()

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_schedule_auto_refresh_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = schedulePreferences.scheduleAutoRefreshEnabled(),
                        title = "Auto-refresh schedule",
                        subtitle = "Automatically refresh the weekly airing schedule in the background",
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = schedulePreferences.scheduleAutoRefreshFrequency(),
                        title = "Refresh frequency",
                        subtitle = "How often to refresh the schedule (1–7 days). Default: every 7 days",
                        entries = autoRefreshFrequencyOptions,
                        enabled = autoRefreshEnabled,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_schedule_sources_title),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.MultiSelectListPreference(
                        pref = schedulePreferences.favoriteSourceIds(),
                        title = stringResource(MR.strings.pref_schedule_favorite_sources),
                        subtitle = stringResource(MR.strings.pref_schedule_favorite_sources_summary),
                        entries = installedSourceOptions,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = schedulePreferences.showOnlyFavoriteSources(),
                        title = stringResource(MR.strings.pref_schedule_show_only_favorites),
                        subtitle = "Off by default. When on, only shows anime that are already in your library from one of your selected favorite/pinned sources.",
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = schedulePreferences.autoAddFromPinnedSources(),
                        title = "Auto-add via pinned sources",
                        subtitle = "When tapping the bookmark button, search only in your pinned sources (from Browse) using priority order — 1st pinned gets highest priority",
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_schedule_upload_delay_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = schedulePreferences.uploadDelayEnabled(),
                        title = "Auto-sync source upload time",
                        subtitle = "For anime already in your library from a favorite/pinned source, checks that source's real episode list against AniList's air time to learn its upload delay. Priority: 1st pinned source → 2nd → etc.",
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = schedulePreferences.uploadDelayRefreshInterval(),
                        title = "Refresh interval",
                        subtitle = "How often to re-check and continuously refine the learned upload delay per source using a running average",
                        entries = intervalOptions,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        pref = schedulePreferences.customUploadDelayMinutes(),
                        title = "Custom delay (minutes)",
                        subtitle = "Only used when Refresh interval is set to Custom. A fixed number of minutes added to the official air time — shifts the expected upload time and countdown shown for every episode, in place of the auto-learned delay. Negative values mean early.",
                        enabled = uploadDelayInterval == SchedulePreferences.UploadDelayInterval.CUSTOM,
                        onValueChanged = { it.trim().toLongOrNull()?.let { minutes -> minutes in -24 * 60..24 * 60 } ?: false },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = "Notifications",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.InfoPreference(
                        title = if (exactAlarmsAllowed) {
                            "Exact alarm permission is granted — the notification bell will fire at the precise air time, even in the background with battery optimization enabled."
                        } else {
                            "Exact alarm permission is not granted. On Android 13+, the notification bell may fire late without it. Tap Settings → Apps → Anikku Modified → Alarms & reminders to allow it, or tap below."
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = "Grant exact alarm permission",
                        subtitle = if (exactAlarmsAllowed) "Already granted" else "Required for reliable on-time episode alerts",
                        onClick = { ScheduleNotifications.requestExactAlarmPermission(context) },
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_schedule_display_group),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        pref = schedulePreferences.titleLanguage(),
                        title = "Preferred title language",
                        subtitle = "Language used to display anime titles in the schedule",
                        entries = titleLanguageOptions,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = schedulePreferences.showAdultContent(),
                        title = "Show 18+ anime",
                        subtitle = "Include adult-only anime in the airing schedule",
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_schedule_about_title),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.InfoPreference(
                        title = "The airing schedule is powered by AniList. Upload delay tracking monitors when episodes appear on your pinned sources vs the official air time — uses priority order (1st pinned source takes precedence). Tap the play or search icon on any anime to find it in All sources. Tap the bookmark icon to add it to your library — enable 'Auto-add via pinned sources' to search only in your pinned sources.",
                    ),
                ),
            ),
        )
    }
}
