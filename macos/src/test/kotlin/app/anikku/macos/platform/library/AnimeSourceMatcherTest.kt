package app.anikku.macos.platform.library

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import rx.Observable

/**
 * Minimal in-memory CatalogueSource for tests. [animes] are (url, title, thumbnail)
 * triples returned verbatim from getSearchAnime.
 */
internal class FakeCatalogueSource(
    override val id: Long,
    override val name: String,
    private val animes: List<Triple<String, String, String?>> = emptyList(),
    private val throwOnSearch: Throwable? = null,
) : CatalogueSource {
    override val lang: String = "en"
    override val supportsLatest: Boolean = false

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
    override suspend fun getVideoList(episode: SEpisode): List<Video> = emptyList()
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        throwOnSearch?.let { throw it }
        return AnimesPage(
            animes = animes.map { (url, title, thumb) ->
                SAnime.create().apply {
                    this.url = url
                    this.title = title
                    thumbnail_url = thumb
                }
            },
            hasNextPage = false,
        )
    }

    override suspend fun getPopularAnime(page: Int): AnimesPage = AnimesPage(emptyList(), false)
    override suspend fun getLatestUpdates(page: Int): AnimesPage = AnimesPage(emptyList(), false)
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = Observable.empty()
    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> = Observable.empty()
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = Observable.empty()
}

class AnimeSourceMatcherTest {

    // ----------------------------------------------------------------------
    // Pure title normalization + scoring
    // ----------------------------------------------------------------------

    @Test
    fun `normalizeTitle strips trailing parentheticals`() {
        assertEquals("sololeveling", AnimeSourceMatcher.normalizeTitle("Solo Leveling (TV)"))
        assertEquals("sololeveling", AnimeSourceMatcher.normalizeTitle("Solo Leveling (Dub)"))
        assertEquals("sololeveling", AnimeSourceMatcher.normalizeTitle("Solo Leveling (Season 1)"))
    }

    @Test
    fun `normalizeTitle lowercases and drops punctuation but keeps unicode`() {
        assertEquals("frierenbeyondjourneysend", AnimeSourceMatcher.normalizeTitle("Frieren: Beyond Journey's End"))
        assertEquals("葬送のフリーレン", AnimeSourceMatcher.normalizeTitle("葬送のフリーレン"))
    }

    @Test
    fun `normalizeTitle keeps mid-title parentheticals`() {
        // "(G)" is not at the end of the string — must be preserved.
        assertEquals("gidle", AnimeSourceMatcher.normalizeTitle("(G)I-DLE"))
    }

    @Test
    fun `scoreTitle exact normalized equality is 1`() {
        assertEquals(AnimeSourceMatcher.EXACT_SCORE, AnimeSourceMatcher.scoreTitle("Solo Leveling", "Solo Leveling (TV)"))
        assertEquals(AnimeSourceMatcher.EXACT_SCORE, AnimeSourceMatcher.scoreTitle("solo leveling", "SOLO LEVELING"))
    }

    @Test
    fun `scoreTitle containment is 085`() {
        assertEquals(AnimeSourceMatcher.CONTAINS_SCORE, AnimeSourceMatcher.scoreTitle("Solo Leveling", "Solo Leveling: Arise"))
        assertEquals(AnimeSourceMatcher.CONTAINS_SCORE, AnimeSourceMatcher.scoreTitle("Frieren", "Frieren: Beyond Journey's End"))
    }

    @Test
    fun `scoreTitle unrelated titles are zero`() {
        assertEquals(0.0, AnimeSourceMatcher.scoreTitle("Solo Leveling", "One Piece"))
        assertEquals(0.0, AnimeSourceMatcher.scoreTitle("Naruto", "One Piece"))
    }

    @Test
    fun `scoreTitle rejects tiny containment matches`() {
        assertEquals(0.0, AnimeSourceMatcher.scoreTitle("a", "ab"))
        assertEquals(0.0, AnimeSourceMatcher.scoreTitle("", "One Piece"))
    }

    // ----------------------------------------------------------------------
    // findBest / findMatches against fake sources
    // ----------------------------------------------------------------------

    @Test
    fun `findBest returns the exact match with source id and url`() = runBlocking {
        val source = FakeCatalogueSource(
            id = 99L,
            name = "SourceA",
            animes = listOf(Triple("/solo/1", "Solo Leveling (TV)", "thumb")),
        )
        val matcher = AnimeSourceMatcher(sourcesProvider = { listOf(source) })

        val best = matcher.findBest("Solo Leveling")

        assertNotNull(best)
        assertEquals(99L, best!!.sourceId)
        assertEquals("/solo/1", best.url)
        assertEquals(AnimeSourceMatcher.EXACT_SCORE, best.score)
        assertEquals("thumb", best.thumbnailUrl)
    }

    @Test
    fun `findBest returns null when no source has a confident match`() = runBlocking {
        val source = FakeCatalogueSource(
            id = 99L,
            name = "SourceA",
            animes = listOf(Triple("/naruto", "Naruto", null)),
        )
        val matcher = AnimeSourceMatcher(sourcesProvider = { listOf(source) })

        assertNull(matcher.findBest("Solo Leveling"))
    }

    @Test
    fun `findMatches excludes the failing source`() = runBlocking {
        val sourceA = FakeCatalogueSource(1L, "SourceA", animes = listOf(Triple("/solo", "Solo Leveling", null)))
        val sourceB = FakeCatalogueSource(2L, "SourceB", animes = listOf(Triple("/solo2", "Solo Leveling (TV)", null)))
        val matcher = AnimeSourceMatcher(sourcesProvider = { listOf(sourceA, sourceB) })

        val matches = matcher.findMatches("Solo Leveling", excludeSourceId = 1L)

        assertEquals(listOf(2L), matches.map { it.sourceId })
        assertTrue(matches.all { it.sourceId != 1L })
    }

    @Test
    fun `matches are ordered best first`() = runBlocking {
        val sourceA = FakeCatalogueSource(1L, "SourceA", animes = listOf(Triple("/solo-a", "Solo Leveling", null)))
        val sourceB = FakeCatalogueSource(2L, "SourceB", animes = listOf(Triple("/solo-b", "Solo Leveling: Arise", null)))
        val matcher = AnimeSourceMatcher(sourcesProvider = { listOf(sourceA, sourceB) })

        val matches = matcher.findMatches("Solo Leveling")

        assertEquals(listOf(1L, 2L), matches.map { it.sourceId })
        assertEquals(AnimeSourceMatcher.EXACT_SCORE, matches.first().score)
    }

    @Test
    fun `a throwing source does not fail the whole match`() = runBlocking {
        val broken = FakeCatalogueSource(1L, "Broken", throwOnSearch = RuntimeException("boom"))
        val good = FakeCatalogueSource(2L, "Good", animes = listOf(Triple("/solo", "Solo Leveling", null)))
        val matcher = AnimeSourceMatcher(sourcesProvider = { listOf(broken, good) })

        val best = matcher.findBest("Solo Leveling")

        assertNotNull(best)
        assertEquals(2L, best!!.sourceId)
    }

    @Test
    fun `blank title returns no matches without searching`() = runBlocking {
        val source = FakeCatalogueSource(1L, "SourceA", animes = listOf(Triple("/solo", "Solo Leveling", null)))
        val matcher = AnimeSourceMatcher(sourcesProvider = { listOf(source) })

        assertTrue(matcher.findMatches("   ").isEmpty())
    }
}
