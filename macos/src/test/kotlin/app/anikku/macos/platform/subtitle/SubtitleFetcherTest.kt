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
