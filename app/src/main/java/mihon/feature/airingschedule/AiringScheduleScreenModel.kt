package mihon.feature.airingschedule

import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.feature.airingschedule.components.BellNotifyState
import mihon.feature.airingschedule.notification.ScheduleNotifications
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

class AiringScheduleScreenModel : StateScreenModel<AiringScheduleScreenModel.State>(State()) {

    private val repository = AiringScheduleRepository()
    private val schedulePrefs: SchedulePreferences = Injekt.get()
    private val sourcePreferences: SourcePreferences = Injekt.get()
    private val uploadDelayTracker: UploadDelayTracker = Injekt.get()
    private val application: Application = Injekt.get()
    private val getLibraryAnime: GetLibraryAnime = Injekt.get()

    private var allEntries: List<AiringScheduleEntry> = emptyList()
    private var hasLoaded = false

    init {
        loadSchedule()
        observePreferences()
        observeLibrary()
    }

    private fun observeLibrary() {
        screenModelScope.launch {
            getLibraryAnime.subscribe().collectLatest { libraryAnime ->
                val titles = libraryAnime.map { lib ->
                    lib.anime.title.trim().lowercase()
                }.toSet()
                // Maps a lowercased title to the set of source ids that carry it in the
                // user's library. This is what lets "filter by favorite source" and "filter
                // by source availability" check a *specific* matched source for a *specific*
                // anime, instead of a global proxy applied to every entry.
                val sourcesByTitle = libraryAnime
                    .groupBy({ it.anime.title.trim().lowercase() }, { it.anime.source.toString() })
                    .mapValues { it.value.toSet() }
                mutableState.update { it.copy(libraryAnimeTitles = titles, librarySourcesByTitle = sourcesByTitle) }
                applyFilters()
            }
        }
    }

    private fun observePreferences() {
        screenModelScope.launch {
            combine(
                schedulePrefs.showOnlyFavoriteSources().changes(),
                schedulePrefs.favoriteSourceIds().changes(),
                schedulePrefs.showAdultContent().changes(),
                schedulePrefs.titleLanguage().changes(),
                schedulePrefs.autoAddFromPinnedSources().changes(),
                schedulePrefs.uploadDelayRefreshInterval().changes(),
                schedulePrefs.customUploadDelayMinutes().changes(),
            ) { _ -> Unit }.collectLatest {
                if (hasLoaded) applyFilters()
            }
        }
    }

