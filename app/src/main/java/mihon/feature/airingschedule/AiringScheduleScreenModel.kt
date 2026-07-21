package mihon.feature.airingschedule

import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
                schedulePrefs.showAdultContent().changes(),
                schedulePrefs.titleLanguage().changes(),
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
                        ScheduleDataRefreshWorker.writeCache(application, currentWeekStart, fetched)
                        fetched
                    } catch (fetchError: Exception) {
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

    private fun computeManualDelayMinutes(): Long? {
        if (!schedulePrefs.uploadDelayEnabled().get()) return null
        if (schedulePrefs.uploadDelayRefreshInterval().get() != SchedulePreferences.UploadDelayInterval.CUSTOM) {
            return null
        }
        return SchedulePreferences.parseCustomDelayMinutes(schedulePrefs.customUploadDelayMinutes().get())
    }

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

    private fun priorityDelayFor(
        matchedSources: Set<String>,
        manualDelayMinutes: Long?,
        delays: Map<String, Long>,
        pinnedSources: Set<String>,
    ): Long? {
        manualDelayMinutes?.let { return it }
        if (delays.isEmpty() || matchedSources.isEmpty()) return null
        for (sourceId in pinnedSources) {
            if (sourceId in matchedSources) delays[sourceId]?.let { return it }
        }
        return null
    }

    private fun groupByDelayAdjustedDay(
        entries: List<AiringScheduleEntry>,
        configuredSources: Set<String>,
        librarySourcesByTitle: Map<String, Set<String>>,
        manualDelayMinutes: Long?,
        delays: Map<String, Long>,
        pinnedSources: Set<String>,
        zone: ZoneId,
    ): Map<DayOfWeek, List<AiringScheduleEntry>> = entries.groupBy { entry ->
        val matchedSources = if (configuredSources.isNotEmpty()) {
            matchedSourcesFor(entry, configuredSources, librarySourcesByTitle)
        } else {
            emptySet()
        }
        val priorityDelay = priorityDelayFor(matchedSources, manualDelayMinutes, delays, pinnedSources)
        val airTime = if (priorityDelay != null) entry.airingAt + (priorityDelay * 60) else entry.airingAt
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(airTime), zone).dayOfWeek
    }

    private fun applyFilters(
        entries: List<AiringScheduleEntry> = allEntries,
        delays: Map<String, Long> = if (schedulePrefs.uploadDelayEnabled().get()) uploadDelayTracker.getDelays() else emptyMap(),
        weekStart: LocalDate? = mutableState.value.weekStartDate,
        weekEnd: LocalDate? = mutableState.value.weekEndDate,
    ) {
        val showAdult = schedulePrefs.showAdultContent().get()
        val titleLang = schedulePrefs.titleLanguage().get()
        val pinnedSources = sourcePreferences.pinnedSources().get()
        val librarySourcesByTitle = mutableState.value.librarySourcesByTitle
        val manualDelayMinutes = computeManualDelayMinutes()

        val filtered = entries.filter { !it.isAdult || showAdult }
        val grouped = groupByDelayAdjustedDay(
            entries = filtered,
            configuredSources = pinnedSources,
            librarySourcesByTitle = librarySourcesByTitle,
            manualDelayMinutes = manualDelayMinutes,
            delays = delays,
            pinnedSources = pinnedSources,
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
                pinnedSourceIds = pinnedSources,
                notifyOnceMediaIds = schedulePrefs.notifyOnceMediaIds().get(),
                notifySeriesMediaIds = schedulePrefs.notifySeriesMediaIds().get(),
            )
        }
    }

    fun toggleNotifyOnce(entry: AiringScheduleEntry) {
        val key = entry.mediaId.toString()
        val current = schedulePrefs.notifyOnceMediaIds().get()
        val seriesCurrent = schedulePrefs.notifySeriesMediaIds().get()
        if (key in current) {
            schedulePrefs.notifyOnceMediaIds().set(current - key)
            ScheduleNotifications.cancel(application, entry)
        } else {
            if (ScheduleNotifications.ensureScheduled(application, entry)) {
                schedulePrefs.notifyOnceMediaIds().set(current + key)
                schedulePrefs.notifySeriesMediaIds().set(seriesCurrent - key)
            }
        }
        applyFilters()
    }

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
        val pinnedSourceIds: Set<String> = emptySet(),
        val notifyOnceMediaIds: Set<String> = emptySet(),
        val notifySeriesMediaIds: Set<String> = emptySet(),
        val libraryAnimeTitles: Set<String> = emptySet(),
        val librarySourcesByTitle: Map<String, Set<String>> = emptyMap(),
    )
}
