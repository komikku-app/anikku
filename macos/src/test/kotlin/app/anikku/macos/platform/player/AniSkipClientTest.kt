package app.anikku.macos.platform.player

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AniSkipClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: AniSkipClient

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        client = AniSkipClient(httpClient = OkHttpClient(), baseUrl = baseUrl)
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    private val opEdFixture = """
        {
          "found": true,
          "results": [
            {
              "interval": { "startTime": 3.221, "endTime": 93.221 },
              "skipType": "op",
              "skipId": "a6ab121c-0001",
              "episodeLength": 1417.16
            },
            {
              "interval": { "startTime": 1417.135, "endTime": 1507.135 },
              "skipType": "ed",
              "skipId": "a6ab121c-0002",
              "episodeLength": 1417.16
            },
            {
              "interval": { "startTime": 10.0, "endTime": 60.0 },
              "skipType": "recap",
              "skipId": "a6ab121c-0003",
              "episodeLength": 1417.16
            }
          ],
          "message": "Successfully found skip times",
          "statusCode": 200
        }
    """.trimIndent()

    @Test
    fun `parses op ed and recap intervals`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(opEdFixture))

        val intervals = client.fetchSkipTimes(malId = 52991, episode = 1)

        assertEquals(3, intervals.size)
        val op = intervals[0]
        assertEquals("op", op.skipType)
        assertTrue(op.isIntro)
        assertEquals(3.221, op.startTime)
        assertEquals(93.221, op.endTime)
        assertEquals("Intro", op.label)

        val ed = intervals[1]
        assertTrue(ed.isEnding)
        assertEquals("Outro", ed.label)

        val recap = intervals[2]
        assertTrue(recap.isRecap)
        assertEquals("Recap", recap.label)
    }

    @Test
    fun `requests the mal id and episode in the URL`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(opEdFixture))

        client.fetchSkipTimes(malId = 52991, episode = 12)

        val request = mockServer.takeRequest()
        assertTrue(request.path!!.startsWith("/v2/skip-times/52991/12"))
        assertTrue(request.path!!.contains("types[]=op"))
        assertTrue(request.path!!.contains("types[]=ed"))
    }

    @Test
    fun `returns empty when found is false`() = runBlocking {
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"found": false, "results": [], "message": "No skip times", "statusCode": 404}""",
            ),
        )
        assertTrue(client.fetchSkipTimes(malId = 1, episode = 1).isEmpty())
    }

    @Test
    fun `returns empty on non-2xx response`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        assertTrue(client.fetchSkipTimes(malId = 1, episode = 1).isEmpty())
    }

    @Test
    fun `returns empty on malformed body`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertTrue(client.fetchSkipTimes(malId = 1, episode = 1).isEmpty())
    }

    @Test
    fun `skips invalid intervals`() = runBlocking {
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "found": true,
                  "results": [
                    { "interval": { "startTime": 10.0, "endTime": 5.0 }, "skipType": "op" },
                    { "interval": { "startTime": 20.0, "endTime": 40.0 }, "skipType": "op" }
                  ],
                  "statusCode": 200
                }
                """.trimIndent(),
            ),
        )
        val intervals = client.fetchSkipTimes(malId = 1, episode = 1)
        assertEquals(1, intervals.size)
        assertEquals(20.0, intervals.first().startTime)
    }
}
