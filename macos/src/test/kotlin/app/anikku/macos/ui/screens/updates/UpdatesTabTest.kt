package app.anikku.macos.ui.screens.updates

import app.anikku.macos.ui.screens.models.MockData
import app.anikku.macos.ui.screens.models.UpdateModel
import org.junit.Test

/**
 * Unit tests for [UpdatesTab] and mock update data.
 *
 * Verifies update model construction, mock data integrity, and
 * seen/unseen distribution.
 */
class UpdatesTabTest {

    @Test
    fun `mock data has correct number of updates`() {
        assert(MockData.sampleUpdates.size == 6) {
            "Expected 6 sample updates, got ${MockData.sampleUpdates.size}"
        }
    }

    @Test
    fun `mock updates have unique episode IDs`() {
        val ids = MockData.sampleUpdates.map { it.episodeId }
        assert(ids.distinct().size == ids.size) { "All episode IDs should be unique" }
    }

    @Test
    fun `mock updates mix seen and unseen`() {
        val unseen = MockData.sampleUpdates.filter { !it.seen }
        val seen = MockData.sampleUpdates.filter { it.seen }

        assert(unseen.isNotEmpty()) { "Should have unseen updates" }
        assert(seen.isNotEmpty()) { "Should have seen updates" }
        assert(unseen.size + seen.size == MockData.sampleUpdates.size)
    }

    @Test
    fun `all update anime titles are non-blank`() {
        val empty = MockData.sampleUpdates.filter { it.animeTitle.isBlank() }
        assert(empty.isEmpty()) { "All update entries should have anime titles" }
    }

    @Test
    fun `all update episode names are non-blank`() {
        val empty = MockData.sampleUpdates.filter { it.episodeName.isBlank() }
        assert(empty.isEmpty()) { "All update entries should have episode names" }
    }

    @Test
    fun `UpdateModel can be constructed with defaults`() {
        val update = UpdateModel(
            animeId = 1L,
            animeTitle = "Test Anime",
            episodeId = 100L,
            episodeName = "Episode 1 - Test",
        )
        assert(update.seen == false)
        assert(update.scanlator == null)
        assert(update.dateFetch == 0L)
    }

    @Test
    fun `unseen updates count is 4`() {
        val unseen = MockData.sampleUpdates.filter { !it.seen }
        assert(unseen.size == 4) { "Expected 4 unseen updates, got ${unseen.size}" }
    }

    @Test
    fun `sample updates contain expected anime`() {
        val titles = MockData.sampleUpdates.map { it.animeTitle }
        assert("Jujutsu Kaisen" in titles)
        assert("One Piece" in titles)
        assert("Attack on Titan" in titles)
    }
}
