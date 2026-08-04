package app.anikku.macos.platform.subtitle

import app.anikku.macos.platform.security.MacOSSecretStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests for [SubtitleFetcher]: episode matching, Jimaku download caching, and
 * the credential store. Live provider calls (AniList/OpenSubtitles) are
 * verified via the app's HTTP layer in end-to-end smoke tests; these cover the
 * pure logic + local download path.
 */
class SubtitleFetcherTest {

    private fun buildFetcher(
        server: MockWebServer,
        credentials: SubtitleCredentials = SubtitleCredentials(
            jimakuToken = "test-token",
            openSubtitlesApiKey = "test-key",
            openSubtitlesUsername = "user",
            openSubtitlesPassword = "pass",
        ),
    ): SubtitleFetcher {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "anikku-subtitle-test-${System.nanoTime()}")
        cacheDir.mkdirs()
        return SubtitleFetcher(
            client = OkHttpClient(),
            credentialStore = SubtitleCredentialStore(
                keychain = FakeSecretStore(),
                bakedDefaults = credentials,
            ),
            cacheDirectory = cacheDir,
        )
    }

    /** Minimal in-memory secret store for tests (avoids the `security` CLI). */
    private class FakeSecretStore : MacOSSecretStore {
        private val map = mutableMapOf<String, String>()
        override val isAvailable: Boolean = true
        override var lastError: String? = null
            private set
        override fun store(key: String, value: String): Boolean { map[key] = value; return true }
        override fun retrieve(key: String): String? = map[key]
        override fun delete(key: String): Boolean { map.remove(key); return true }
    }

    // ---- Episode filename matching ----------------------------------------

    @Test
    fun `matches episode in various filename formats`() {
        val fetcher = buildFetcher(MockWebServer())
        assertTrue(fetcher.fileMatchesEpisode("Anime - 12.srt", 12))
        assertTrue(fetcher.fileMatchesEpisode("Anime S1E12.ass", 12))
        assertTrue(fetcher.fileMatchesEpisode("Anime Episode 12.srt", 12))
        assertTrue(fetcher.fileMatchesEpisode("Anime ep12.vtt", 12))
        assertTrue(fetcher.fileMatchesEpisode("Anime #12.srt", 12))
        assertTrue(fetcher.fileMatchesEpisode("Anime.12.srt", 12))
        assertFalse(fetcher.fileMatchesEpisode("Anime - 2.srt", 12))
        assertFalse(fetcher.fileMatchesEpisode("Anime S1E1.srt", 12))
        assertFalse(fetcher.fileMatchesEpisode("Anime OP Theme.srt", 12))
    }

    @Test
    fun `does not match season markers or years as episodes`() {
        val fetcher = buildFetcher(MockWebServer())
        // "Season 2" must not match episode 2; the year (2025) must not match
        // episode 2025 or episode 25.
        assertFalse(fetcher.fileMatchesEpisode("Anime Season 2 - 01.srt", 2))
        assertFalse(fetcher.fileMatchesEpisode("Anime (2025) - 01.srt", 2025))
        assertFalse(fetcher.fileMatchesEpisode("Anime (2025) - 01.srt", 25))
        // But the real episode in those files still matches.
        assertTrue(fetcher.fileMatchesEpisode("Anime Season 2 - 01.srt", 1))
        assertTrue(fetcher.fileMatchesEpisode("Anime (2025) - 01.srt", 1))
    }

    @Test
    fun `matches netflix-style continuation numbering with offset`() {
        val fetcher = buildFetcher(MockWebServer())
        // S2 entry: Season 1 had 12 episodes, so S2E1 == "13" and S2E2 == "14".
        val offset = 12
        assertTrue(fetcher.fileMatchesEpisode("Solo Leveling (2025) - 13 「You aren't E-rank」.ass", 1, offset))
        assertTrue(fetcher.fileMatchesEpisode("俺だけレベルアップな件.S01E13.You.aren_t.E-rank.srt", 1, offset))
        assertTrue(fetcher.fileMatchesEpisode("俺だけレベルアップな件.S01E14.srt", 2, offset))
        // Season-relative filenames in the same entry still match directly.
        assertTrue(fetcher.fileMatchesEpisode("[NanakoRaws] Solo Leveling Season 2 - 01.srt", 1, offset))
        assertTrue(fetcher.fileMatchesEpisode("[Judas] Solo Leveling - S02E02.ass", 2, offset))
        // And the offset form does not over-match unrelated numbers.
        assertFalse(fetcher.fileMatchesEpisode("Solo Leveling (2025) - 03.srt", 1, offset))
    }

    // ---- Jimaku download path ---------------------------------------------

    @Test
    fun `downloads a candidate from a direct url and caches it`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("1\n00:00:01,000 --> 00:00:03,000\nHello\n"),
        )
        server.start()
        try {
            val fetcher = buildFetcher(server)
            val candidate = SubtitleCandidate(
                provider = "jimaku",
                title = "Solo Leveling - 01.mkv.srt",
                language = "en",
                downloadUrl = server.url("/subs/en-1.srt").toString(),
                cacheKey = "jimaku-151807-ep1-500-9001",
            )
            val file = fetcher.downloadCandidate(candidate)
            assertNotNull(file)
            assertTrue(file!!.isFile)
            assertTrue(file.readText().contains("Hello"))

            // Second download returns the cached file (no extra HTTP call).
            val again = fetcher.downloadCandidate(candidate)
            assertNotNull(again)
            assertEquals(file.absolutePath, again!!.absolutePath)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `download returns null when provider not configured`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val fetcher = buildFetcher(server, credentials = SubtitleCredentials())
            // OpenSubtitles candidate requires a configured login → safe null.
            val candidate = SubtitleCandidate(
                provider = "opensubtitles",
                title = "Some.Release.en.srt",
                language = "en",
                downloadUrl = "opensubtitles://download/999",
                cacheKey = "os-999",
            )
            assertNull(fetcher.downloadCandidate(candidate))
        } finally {
            server.shutdown()
        }
    }

    // ---- Season-aware AniList selection ------------------------------------

    private fun media(id: Int, episodes: Int?): SubtitleFetcher.Media =
        SubtitleFetcher.Media(id = id, episodes = episodes)

    @Test
    fun `picks season whose episode range covers the requested episode`() {
        val fetcher = buildFetcher(MockWebServer())
        // Solo Leveling: S1 (151807, 12 eps), S2 (176496, 13 eps), movie (1 ep)
        val candidates = listOf(media(151807, 12), media(176496, 13), media(184694, 1))
        // Ep 5 of S1 → S1
        assertEquals(151807, fetcher.pickSeasonMatch(candidates, 5.0))
        // Ep 13 → S2 (S1 only has 12)
        assertEquals(176496, fetcher.pickSeasonMatch(candidates, 13.0))
        // Ep 0 / unknown → top result
        assertEquals(151807, fetcher.pickSeasonMatch(candidates, 0.0))
    }

    @Test
    fun `falls back when episode ranges are unknown`() {
        val fetcher = buildFetcher(MockWebServer())
        assertEquals(10, fetcher.pickSeasonMatch(listOf(media(10, null), media(20, null)), 7.0))
        assertEquals(null, fetcher.pickSeasonMatch(emptyList(), 3.0))
    }

    @Test
    fun `ongoing season with unknown range still resolves by title rank`() {
        val fetcher = buildFetcher(MockWebServer())
        // S2 is airing (AniList reports episodes=null) and ranks first for a
        // season-qualified title → any requested episode resolves to S2.
        assertEquals(200, fetcher.pickSeasonMatch(listOf(media(200, null), media(100, 12)), 8.0))
        // Generic title ranks S1 first (12 eps); ep 8 is S1's.
        assertEquals(100, fetcher.pickSeasonMatch(listOf(media(100, 12), media(200, null)), 8.0))
    }

    // ---- Credential store --------------------------------------------------

    @Test
    fun `credential store prefers keychain over baked defaults`() {
        val keychain = FakeSecretStore()
        val store = SubtitleCredentialStore(
            keychain = keychain,
            bakedDefaults = SubtitleCredentials(jimakuToken = "baked"),
        )
        // Keychain empty → baked default
        assertEquals("baked", store.load().jimakuToken)

        // User override → keychain wins
        store.save(SubtitleCredentials(jimakuToken = "user-token"))
        assertEquals("user-token", store.load().jimakuToken)
    }

    @Test
    fun `anyConfigured reflects available providers`() {
        val keychain = FakeSecretStore()
        val store = SubtitleCredentialStore(
            keychain = keychain,
            bakedDefaults = SubtitleCredentials(),
        )
        assertFalse(store.anyConfigured())
        store.save(SubtitleCredentials(jimakuToken = "token"))
        assertTrue(store.anyConfigured())
    }
}
