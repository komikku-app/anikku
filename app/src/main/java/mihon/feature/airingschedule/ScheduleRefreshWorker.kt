package mihon.feature.airingschedule

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.domain.source.service.SourcePreferences
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Periodic WorkManager job that:
 *  1. Refreshes the airing schedule cache weekly.
 *  2. For episodes that have aired and match an anime already in the user's library from a
 *     favorite/pinned source, fetches that specific source's real episode list and compares its
 *     actual upload timestamp against AniList's official air time to learn a genuine per-source
 *     upload delay. Scoped to library anime only (not every source's full catalogue) so this
 *     stays fast — it never crawls every anime on every source.
 */
class ScheduleRefreshWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withIOContext {
        try {
            val schedulePrefs = Injekt.get<SchedulePreferences>()
            if (!schedulePrefs.uploadDelayEnabled().get()) return@withIOContext Result.success()

            val nowEpoch = System.currentTimeMillis() / 1000L
            val trackedSourceIds = schedulePrefs.favoriteSourceIds().get() +
                Injekt.get<SourcePreferences>().pinnedSources().get()
            if (trackedSourceIds.isEmpty()) return@withIOContext Result.success()

            val airedEntries = fetchAiredEntries(schedulePrefs, nowEpoch)
            if (airedEntries.isEmpty()) {
                schedulePrefs.lastDelayCheckTime().set(nowEpoch)
                return@withIOContext Result.success()
            }

            // Bound the work to anime already in the user's library that live on a
            // favorite/pinned source — this is the only set where we can resolve a concrete
            // (anime, source) pair to actually query, so we never scan every source's full
            // catalogue for every scheduled anime.
            val libraryAnime = Injekt.get<GetLibraryAnime>().await()
                .map { it.anime }
                .filter { it.source.toString() in trackedSourceIds }
            if (libraryAnime.isEmpty()) {
                schedulePrefs.lastDelayCheckTime().set(nowEpoch)
                return@withIOContext Result.success()
            }

            val observations = collectDelayObservations(airedEntries, libraryAnime)
            Injekt.get<UploadDelayTracker>().recordObservations(observations)

            schedulePrefs.lastDelayCheckTime().set(nowEpoch)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /** Fetches this week's schedule and returns only the entries that aired since the last check. */
    private suspend fun fetchAiredEntries(
        schedulePrefs: SchedulePreferences,
        nowEpoch: Long,
    ): List<AiringScheduleEntry> {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate().atStartOfDay(zone)
        val weekEnd = weekStart.plusDays(7).minusSeconds(1)

        val entries = AiringScheduleRepository().getWeeklySchedule(
            weekStart.toEpochSecond(),
            weekEnd.toEpochSecond(),
            includeAdult = schedulePrefs.showAdultContent().get(),
        )

        // Only consider episodes that aired within the last worker interval, so the same
        // episode isn't re-checked (and the running average skewed) on every subsequent run.
        val lastCheckTime = schedulePrefs.lastDelayCheckTime().get()
        val windowStart = if (lastCheckTime > 0L) lastCheckTime else nowEpoch - 24 * 3600
        return entries.filter { it.airingAt in windowStart..nowEpoch }
    }

    /**
     * Queries the real source episode list for each aired entry's matching library anime and
     * returns the learned (sourceId, delayMinutes) observations. Avoids re-fetching the same
     * source within a single run and caps total source calls so large libraries can't make the
     * background worker exceed its WorkManager window. This is a soft cap; the per-source
     * observations are still written so the learning keeps improving over subsequent runs.
     */
    private suspend fun collectDelayObservations(
        airedEntries: List<AiringScheduleEntry>,
        libraryAnime: List<Anime>,
    ): List<Pair<String, Long>> {
        val sourceManager = Injekt.get<SourceManager>()
        val observations = mutableListOf<Pair<String, Long>>()
        val alreadyQueriedSources = mutableSetOf<Long>()
        var sourceCallsThisRun = 0

        for (entry in airedEntries) {
            if (sourceCallsThisRun >= MAX_SOURCE_CALLS_PER_RUN) break
            val matches = matchingLibraryAnime(entry, libraryAnime)
            for (anime in matches) {
                if (!alreadyQueriedSources.add(anime.source)) continue
                if (sourceCallsThisRun >= MAX_SOURCE_CALLS_PER_RUN) break
                sourceCallsThisRun++

                observeUploadDelay(sourceManager, anime, entry)?.let { delayMinutes ->
                    observations.add(anime.source.toString() to delayMinutes)
                }
            }
        }
        return observations
    }

    private fun matchingLibraryAnime(
        entry: AiringScheduleEntry,
        libraryAnime: List<Anime>,
    ): List<Anime> {
        val titleCandidates = listOfNotNull(
            entry.titleUserPreferred,
            entry.titleEnglish,
            entry.titleRomaji,
            entry.titleNative,
        ).map { it.trim().lowercase() }.toSet()
        return libraryAnime.filter { it.title.trim().lowercase() in titleCandidates }
    }

    /** Fetches [anime]'s real episode list from its source and returns a plausible delay in minutes, if any. */
    private suspend fun observeUploadDelay(
        sourceManager: SourceManager,
        anime: Anime,
        entry: AiringScheduleEntry,
    ): Long? {
        val source = sourceManager.get(anime.source) ?: return null
        val episodes = runCatching { source.getEpisodeList(anime.toSAnime()) }.getOrNull() ?: return null
        val matchingEpisode = episodes.firstOrNull { abs(it.episode_number - entry.episode) < 0.01f } ?: return null
        val uploadDate = matchingEpisode.date_upload
        if (uploadDate <= 0L) return null

        val delayMinutes = (uploadDate / 1000L - entry.airingAt) / 60L
        // Discard implausible outliers (source's clock/metadata far off, or the episode
        // predates this air date entirely) rather than let them corrupt the running average.
        return delayMinutes.takeIf { it in -60L..(24 * 60) }
    }

    companion object {
        private const val WORK_NAME = "ScheduleRefreshWorker"
        private const val MAX_SOURCE_CALLS_PER_RUN = 12

        fun schedule(context: Context, interval: SchedulePreferences.UploadDelayInterval) {
            val wm = WorkManager.getInstance(context)
            // NEVER: user wants no background refresh at all. CUSTOM: the user supplies a fixed
            // manual delay instead of an auto-learned one, so there is nothing for this worker
            // to learn — cancel any existing periodic job rather than scheduling one.
            if (
                interval == SchedulePreferences.UploadDelayInterval.NEVER ||
                interval == SchedulePreferences.UploadDelayInterval.CUSTOM
            ) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val minutes = when (interval) {
                SchedulePreferences.UploadDelayInterval.THIRTY_MIN -> 30L
                SchedulePreferences.UploadDelayInterval.ONE_HOUR -> 60L
                SchedulePreferences.UploadDelayInterval.TWO_HOURS -> 120L
                SchedulePreferences.UploadDelayInterval.SIX_HOURS -> 360L
                SchedulePreferences.UploadDelayInterval.TWELVE_HOURS -> 720L
                SchedulePreferences.UploadDelayInterval.NEVER,
                SchedulePreferences.UploadDelayInterval.CUSTOM,
                -> return
            }
            val request = PeriodicWorkRequestBuilder<ScheduleRefreshWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        // Explicitly does NOT require battery-not-low / device-idle, so this
                        // still runs on a low battery or when the device is actively in use —
                        // it is only gated on network availability. WorkManager may still defer
                        // execution slightly under Doze regardless of the app's own
                        // "unrestricted background battery usage" setting; that ceiling can only
                        // be fully removed by running as a foreground service, which isn't
                        // appropriate for a periodic background sync like this.
                        .build(),
                )
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
