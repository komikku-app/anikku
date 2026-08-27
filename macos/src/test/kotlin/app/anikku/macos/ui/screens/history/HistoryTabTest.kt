package app.anikku.macos.ui.screens.history

import app.anikku.macos.ui.screens.models.HistoryEntryModel
import app.anikku.macos.ui.screens.models.MockData
import org.junit.Test

/**
 * Unit tests for [HistoryTab] and mock history data.
 *
 * Verifies history model construction, mock data integrity,
 * and timestamp validity.
 */
class HistoryTabTest {

    @Test
    fun `mock data has correct number of entries`() {
        assert(MockData.sampleHistory.size == 5) {
            "Expected 5 history entries, got ${MockData.sampleHistory.size}"
        }
    }

    @Test
    fun `all mock history entries have valid seen timestamps`() {
        val allHaveTimestamps = MockData.sampleHistory.all { it.seenAt > 0 }
        assert(allHaveTimestamps) { "All history entries should have seenAt > 0" }
    }

    @Test
    fun `history entries have unique IDs`() {
        val ids = MockData.sampleHistory.map { it.id }
        assert(ids.distinct().size == ids.size) { "All history entry IDs should be unique" }
    }

    @Test
    fun `history entries sorted chronologically`() {
        val timestamps = MockData.sampleHistory.map { it.seenAt }
        for (i in 0 until timestamps.size - 1) {
            assert(timestamps[i] > timestamps[i + 1]) {
                "History should be sorted by seenAt descending"
            }
        }
    }

    @Test
    fun `all history anime titles are non-blank`() {
        val empty = MockData.sampleHistory.filter { it.animeTitle.isBlank() }
        assert(empty.isEmpty()) { "All history entries should have anime titles" }
    }

    @Test
    fun `all history entries have valid episode numbers`() {
        val invalid = MockData.sampleHistory.filter { it.episodeNumber <= 0 }
        assert(invalid.isEmpty()) { "All history entries should have valid episode numbers" }
    }

    @Test
    fun `HistoryEntryModel can be constructed with defaults`() {
        val entry = HistoryEntryModel(
            id = 1L,
            animeId = 1L,
            animeTitle = "Test Anime",
            episodeId = 100L,
            episodeNumber = 1.0,
        )
        assert(entry.seenAt == 0L)
        assert(entry.watchDuration == 0L)
    }

    @Test
    fun `sample history has diverse anime`() {
        val animeIds = MockData.sampleHistory.map { it.animeId }.distinct()
        assert(animeIds.size >= 3) { "Should have at least 3 different anime in history" }
    }

    @Test
    fun `One Piece has the highest episode count`() {
        val onePiece = MockData.sampleHistory.find { it.animeTitle == "One Piece" }
        assert(onePiece != null)
        assert(onePiece!!.episodeNumber == 1072.0)
    }
}
