package app.anikku.macos.platform.anilist

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AniListSearchClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: AniListSearchClient

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        client = AniListSearchClient(httpClient = OkHttpClient(), endpoint = baseUrl)
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    private val mediaFixture = """
        {
          "data": {
            "Page": {
              "media": [
                {
                  "id": 1,
                  "episodes": 37,
                  "seasonYear": 2006,
                  "format": "TV",
                  "description": "<p>Light Yagami finds a notebook.</p>",
                  "title": { "romaji": "Death Note", "english": "Death Note", "native": "デスノート" },
                  "coverImage": {
                    "medium": "https://s4.anilist.co/.../medium/bx1.jpg",
                    "large": "https://s4.anilist.co/.../large/bx1.jpg"
                  }
                },
                {
                  "id": 2,
                  "episodes": null,
                  "seasonYear": null,
                  "format": "MOVIE",
                  "title": { "romaji": "Death Note 2: The Last Name", "english": null, "native": null },
                  "coverImage": { "medium": null, "large": null }
                }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parses media list from GraphQL response`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(mediaFixture))

        val results = client.searchAnime("Death Note")

        assertEquals(2, results.size)
        val first = results[0]
        assertEquals(1, first.id)
        assertEquals("Death Note", first.romajiTitle)
        assertEquals("Death Note", first.englishTitle)
        assertEquals("デスノート", first.nativeTitle)
        assertEquals("https://s4.anilist.co/.../large/bx1.jpg", first.coverUrl)
        assertEquals(37, first.episodes)
        assertEquals(2006, first.seasonYear)
        assertEquals("TV", first.format)
        assertTrue(first.synopsis!!.contains("Light Yagami"))

        val second = results[1]
        assertEquals("MOVIE", second.format)
        assertNull(second.episodes)
        assertNull(second.coverUrl)
        assertNull(second.englishTitle)
    }

    @Test
    fun `returns empty list on non-2xx response`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("oops"))

        assertTrue(client.searchAnime("Death Note").isEmpty())
    }

    @Test
    fun `returns empty list on malformed body`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("not json at all"))

        assertTrue(client.searchAnime("Death Note").isEmpty())
    }

    @Test
    fun `sends the search query and variable in the request body`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(mediaFixture))

        client.searchAnime("Death Note")

        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("media(search: \$search, type: ANIME)".replace("\\$", "$")))
        assertTrue(body.contains("\"search\":\"Death Note\""))
    }

    @Test
    fun `returns empty for blank query without hitting the network`() = runBlocking {
        assertTrue(client.searchAnime("   ").isEmpty())
        assertEquals(0, mockServer.requestCount)
    }

    @Test
    fun `pickBest prefers exact normalized match`() {
        val exact = AniListAnime(id = 1, romajiTitle = "Death Note")
        val sequel = AniListAnime(id = 2, romajiTitle = "Death Note 2: The Last Name")

        val best = AniListSearchClient.pickBest("Death Note", listOf(sequel, exact))
        assertEquals(1, best?.id)
    }

    @Test
    fun `pickBest accepts containment matches`() {
        val sequel = AniListAnime(id = 2, romajiTitle = "Death Note 2: The Last Name")
        assertEquals(2, AniListSearchClient.pickBest("Death Note", listOf(sequel))?.id)
    }

    @Test
    fun `pickBest rejects unrelated candidates`() {
        val naruto = AniListAnime(id = 3, romajiTitle = "Naruto")
        assertNull(AniListSearchClient.pickBest("Death Note", listOf(naruto)))
    }

    // ---- Airing schedule ----------------------------------------------------

    private val airingFixture = """
        {
          "data": {
            "Page": {
              "airingSchedules": [
                {
                  "id": 1,
                  "episode": 12,
                  "airingAt": 1760000000,
                  "media": {
                    "id": 100,
                    "episodes": 24,
                    "seasonYear": 2026,
                    "format": "TV",
                    "title": { "romaji": "Frieren", "english": "Frieren: Beyond Journey's End" },
                    "coverImage": { "medium": "https://x/med.jpg", "large": "https://x/large.jpg" }
                  }
                },
                {
                  "id": 2,
                  "episode": 5,
                  "airingAt": 1760003600,
                  "media": {
                    "id": 101,
                    "episodes": null,
                    "seasonYear": null,
                    "format": "TV",
                    "title": { "romaji": "Dandadan", "english": null },
                    "coverImage": { "medium": null, "large": null }
                  }
                }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parses airing schedule entries`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(airingFixture))

        val episodes = client.airingThisWeek(nowEpochSeconds = 1_000_000_000L)

        assertEquals(2, episodes.size)
        val first = episodes[0]
        assertEquals(12, first.episode)
        assertEquals(1760000000L, first.airingAt)
        assertEquals("Frieren", first.media.romajiTitle)
        assertEquals(24, first.media.episodes)
    }

    @Test
    fun `airing schedule sends now and end window variables`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(airingFixture))

        client.airingThisWeek(nowEpochSeconds = 1_000_000_000L)

        val body = mockServer.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"now\":1000000000"))
        assertTrue(body.contains("\"end\":"))
    }

    // ---- Trending / seasonal ------------------------------------------------

    private val mediaListFixture = """
        {
          "data": {
            "Page": {
              "media": [
                { "id": 200, "title": { "romaji": "Solo Leveling", "english": null }, "coverImage": { "large": "https://x/l2.jpg" } },
                { "id": 201, "title": { "romaji": "One Piece", "english": null }, "coverImage": { "large": null } }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `trending parses media list`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(mediaListFixture))

        val results = client.trending(perPage = 2)

        assertEquals(2, results.size)
        assertEquals("Solo Leveling", results[0].romajiTitle)
        assertEquals("https://x/l2.jpg", results[0].coverUrl)
        assertTrue(mockServer.takeRequest().body.readUtf8().contains("TRENDING_DESC"))
    }

    @Test
    fun `seasonal sends season and year variables`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(mediaListFixture))

        client.seasonal(season = "SUMMER", year = 2026)

        val body = mockServer.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"season\":\"SUMMER\""))
        assertTrue(body.contains("\"year\":2026"))
        assertTrue(body.contains("POPULARITY_DESC"))
    }

    // ---- Recommendations ----------------------------------------------------

    private val mediaListUserFixture = """
        {
          "data": {
            "Page": {
              "mediaList": [
                { "mediaId": 100 },
                { "mediaId": 101 }
              ]
            }
          }
        }
    """.trimIndent()

    private val recommendationsFixture = """
        {
          "data": {
            "Page": {
              "recommendations": [
                { "mediaRecommendation": { "id": 300, "title": { "romaji": "Mushoku Tensei", "english": null }, "coverImage": { "large": null } } }
              ]
            }
          }
        }
    """.trimIndent()

    @Test
    fun `recommendations requires a username`() = runBlocking {
        assertTrue(client.recommendationsFor(userName = null).isEmpty())
        assertTrue(client.recommendationsFor(userName = "   ").isEmpty())
        assertEquals(0, mockServer.requestCount)
    }

    @Test
    fun `recommendations fetches user list then recommendations`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(mediaListUserFixture))
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(recommendationsFixture))

        val results = client.recommendationsFor(userName = "ernest")

        assertEquals(1, results.size)
        assertEquals("Mushoku Tensei", results[0].romajiTitle)

        val first = mockServer.takeRequest().body.readUtf8()
        assertTrue(first.contains("\"userName\":\"ernest\""))
        val second = mockServer.takeRequest().body.readUtf8()
        assertTrue(second.contains("mediaId_in"))
    }
}
