package app.anikku.macos.platform.stats

import app.anikku.macos.platform.data.HistoryRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Per-anime watch summary for the "most watched" ranking.
 */
data class AnimeWatchSummary(
    val animeId: Long,
    val title: String,
    val episodes: Int,
    val watchSeconds: Long,
)

/**
 * Aggregated watch statistics derived from [HistoryRepository] entries.
 *
 * [watchSeconds] uses each entry's final `watchDuration` (the position the user
 * reached when it was last saved) as a proxy for time watched — the app tracks
 * per-episode end positions, not continuous telemetry.
 */
data class WatchStats(
    val totalEpisodes: Int = 0,
    val totalWatchSeconds: Long = 0L,
    val mostWatched: List<AnimeWatchSummary> = emptyList(),
    /** (dayStartEpochMillis, watchSeconds) for the last [WatchStatsCalculator.DAYS] days, oldest first. */
    val dailyWatchSeconds: List<Pair<Long, Long>> = emptyList(),
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
) {
    val totalWatchHours: Double get() = totalWatchSeconds / 3_600.0
}

/**
 * Pure watch-stats computation over history entries — unit-testable, no IO.
 */
object WatchStatsCalculator {

    /** Number of days shown in the activity series. */
    const val DAYS = 14

    private val defaultZone: ZoneId = ZoneId.systemDefault()

    private fun HistoryRepository.HistoryEntry.watchSeconds(): Long {
        val duration = watchDuration.takeIf { it > 0 } ?: lastSecondSeen
        if (duration <= 0) return 0L
        return if (totalSeconds > 0) duration.coerceAtMost(totalSeconds) else duration
    }

    private fun HistoryRepository.HistoryEntry.active(): Boolean =
        (watchDuration > 0 || lastSecondSeen > 0)

    /**
     * Compute stats. [nowMillis] injectable for deterministic tests.
     */
    fun computeStats(
        history: List<HistoryRepository.HistoryEntry>,
        nowMillis: Long = System.currentTimeMillis(),
        days: Int = DAYS,
    ): WatchStats {
        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), defaultZone)

        // Distinct episodes + total watched time.
        val distinctEpisodes = history
            .map { it.animeId to it.episodeId }
            .distinct()
            .size
        val totalWatchSeconds = history.sumOf { it.watchSeconds() }

        // Per-anime summaries.
        val perAnime = history.groupBy { it.animeId }
        val mostWatched = perAnime.map { (animeId, entries) ->
            AnimeWatchSummary(
                animeId = animeId,
                title = entries.maxByOrNull { it.seenAt }?.animeTitle ?: "Unknown",
                episodes = entries.map { it.episodeId }.distinct().size,
                watchSeconds = entries.sumOf { it.watchSeconds() },
            )
        }.sortedWith(compareByDescending<AnimeWatchSummary> { it.episodes }.thenByDescending { it.watchSeconds })
            .take(5)

        // Daily activity series (last `days` days, oldest first).
        val dayStarts = (days - 1 downTo 0).map { offset ->
            today.minusDays(offset.toLong()).atStartOfDay(defaultZone).toEpochSecond() * 1000L
        }
        val dailyWatchSeconds = dayStarts.map { dayStart ->
            val dayEnd = dayStart + ChronoUnit.DAYS.duration.toMillis()
            val seconds = history
                .filter { it.seenAt in dayStart until dayEnd }
                .sumOf { it.watchSeconds() }
            dayStart to seconds
        }

        // Streaks over activity dates.
        val activeDays = history
            .filter { it.active() }
            .map { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.seenAt), defaultZone).toEpochDay() }
            .toSet()
            .sorted()

        return WatchStats(
            totalEpisodes = distinctEpisodes,
            totalWatchSeconds = totalWatchSeconds,
            mostWatched = mostWatched,
            dailyWatchSeconds = dailyWatchSeconds,
            currentStreakDays = currentStreak(activeDays, today.toEpochDay()),
            longestStreakDays = longestStreak(activeDays),
        )
    }

    private fun currentStreak(activeDays: List<Long>, todayEpochDay: Long): Int {
        if (activeDays.isEmpty()) return 0
        // A streak counts from today, or from yesterday if today has no activity yet.
        var cursor = if (activeDays.last() == todayEpochDay) todayEpochDay else todayEpochDay - 1
        var streak = 0
        val set = activeDays.toSet()
        while (cursor in set) {
            streak++
            cursor--
        }
        return streak
    }

    private fun longestStreak(activeDays: List<Long>): Int {
        if (activeDays.isEmpty()) return 0
        var longest = 0
        var run = 1
        for (i in 1 until activeDays.size) {
            if (activeDays[i] == activeDays[i - 1] + 1) {
                run++
            } else {
                if (run > longest) longest = run
                run = 1
            }
        }
        return maxOf(longest, run)
    }
}
