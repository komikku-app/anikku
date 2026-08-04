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
}
