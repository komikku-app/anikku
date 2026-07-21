package mihon.feature.airingschedule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager job that automatically refreshes the weekly airing schedule
 * from AniList and caches it locally. Frequency is configurable by the user
 * (1–7 days). Disabled by default.
 */
class ScheduleDataRefreshWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val schedulePrefs = Injekt.get<SchedulePreferences>()

            if (!schedulePrefs.scheduleAutoRefreshEnabled().get()) {
                return@withContext Result.success()
            }

            val repository = AiringScheduleRepository()
            val includeAdult = schedulePrefs.showAdultContent().get()

            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate().atStartOfDay(zone)
            val weekEnd = weekStart.plusDays(7).minusSeconds(1)

            val entries = repository.getWeeklySchedule(
                weekStart.toEpochSecond(),
                weekEnd.toEpochSecond(),
                includeAdult = includeAdult,
            )

            writeCache(context, weekStart.toEpochSecond(), entries)

            // Re-arm alarms for any anime the user subscribed to "notify every episode" for.
            // Series alarms are only ever scheduled from whichever week is currently loaded in
            // the UI, so without this, a series subscription silently stops alerting once the
            // originally-loaded week's episodes are exhausted. This periodic refresh (which runs
            // independently of the Schedule tab being open) re-schedules alarms for each newly
            // fetched week's unaired episodes belonging to a subscribed series.
            val seriesIds = schedulePrefs.notifySeriesMediaIds().get()
            if (seriesIds.isNotEmpty()) {
                entries
                    .filter { it.mediaId.toString() in seriesIds && !it.hasAired() }
                    .forEach { mihon.feature.airingschedule.notification.ScheduleNotifications.ensureScheduled(context, it) }
            }

            schedulePrefs.scheduleLastAutoRefresh().set(System.currentTimeMillis())
            Result.success()
        } catch (_: Exception) {
            // WorkManager will retry this with the exponential backoff policy configured in
            // `schedule()` below, so a transient failure here (rate limiting, no network, etc.)
            // self-heals without any user action.
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "ScheduleDataRefreshWorker"
        private val cacheJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        private fun Context.cacheFile() = java.io.File(filesDir, "schedule_cache.json")

        fun schedule(context: Context, frequency: SchedulePreferences.AutoRefreshFrequency) {
            val days = frequency.toDays()
            val request = PeriodicWorkRequestBuilder<ScheduleDataRefreshWorker>(days, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                // If a refresh attempt fails (e.g. transient network/rate-limit issue), WorkManager
                // retries with growing delays instead of hammering the API or giving up until the
                // next full period, so the "one week ends, next week fails to load" scenario
                // self-heals well before the week is over.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        suspend fun readCache(context: Context): ScheduleCacheData? = withContext(Dispatchers.IO) {
            try {
                val file = context.cacheFile()
                if (!file.exists()) return@withContext null
                cacheJson.decodeFromString(ScheduleCacheData.serializer(), file.readText())
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Persists a successfully fetched schedule to disk so it survives app process death and
         * can be used as a fallback if a later refresh attempt fails. Safe to call regardless of
         * whether auto-refresh is enabled.
         */
        suspend fun writeCache(context: Context, weekStartEpoch: Long, entries: List<AiringScheduleEntry>) =
            withContext(Dispatchers.IO) {
                try {
                    val cacheData = ScheduleCacheData(
                        fetchedAt = System.currentTimeMillis(),
                        weekStartEpoch = weekStartEpoch,
                        entries = entries.map { it.toCached() },
                    )
                    val file = context.cacheFile()
                    file.parentFile?.mkdirs()
                    file.writeText(cacheJson.encodeToString(ScheduleCacheData.serializer(), cacheData))
                } catch (_: Exception) {
                    // Best-effort cache write; failing to persist shouldn't break the current load.
                }
            }

        fun isCacheFresh(cacheData: ScheduleCacheData, frequency: SchedulePreferences.AutoRefreshFrequency): Boolean {
            val maxAge = frequency.toDays() * 24L * 60L * 60L * 1000L
            val age = System.currentTimeMillis() - cacheData.fetchedAt
            return age < maxAge
        }

        private fun SchedulePreferences.AutoRefreshFrequency.toDays(): Long = when (this) {
            SchedulePreferences.AutoRefreshFrequency.EVERY_1_DAY -> 1L
            SchedulePreferences.AutoRefreshFrequency.EVERY_2_DAYS -> 2L
            SchedulePreferences.AutoRefreshFrequency.EVERY_3_DAYS -> 3L
            SchedulePreferences.AutoRefreshFrequency.EVERY_4_DAYS -> 4L
            SchedulePreferences.AutoRefreshFrequency.EVERY_5_DAYS -> 5L
            SchedulePreferences.AutoRefreshFrequency.EVERY_6_DAYS -> 6L
            SchedulePreferences.AutoRefreshFrequency.EVERY_7_DAYS -> 7L
        }
    }
}

private fun AiringScheduleEntry.toCached() = CachedAiringEntry(
    scheduleId = scheduleId,
    airingAt = airingAt,
    episode = episode,
    mediaId = mediaId,
    titleUserPreferred = titleUserPreferred,
    titleEnglish = titleEnglish,
    titleRomaji = titleRomaji,
    titleNative = titleNative,
    coverImageUrl = coverImageUrl,
    totalEpisodes = totalEpisodes,
    averageScore = averageScore,
    format = format,
    status = status,
    isAdult = isAdult,
    genres = genres,
)

fun CachedAiringEntry.toEntry() = AiringScheduleEntry(
    scheduleId = scheduleId,
    airingAt = airingAt,
    episode = episode,
    mediaId = mediaId,
    titleUserPreferred = titleUserPreferred,
    titleEnglish = titleEnglish,
    titleRomaji = titleRomaji,
    titleNative = titleNative,
    coverImageUrl = coverImageUrl,
    totalEpisodes = totalEpisodes,
    averageScore = averageScore,
    format = format,
    status = status,
    isAdult = isAdult,
    genres = genres,
)

@Serializable
data class ScheduleCacheData(
    val fetchedAt: Long,
    val weekStartEpoch: Long,
    val entries: List<CachedAiringEntry>,
)

@Serializable
data class CachedAiringEntry(
    val scheduleId: Int,
    val airingAt: Long,
    val episode: Int,
    val mediaId: Int,
    val titleUserPreferred: String,
    val titleEnglish: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    val coverImageUrl: String,
    val totalEpisodes: Int? = null,
    val averageScore: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val isAdult: Boolean = false,
    val genres: List<String> = emptyList(),
)
