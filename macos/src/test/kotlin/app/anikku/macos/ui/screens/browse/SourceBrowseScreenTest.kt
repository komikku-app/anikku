package app.anikku.macos.ui.screens.browse

import app.anikku.macos.ui.screens.models.toAnimeModel
import eu.kanade.tachiyomi.animesource.model.SAnime
import org.junit.Test

class SourceBrowseScreenTest {

    @Test
    fun `SAnime toAnimeModel conversion preserves fields`() {
        val sAnime = SAnime.create().apply {
            url = "https://example.com/anime/1"
            title = "Test Anime"
            author = "Test Author"
            artist = "Test Artist"
            description = "A test anime"
            genre = "Action, Adventure"
            status = 1
            thumbnail_url = "https://example.com/thumb.jpg"
            initialized = true
        }

        val model = sAnime.toAnimeModel(sourceId = 42L)

        assert(model.title == "Test Anime") { "Expected 'Test Anime', got '${model.title}'" }
        assert(model.author == "Test Author") { "Expected 'Test Author', got '${model.author}'" }
        assert(model.artist == "Test Artist") { "Expected 'Test Artist', got '${model.artist}'" }
        assert(model.description == "A test anime") { "Expected description, got '${model.description}'" }
        assert(model.genre == listOf("Action", "Adventure")) { "Expected genres, got ${model.genre}" }
        assert(model.status == 1) { "Expected status 1, got ${model.status}" }
        assert(model.thumbnailUrl == "https://example.com/thumb.jpg") { "Wrong thumbnail" }
        assert(model.url == "https://example.com/anime/1") { "Wrong URL" }
        assert(model.source == 42L) { "Expected source 42, got ${model.source}" }
        assert(!model.favorite) { "Should not be favorite by default" }
    }

    @Test
    fun `SAnime toAnimeModel handles null fields gracefully`() {
        val sAnime = SAnime.create().apply {
            url = "/anime/1"
            title = "Minimal Anime"
        }

        val model = sAnime.toAnimeModel()

        assert(model.title == "Minimal Anime") { "Wrong title" }
        assert(model.author == null) { "Expected null author" }
        assert(model.genre == null) { "Expected null genre for blank genre string" }
        assert(model.thumbnailUrl == null) { "Expected null thumbnail" }
    }

    @Test
    fun `toAnimeModel generates stable ID from URL hash`() {
        val sAnime1 = SAnime.create().apply { url = "/anime/1"; title = "Anime 1" }
        val sAnime2 = SAnime.create().apply { url = "/anime/2"; title = "Anime 2" }

        val model1a = sAnime1.toAnimeModel()
        val model1b = sAnime1.toAnimeModel()
        val model2 = sAnime2.toAnimeModel()

        // Same URL → same ID (stable)
        assert(model1a.id == model1b.id) { "Same URL should produce same ID" }
        // Different URL → different ID
        assert(model1a.id != model2.id) { "Different URLs should produce different IDs" }
    }
}
