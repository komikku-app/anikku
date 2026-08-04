package app.anikku.macos.platform.stats

import app.anikku.macos.platform.data.HistoryRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WatchStatsCalculatorTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun dayStartEpochMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun entry(
        animeId: Long,
        episodeId: Long,
        title: String = "Anime $animeId",
        seenAt: Long = 0L,
        watchDuration: Long = 600L,
        totalSeconds: Long = 1200L,
        lastSecondSeen: Long = 600L,
    ) = HistoryRepository.HistoryEntry(
        animeId = animeId,
        episodeId = episodeId,
        animeTitle = title,
        seenAt = seenAt,
        watchDuration = watchDuration,
        totalSeconds = totalSeconds,
        lastSecondSeen = lastSecondSeen,
    )

    @Test
    fun `totals count distinct episodes and sum watch seconds`() {
        val now = dayStartEpochMillis(20_000)
        val stats = WatchStatsCalculator.computeStats(
            history = listOf(
                entry(1, 101, watchDuration = 600),
                entry(1, 102, watchDuration = 600),
                entry(1, 102, watchDuration = 900), // same episode re-watched → dedupe
                entry(2, 201, watchDuration = 300),
            ),
            nowMillis = now,
        )

        assertEquals(3, stats.totalEpisodes)
        // 600 + 600 + 900 + 300 = 2400 (history is deduped upstream, so the
        // synthetic duplicate episode still contributes its row's duration).
        assertEquals(2400L, stats.totalWatchSeconds)
    }

    @Test
    fun `watch seconds are capped at episode total`() {
        val stats = WatchStatsCalculator.computeStats(
            history = listOf(entry(1, 101, watchDuration = 5000, totalSeconds = 1200)),
            nowMillis = dayStartEpochMillis(20_000),
        )

        assertEquals(1200L, stats.totalWatchSeconds)
    }

    @Test
    fun `most watched ranks by episode count then time`() {
        val stats = WatchStatsCalculator.computeStats(
            history = listOf(
                entry(1, 101, title = "Three eps", watchDuration = 100),
                entry(1, 102, title = "Three eps", watchDuration = 100),
                entry(1, 103, title = "Three eps", watchDuration = 100),
                entry(2, 201, title = "One long ep", watchDuration = 1000),
            ),
            nowMillis = dayStartEpochMillis(20_000),
        )

        assertEquals(listOf("Three eps", "One long ep"), stats.mostWatched.map { it.title })
        assertEquals(3, stats.mostWatched.first().episodes)
    }

    @Test
    fun `daily series covers the last 14 days with per-day sums`() {
        val today = LocalDate.ofEpochDay(20_000)
        val now = today.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L // 10:00 today
        val yesterday = dayStartEpochMillis(19_999)
        val threeDaysAgo = dayStartEpochMillis(19_997)

        val stats = WatchStatsCalculator.computeStats(
            history = listOf(
                entry(1, 101, seenAt = yesterday + 1_000, watchDuration = 600),
                entry(2, 201, seenAt = threeDaysAgo + 1_000, watchDuration = 300),
            ),
            nowMillis = now,
        )

        assertEquals(WatchStatsCalculator.DAYS, stats.dailyWatchSeconds.size)
        // Oldest first: the series covers today-13 … today.
        assertEquals(dayStartEpochMillis(today.toEpochDay() - (WatchStatsCalculator.DAYS - 1)), stats.dailyWatchSeconds.first().first)
        assertEquals(300L, stats.dailyWatchSeconds.first { it.first == threeDaysAgo }.second)
        assertEquals(600L, stats.dailyWatchSeconds.first { it.first == yesterday }.second)
        assertEquals(0L, stats.dailyWatchSeconds.last().second) // today not yet active
    }

    @Test
    fun `current streak counts consecutive active days ending today or yesterday`() {
        val today = 20_000L
        val now = dayStartEpochMillis(today) + 3_600_000L

        // Active today, yesterday, and day before → streak 3.
        val streak3 = WatchStatsCalculator.computeStats(
            history = listOf(
                entry(1, 101, seenAt = dayStartEpochMillis(today) + 1_000),
                entry(1, 102, seenAt = dayStartEpochMillis(today - 1) + 1_000),
                entry(1, 103, seenAt = dayStartEpochMillis(today - 2) + 1_000),
            ),
            nowMillis = now,
        )
        assertEquals(3, streak3.currentStreakDays)

        // No activity today but active yesterday → streak still counts from yesterday.
        val streakFromYesterday = WatchStatsCalculator.computeStats(
            history = listOf(
                entry(1, 102, seenAt = dayStartEpochMillis(today - 1) + 1_000),
                entry(1, 103, seenAt = dayStartEpochMillis(today - 2) + 1_000),
            ),
            nowMillis = now,
        )
        assertEquals(2, streakFromYesterday.currentStreakDays)

        // Nothing active at all.
        val none = WatchStatsCalculator.computeStats(emptyList(), nowMillis = now)
        assertEquals(0, none.currentStreakDays)
    }

    @Test
    fun `longest streak finds the longest run even with a gap`() {
        val today = 20_000L
        val now = dayStartEpochMillis(today)

        val stats = WatchStatsCalculator.computeStats(
            history = listOf(
                // Run of 4 (days 10-13)
                entry(1, 11, seenAt = dayStartEpochMillis(10)),
                entry(1, 12, seenAt = dayStartEpochMillis(11)),
                entry(1, 13, seenAt = dayStartEpochMillis(12)),
                entry(1, 14, seenAt = dayStartEpochMillis(13)),
                // Gap, then run of 2 (days 16-17)
                entry(2, 21, seenAt = dayStartEpochMillis(16)),
                entry(2, 22, seenAt = dayStartEpochMillis(17)),
            ),
            nowMillis = now,
        )

        assertEquals(4, stats.longestStreakDays)
        // Current streak only counts consecutive days ending today/yesterday → 0 here.
        assertEquals(0, stats.currentStreakDays)
    }

    @Test
    fun `empty history yields zero stats`() {
        val stats = WatchStatsCalculator.computeStats(emptyList(), nowMillis = dayStartEpochMillis(20_000))
        assertEquals(0, stats.totalEpisodes)
        assertEquals(0L, stats.totalWatchSeconds)
        assertTrue(stats.mostWatched.isEmpty())
        assertEquals(0, stats.currentStreakDays)
        assertEquals(0, stats.longestStreakDays)
    }
}