    fun loadSchedule() {
        screenModelScope.launch {
            mutableState.update { it.copy(isLoading = true, error = null) }
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(zone)
            val weekEnd = weekStart.plusDays(7).minusSeconds(1)
            val currentWeekStart = weekStart.toEpochSecond()

            try {
                val autoRefreshEnabled = schedulePrefs.scheduleAutoRefreshEnabled().get()
                val cache = ScheduleDataRefreshWorker.readCache(application)
                val entries: List<AiringScheduleEntry> = if (autoRefreshEnabled &&
                    cache != null &&
                    cache.weekStartEpoch == currentWeekStart &&
                    ScheduleDataRefreshWorker.isCacheFresh(cache, schedulePrefs.scheduleAutoRefreshFrequency().get())
                ) {
                    cache.entries.map { it.toEntry() }
                } else {
                    val includeAdult = schedulePrefs.showAdultContent().get()
                    try {
                        val fetched = repository.getWeeklySchedule(
                            weekStart.toEpochSecond(),
                            weekEnd.toEpochSecond(),
                            includeAdult = includeAdult,
                        )
                        // Persist every successful live fetch, regardless of the auto-refresh
                        // setting, so a subsequent failure (network hiccup, app process death,
                        // etc.) always has fresh data to fall back on for this week.
                        ScheduleDataRefreshWorker.writeCache(application, currentWeekStart, fetched)
                        fetched
                    } catch (fetchError: Exception) {
                        // The live fetch failed. Rather than showing a hard error and leaving the
                        // schedule blank, fall back to whatever we have cached for this exact
                        // week - even if it's stale - so the user still sees a schedule. Only
                        // surface the error if there's truly nothing to show.
                        val fallback = cache?.takeIf { it.weekStartEpoch == currentWeekStart }
                        if (fallback != null) {
                            fallback.entries.map { it.toEntry() }
                        } else {
                            throw fetchError
                        }
                    }
                }

                allEntries = entries
                hasLoaded = true

                val delays = if (schedulePrefs.uploadDelayEnabled().get()) {
                    uploadDelayTracker.getDelays()
                } else {
                    emptyMap()
                }

                rescheduleSeriesAlarms()

                applyFilters(
                    entries = allEntries,
                    delays = delays,
                    weekStart = weekStart.toLocalDate(),
                    weekEnd = weekEnd.toLocalDate(),
                )
            } catch (e: Exception) {
                mutableState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /**
     * Manual override: when the user has picked "Custom" for the upload-delay refresh interval,
     * they've supplied a fixed delay themselves — that takes priority over any auto-learned
     * per-source delay when computing expected upload time / countdown.
     */
    private fun computeManualDelayMinutes(): Long? {
        if (!schedulePrefs.uploadDelayEnabled().get()) return null
        if (schedulePrefs.uploadDelayRefreshInterval().get() != SchedulePreferences.UploadDelayInterval.CUSTOM) {
            return null
        }
        return SchedulePreferences.parseCustomDelayMinutes(schedulePrefs.customUploadDelayMinutes().get())
    }

    /**
     * The actual favourite/pinned source ids that carry *this specific* anime, resolved via the
     * library-anime title match (bounded to anime the user already added — we can't check every
     * source's full catalogue for every scheduled anime without being far too slow, so this only
     * reports "confirmed on a favourite source" for library anime).
     */
    private fun matchedSourcesFor(
        entry: AiringScheduleEntry,
        configuredSources: Set<String>,
        librarySourcesByTitle: Map<String, Set<String>>,
    ): Set<String> {
        val titleCandidates = listOfNotNull(
            entry.titleUserPreferred,
            entry.titleEnglish,
            entry.titleRomaji,
            entry.titleNative,
        ).map { it.trim().lowercase() }
        val candidateSources = titleCandidates.flatMap { librarySourcesByTitle[it].orEmpty() }.toSet()
        return candidateSources.intersect(configuredSources)
    }

    /**
     * The delay-adjusted air time using only that entry's own matched sources (pinned sources
     * take priority over plain favourites, unless a manual override is active), instead of one
     * global delay applied to every unrelated entry.
     */
    private fun priorityDelayFor(
        matchedSources: Set<String>,
        manualDelayMinutes: Long?,
        delays: Map<String, Long>,
        pinnedSources: Set<String>,
        favoriteIds: Set<String>,
    ): Long? {
        manualDelayMinutes?.let { return it }
        if (delays.isEmpty() || matchedSources.isEmpty()) return null
        for (sourceId in pinnedSources) {
            if (sourceId in matchedSources) delays[sourceId]?.let { return it }
        }
        for (sourceId in favoriteIds) {
            if (sourceId in matchedSources) delays[sourceId]?.let { return it }
        }
        return null
    }

    private fun filterEntries(
        entries: List<AiringScheduleEntry>,
        showAdult: Boolean,
        showOnlyFavorites: Boolean,
        configuredSources: Set<String>,
        librarySourcesByTitle: Map<String, Set<String>>,
    ): List<AiringScheduleEntry> = entries.filter { entry ->
        // Re-apply adult-content filter in case the preference changed since last fetch.
        if (!showAdult && entry.isAdult) return@filter false
        // Source filters only apply when the user has configured favourite/pinned sources.
        if (configuredSources.isNotEmpty() && showOnlyFavorites) {
            val matchedSources = matchedSourcesFor(entry, configuredSources, librarySourcesByTitle)
            // showOnlyFavoriteSources: keep entries only when this specific anime is
            // confirmed to be on one of the user's favourite/pinned sources.
            if (matchedSources.isEmpty()) return@filter false
        }
        true
    }

    private fun groupByDelayAdjustedDay(
        entries: List<AiringScheduleEntry>,
        configuredSources: Set<String>,
        librarySourcesByTitle: Map<String, Set<String>>,
        manualDelayMinutes: Long?,
        delays: Map<String, Long>,
        pinnedSources: Set<String>,
        favoriteIds: Set<String>,
        zone: ZoneId,
    ): Map<DayOfWeek, List<AiringScheduleEntry>> = entries.groupBy { entry ->
        val matchedSources = if (configuredSources.isNotEmpty()) {
            matchedSourcesFor(entry, configuredSources, librarySourcesByTitle)
        } else {
            emptySet()
        }
        val priorityDelay = priorityDelayFor(matchedSources, manualDelayMinutes, delays, pinnedSources, favoriteIds)
        val airTime = if (priorityDelay != null) entry.airingAt + (priorityDelay * 60) else entry.airingAt
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(airTime), zone).dayOfWeek
    }

    private fun applyFilters(
        entries: List<AiringScheduleEntry> = allEntries,
        delays: Map<String, Long> = if (schedulePrefs.uploadDelayEnabled().get()) uploadDelayTracker.getDelays() else emptyMap(),
        weekStart: LocalDate? = mutableState.value.weekStartDate,
        weekEnd: LocalDate? = mutableState.value.weekEndDate,
    ) {
        val showOnlyFavorites = schedulePrefs.showOnlyFavoriteSources().get()
        val favoriteIds = schedulePrefs.favoriteSourceIds().get()
        val showAdult = schedulePrefs.showAdultContent().get()
        val titleLang = schedulePrefs.titleLanguage().get()
        val autoAdd = schedulePrefs.autoAddFromPinnedSources().get()
        val pinnedSources = sourcePreferences.pinnedSources().get()
        val librarySourcesByTitle = mutableState.value.librarySourcesByTitle
        val manualDelayMinutes = computeManualDelayMinutes()
        // Source filters should apply for either favourite or pinned sources — a user who
        // only pins sources from Browse (without also marking them "favourite" here) still
        // expects "show only my sources" to work.
        val configuredSources = favoriteIds + pinnedSources

        val filtered = filterEntries(entries, showAdult, showOnlyFavorites, configuredSources, librarySourcesByTitle)
        val grouped = groupByDelayAdjustedDay(
            entries = filtered,
            configuredSources = configuredSources,
            librarySourcesByTitle = librarySourcesByTitle,
            manualDelayMinutes = manualDelayMinutes,
            delays = delays,
            pinnedSources = pinnedSources,
            favoriteIds = favoriteIds,
            zone = ZoneId.systemDefault(),
        )

        mutableState.update {
            it.copy(
                isLoading = false,
                scheduleByDay = grouped,
                weekStartDate = weekStart,
                weekEndDate = weekEnd,
                titleLanguage = titleLang,
                sourceDelays = delays,
                manualDelayMinutes = manualDelayMinutes,
                favoriteSourceIds = favoriteIds,
                pinnedSourceIds = pinnedSources,
                autoAddFromPinnedSources = autoAdd,
                notifyOnceMediaIds = schedulePrefs.notifyOnceMediaIds().get(),
                notifySeriesMediaIds = schedulePrefs.notifySeriesMediaIds().get(),
            )
        }
    }

    /** Determines the current bell state for a given schedule entry. */
    fun notifyStateFor(mediaId: Int): BellNotifyState {
        val state = mutableState.value
        return when {
            mediaId.toString() in state.notifySeriesMediaIds -> BellNotifyState.SERIES
            mediaId.toString() in state.notifyOnceMediaIds -> BellNotifyState.ONCE
            else -> BellNotifyState.NONE
        }
    }

    /** Toggles a single alert for the next upcoming episode of this anime. */
    fun toggleNotifyOnce(entry: AiringScheduleEntry) {
        val key = entry.mediaId.toString()
        val current = schedulePrefs.notifyOnceMediaIds().get()
        val seriesCurrent = schedulePrefs.notifySeriesMediaIds().get()
        if (key in current) {
            schedulePrefs.notifyOnceMediaIds().set(current - key)
            ScheduleNotifications.cancel(application, entry)
        } else {
            // Only persist the "notify" preference if an alarm was actually scheduled —
            // an already-aired entry has nothing to back the bell state, so don't leave a
            // stuck ONCE indicator with no alarm behind it.
            if (ScheduleNotifications.ensureScheduled(application, entry)) {
                schedulePrefs.notifyOnceMediaIds().set(current + key)
                schedulePrefs.notifySeriesMediaIds().set(seriesCurrent - key)
            }
        }
        applyFilters()
    }

    /** Toggles recurring alerts for every future episode of this anime until it finishes airing. */
    fun toggleNotifySeries(entry: AiringScheduleEntry) {
        val key = entry.mediaId.toString()
        val seriesCurrent = schedulePrefs.notifySeriesMediaIds().get()
        val onceCurrent = schedulePrefs.notifyOnceMediaIds().get()
        if (key in seriesCurrent) {
            schedulePrefs.notifySeriesMediaIds().set(seriesCurrent - key)
            ScheduleNotifications.cancelAllForMedia(application, entry.mediaId, allEntries)
        } else {
            schedulePrefs.notifySeriesMediaIds().set(seriesCurrent + key)
            schedulePrefs.notifyOnceMediaIds().set(onceCurrent - key)
            rescheduleSeriesAlarms()
        }
        applyFilters()
    }

    private fun rescheduleSeriesAlarms() {
        val seriesIds = schedulePrefs.notifySeriesMediaIds().get()
        if (seriesIds.isEmpty()) return
        allEntries
            .filter { it.mediaId.toString() in seriesIds && !it.hasAired() }
            .forEach { ScheduleNotifications.ensureScheduled(application, it) }
    }

    fun selectDay(day: DayOfWeek) {
        mutableState.update { it.copy(selectedDay = day) }
    }

    fun clearLearnedDelays() {
        uploadDelayTracker.clearAllDelays()
        applyFilters(delays = emptyMap())
    }

    data class State(
        val isLoading: Boolean = true,
        val scheduleByDay: Map<DayOfWeek, List<AiringScheduleEntry>> = emptyMap(),
        val selectedDay: DayOfWeek = ZonedDateTime.now().dayOfWeek,
        val weekStartDate: LocalDate? = null,
        val weekEndDate: LocalDate? = null,
        val error: String? = null,
        val titleLanguage: SchedulePreferences.TitleLanguage = SchedulePreferences.TitleLanguage.USER_PREFERRED,
        val sourceDelays: Map<String, Long> = emptyMap(),
        val manualDelayMinutes: Long? = null,
        val favoriteSourceIds: Set<String> = emptySet(),
        val pinnedSourceIds: Set<String> = emptySet(),
        val autoAddFromPinnedSources: Boolean = false,
        val notifyOnceMediaIds: Set<String> = emptySet(),
        val notifySeriesMediaIds: Set<String> = emptySet(),
        val libraryAnimeTitles: Set<String> = emptySet(),
        val librarySourcesByTitle: Map<String, Set<String>> = emptyMap(),
    )
}
