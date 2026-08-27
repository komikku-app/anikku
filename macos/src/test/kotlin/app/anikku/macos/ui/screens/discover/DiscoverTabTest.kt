package app.anikku.macos.ui.screens.discover

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscoverTabTest {

    @Test
    fun `seasonalFor maps months to AniList seasons`() {
        // Fixed epochs in each season (noon UTC on the 15th).
        assertEquals("WINTER" to 2026, DiscoverTab.seasonalFor(epochSeconds(2026, 1, 15)))
        assertEquals("SPRING" to 2026, DiscoverTab.seasonalFor(epochSeconds(2026, 5, 15)))
        assertEquals("SUMMER" to 2026, DiscoverTab.seasonalFor(epochSeconds(2026, 8, 15)))
        assertEquals("FALL" to 2026, DiscoverTab.seasonalFor(epochSeconds(2026, 11, 15)))
    }

    private fun epochSeconds(year: Int, month: Int, day: Int): Long {
        val zoned = java.time.ZonedDateTime.of(
            year, month, day, 12, 0, 0, 0, java.time.ZoneId.of("UTC"),
        )
        return zoned.toEpochSecond()
    }
}
