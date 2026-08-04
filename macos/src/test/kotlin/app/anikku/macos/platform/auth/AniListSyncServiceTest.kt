package app.anikku.macos.platform.auth

import app.anikku.macos.platform.data.CATEGORY_DEFAULT_ID
import app.anikku.macos.platform.data.CategoryEntry
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSSecretStore
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class AniListSyncServiceTest {

    // ------------------------------------------------------------------
    // Pure merge helpers
    // ------------------------------------------------------------------

    private val categories = listOf(
        CategoryEntry(id = 0L, name = "Default", isDefault = true),
        CategoryEntry(id = 1L, name = "Watching"),
        CategoryEntry(id = 2L, name = "Completed"),
        CategoryEntry(id = 3L, name = "Dropped"),
        CategoryEntry(id = 4L, name = "Plan to Watch"),
    )

    @Test
    fun `categoryIdForStatus maps AniList list statuses to categories`() {
        assertEquals(1L, AniListSyncService.categoryIdForStatus("CURRENT", categories))
        assertEquals(1L, AniListSyncService.categoryIdForStatus("REPEATING", categories))
        assertEquals(2L, AniListSyncService.categoryIdForStatus("COMPLETED", categories))
        assertEquals(3L, AniListSyncService.categoryIdForStatus("DROPPED", categories))
        assertEquals(4L, AniListSyncService.categoryIdForStatus("PLANNING", categories))
        assertEquals(CATEGORY_DEFAULT_ID, AniListSyncService.categoryIdForStatus("PAUSED", categories))
        assertEquals(CATEGORY_DEFAULT_ID, AniListSyncService.categoryIdForStatus("UNKNOWN", categories))
    }

    @Test
    fun `categoryIdForStatus matches renamed categories case-insensitively`() {
        val renamed = listOf(
            CategoryEntry(id = 0L, name = "Default", isDefault = true),
            CategoryEntry(id = 9L, name = "watching"),
        )
        assertEquals(9L, AniListSyncService.categoryIdForStatus("CURRENT", renamed))
    }

    @Test
    fun `animeStatusInt maps AniList media statuses`() {
        assertEquals(1, AniListSyncService.animeStatusInt("RELEASING"))
        assertEquals(2, AniListSyncService.animeStatusInt("FINISHED"))
        assertEquals(4, AniListSyncService.animeStatusInt("NOT_YET_RELEASED"))
        assertEquals(5, AniListSyncService.animeStatusInt("CANCELLED"))
        assertEquals(6, AniListSyncService.animeStatusInt("HIATUS"))
        assertEquals(0, AniListSyncService.animeStatusInt(null))
        assertEquals(0, AniListSyncService.animeStatusInt("WEIRD"))
    }

    @Test
    fun `shouldPushStatus pushes commit states but never downgrades completed`() {
        // Terminal/commit states push regardless of remote.
        assertEquals("COMPLETED", AniListSyncService.shouldPushStatus("Completed", "CURRENT"))
        assertEquals("DROPPED", AniListSyncService.shouldPushStatus("Dropped", null))
        // Never downgrade a remote terminal state.
        assertNull(AniListSyncService.shouldPushStatus("Watching", "COMPLETED"))
        assertNull(AniListSyncService.shouldPushStatus("Completed", "COMPLETED"))
        // Watching promotes only when remote is unset/planning/paused.
        assertEquals("CURRENT", AniListSyncService.shouldPushStatus("Watching", null))
        assertEquals("CURRENT", AniListSyncService.shouldPushStatus("Watching", "PLANNING"))
        assertEquals("CURRENT", AniListSyncService.shouldPushStatus("Watching", "PAUSED"))
        assertNull(AniListSyncService.shouldPushStatus("Watching", "CURRENT"))
        // Default category carries no status.
        assertNull(AniListSyncService.shouldPushStatus("Default", null))
        assertNull(AniListSyncService.shouldPushStatus(null, null))
    }

    @Test
    fun `sync outcome message describes the cycle`() {
        assertEquals("AniList sync: up to date", SyncOutcome().toMessage())
        assertEquals(
            "AniList sync: 3 imported, 2 pushed",
            SyncOutcome(imported = 3, pushed = 2).toMessage(),
        )
        assertEquals(
            "AniList sync: 1 imported, 1 updated, 1 error(s)",
            SyncOutcome(imported = 1, updated = 1, errors = listOf("x")).toMessage(),
        )
    }

    // ------------------------------------------------------------------
    // fetchAniListLibrary parsing
    // ------------------------------------------------------------------

    @TempDir
    lateinit var tempDir: Path

    private class LastCallInterceptor : Interceptor {
        var statusCode: Int = 200
        var responseBody: String = "{}"
        var lastRequestBody: String? = null
        var lastRequestUrl: String? = null
        var lastBodyClass: String? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            // Capture the outgoing body so tests can validate the payload.
            lastRequestUrl = request.url.toString()
            lastBodyClass = request.body?.javaClass?.name
            lastRequestBody = request.body?.let { body ->
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(if (statusCode == 200) "OK" else "Error")
                .body(responseBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    /** In-memory keychain so tests never touch the real macOS keychain. */
    private class FakeKeychain : MacOSSecretStore {
        private val map = mutableMapOf<String, String>()
        override val isAvailable: Boolean = true
        override val lastError: String? get() = null
        override fun store(key: String, value: String): Boolean { map[key] = value; return true }
        override fun retrieve(key: String): String? = map[key]
        override fun delete(key: String): Boolean { map.remove(key); return true }
    }

    private fun buildManager(interceptor: LastCallInterceptor): TrackerManager {
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val prefs = MacOSPreferenceStore(tempDir.resolve("preferences.json").toFile())
        val tokenStore = TrackerTokenStore(prefs, FakeKeychain())
        tokenStore.saveTokensWithUsername(
            "anilist",
            TokenResponse(accessToken = "test-token", refreshToken = "test-refresh"),
            "ernestadmin",
        )
        return TrackerManager(
            oauthManager = TrackerOAuthManager(client),
            tokenStore = tokenStore,
            httpClient = client,
        )
    }

    @Test
    fun `fetchAniListLibrary parses media list collection`() {
        val interceptor = LastCallInterceptor().apply {
            responseBody = """
                {
                  "data": {
                    "MediaListCollection": {
                      "lists": [
                        {
                          "entries": [
                            {
                              "status": "CURRENT",
                              "score": 8,
                              "progress": 5,
                              "media": {
                                "id": 151807,
                                "status": "RELEASING",
                                "episodes": 12,
                                "title": { "romaji": "Solo Leveling", "english": "Solo Leveling" },
                                "coverImage": { "extraLarge": "https://s4.anilist.co/bx151807.jpg" },
                                "genres": ["Action", "Adventure"],
                                "description": "The weakest hunter"
                              }
                            },
                            {
                              "status": "COMPLETED",
                              "score": 9,
                              "progress": 13,
                              "media": {
                                "id": 176496,
                                "status": "FINISHED",
                                "episodes": 13,
                                "title": { "romaji": "Solo Leveling Season 2", "english": null },
                                "coverImage": { "extraLarge": null },
                                "genres": [],
                                "description": ""
                              }
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
            """.trimIndent()
        }
        val manager = buildManager(interceptor)

        val library = manager.fetchAniListLibrary()

        assertEquals(2, library?.size)
        val first = library!![0]
        assertEquals(151807L, first.mediaId)
        assertEquals("Solo Leveling", first.title)
        assertEquals("CURRENT", first.status)
        assertEquals(5, first.progress)
        assertEquals(12, first.totalEpisodes)
        assertEquals("https://s4.anilist.co/bx151807.jpg", first.coverUrl)
        assertEquals(listOf("Action", "Adventure"), first.genres)
        assertEquals("RELEASING", first.mediaStatus)

        val second = library[1]
        // English title missing -> falls back to romaji.
        assertEquals("Solo Leveling Season 2", second.title)
        assertEquals(13, second.progress)
        assertTrue(second.genres.isNullOrEmpty())
        assertNull(second.coverUrl)
    }

    @Test
    fun `fetchAniListLibrary returns null when not logged in`() {
        val client = OkHttpClient.Builder().build()
        val prefs = MacOSPreferenceStore(tempDir.resolve("empty-prefs.json").toFile())
        val manager = TrackerManager(
            oauthManager = TrackerOAuthManager(client),
            tokenStore = TrackerTokenStore(prefs, FakeKeychain()),
            httpClient = client,
        )
        assertNull(manager.fetchAniListLibrary())
    }

    @Test
    fun `fetchAniListLibrary returns empty list on HTTP error`() {
        val interceptor = LastCallInterceptor().apply { statusCode = 500 }
        val manager = buildManager(interceptor)
        assertEquals(emptyList<AniListLibraryEntry>(), manager.fetchAniListLibrary() ?: emptyList<AniListLibraryEntry>())
    }

    @Test
    fun `anilist library query is well-formed JSON with proper quoting`() {
        // Regression: the username was double-quoted (JSONObject.quote adds the
        // surrounding quotes itself; the old code wrapped them again), producing
        // invalid JSON that AniList rejected with "No query or mutation provided"
        // — sync silently imported nothing.
        val manager = buildManager(LastCallInterceptor())
        val body = manager.buildAniListLibraryQuery("ErnestHysa")

        val json = org.json.JSONObject(body) // throws if invalid JSON (the regression)
        assertTrue(json.getString("query").contains("MediaListCollection"))
        assertEquals("ANIME", json.getJSONObject("variables").getString("type"))
        assertEquals("ErnestHysa", json.getJSONObject("variables").getString("user"))
    }

    @Test
    fun `sync not possible without anilist login`() {
        val client = OkHttpClient.Builder().build()
        val prefs = MacOSPreferenceStore(tempDir.resolve("nologin.json").toFile())
        val service = AniListSyncService(
            trackerManager = TrackerManager(
                oauthManager = TrackerOAuthManager(client),
                tokenStore = TrackerTokenStore(prefs, FakeKeychain()),
                httpClient = client,
            ),
            libraryRepository = app.anikku.macos.platform.data.LibraryRepository(tempDir.toFile()),
            historyRepository = app.anikku.macos.platform.data.HistoryRepository(tempDir.toFile()),
        )
        assertFalse(service.canSync())
    }
}
