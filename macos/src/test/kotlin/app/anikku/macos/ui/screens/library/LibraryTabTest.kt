package app.anikku.macos.ui.screens.library

import app.anikku.macos.ui.screens.models.MockData
import org.junit.Test

/**
 * Unit tests for [LibraryTab].
 *
 * Verifies enum structure, mock data integrity, and tab configuration.
 * Compose UI rendering tests are excluded because Voyager's Navigator
 * composable is not available in the Compose Desktop test harness.
 */
class LibraryTabTest {

    @Test
    fun `DisplayMode enum has two values`() {
        val values = LibraryTab.DisplayMode.entries
        assert(values.size == 2) { "DisplayMode should have Grid and List, got ${values.size}" }
        assert(values.contains(LibraryTab.DisplayMode.Grid))
        assert(values.contains(LibraryTab.DisplayMode.List))
    }

    @Test
    fun `mock data has 12 sample anime`() {
        assert(MockData.sampleAnime.size == 12) {
            "Expected 12 sample anime, got ${MockData.sampleAnime.size}"
        }
    }

    @Test
    fun `all mock anime have non-blank titles`() {
        val emptyTitles = MockData.sampleAnime.filter { it.title.isBlank() }
        assert(emptyTitles.isEmpty()) { "All anime should have non-blank titles" }
    }

    @Test
    fun `all mock anime have valid IDs`() {
        val ids = MockData.sampleAnime.map { it.id }
        assert(ids.distinct().size == ids.size) { "All anime IDs should be unique" }
    }

    @Test
    fun `mock anime mix status types`() {
        val statuses = MockData.sampleAnime.map { it.status }.distinct()
        assert(statuses.contains(1)) { "Should have Ongoing (status=1) anime" }
        assert(statuses.contains(2)) { "Should have Completed (status=2) anime" }
    }

    @Test
    fun `first anime is Attack on Titan`() {
        val first = MockData.sampleAnime.first()
        assert(first.title == "Attack on Titan") { "First anime should be 'Attack on Titan'" }
        assert(first.id == 1L)
    }

    @Test
    fun `mock anime have descriptions`() {
        val withoutDescription = MockData.sampleAnime.filter { it.description.isNullOrBlank() }
        assert(withoutDescription.isEmpty()) { "All anime should have descriptions" }
    }

    @Test
    fun `mock anime have genres`() {
        val withoutGenres = MockData.sampleAnime.filter { it.genre.isNullOrEmpty() }
        assert(withoutGenres.isEmpty()) { "All anime should have genres" }
    }
}
