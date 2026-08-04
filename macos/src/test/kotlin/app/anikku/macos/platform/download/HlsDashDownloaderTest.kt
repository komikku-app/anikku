package app.anikku.macos.platform.download

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class HlsDashDownloaderTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var server: MockWebServer
    private lateinit var downloader: HlsDashDownloader

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = HlsDashDownloader(OkHttpClient())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun base(): String = server.url("/").toString().trimEnd('/')

    // ------------------------------------------------------------------ HLS

    @Test
    fun `downloads hls master variant and segments into a local playlist`() = runBlocking {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080
            high/index.m3u8
        """.trimIndent()
        val variant = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.000,
            seg0.ts
            #EXTINF:10.000,
            seg1.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        server.enqueue(MockResponse().setBody(master))
        server.enqueue(MockResponse().setBody(variant))
        server.enqueue(MockResponse().setBody("x".repeat(100)))
        server.enqueue(MockResponse().setBody("x".repeat(200)))

        val result = downloader.download("${base()}/master.m3u8", emptyMap<String, String>(), tempDir)

        assertNotNull(result)
        val playlist = File(tempDir, "playlist.m3u8")
        assertTrue(playlist.isFile)
        val text = playlist.readText()
        // Picked the HIGHEST bandwidth variant (high/index.m3u8).
        assertTrue(text.contains("segments/seg_00000.ts"))
        assertTrue(text.contains("segments/seg_00001.ts"))
        assertEquals(2, File(tempDir, "segments").listFiles()?.count { it.name.startsWith("seg_") })
        // Requests: master, high variant, 2 segments.
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `rewrites encrypted key URI to a local file`() = runBlocking {
        val variant = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:10.000,
            seg0.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        server.enqueue(MockResponse().setBody(variant))
        server.enqueue(MockResponse().setBody("x".repeat(16)))
        server.enqueue(MockResponse().setBody("x".repeat(100)))

        val result = downloader.download("${base()}/v/index.m3u8", emptyMap<String, String>(), tempDir)

        assertNotNull(result)
        val text = File(tempDir, "playlist.m3u8").readText()
        assertTrue(text.contains("segments/key_0.key"))
        assertTrue(File(tempDir, "segments/key_0.key").isFile)
    }

    // ----------------------------------------------------------------- DASH

    @Test
    fun `downloads dash fmp4 segments and writes an hls playlist`() = runBlocking {
        val mpd = """
            <?xml version="1.0"?>
            <MPD type="static" mediaPresentationDuration="PT20S">
              <Period>
                <AdaptationSet contentType="video" mimeType="video/mp4">
                  <Representation id="0" mimeType="video/mp4" bandwidth="1000000" width="1920" height="1080">
                    <SegmentTemplate timescale="1000" initialization="init-stream0.m4s" media="chunk-stream0-${"$"}Number%05d${"$"}.m4s" startNumber="1">
                      <SegmentTimeline>
                        <S d="10000" />
                        <S d="10000" />
                      </SegmentTimeline>
                    </SegmentTemplate>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(mpd))
        server.enqueue(MockResponse().setBody("x".repeat(500))) // init
        server.enqueue(MockResponse().setBody("x".repeat(1000))) // seg 1
        server.enqueue(MockResponse().setBody("x".repeat(1000))) // seg 2

        val result = downloader.download("${base()}/dash/token/manifest.mpd", emptyMap<String, String>(), tempDir)

        assertNotNull(result)
        val playlist = File(tempDir, "playlist.m3u8")
        val text = playlist.readText()
        assertTrue(text.contains("#EXT-X-MAP:URI=\"segments/init.m4s\""))
        assertTrue(text.contains("segments/seg_00000.m4s"))
        assertTrue(text.contains("segments/seg_00001.m4s"))
        assertTrue(text.endsWith("#EXT-X-ENDLIST\n"))
        assertEquals(3, File(tempDir, "segments").listFiles()?.size)
        // Requests: MPD, init, seg1, seg2.
        assertEquals(4, server.requestCount)
    }

    @Test
    fun `dash segments resolve under a token directory when the mpd url has no extension`() = runBlocking {
        val mpd = """
            <MPD>
              <Period>
                <AdaptationSet contentType="video" mimeType="video/mp4">
                  <Representation id="0" mimeType="video/mp4" bandwidth="500000">
                    <SegmentTemplate timescale="1000" initialization="init-stream0.m4s" media="chunk-stream0-${"$"}Number%05d${"$"}.m4s" startNumber="1">
                      <SegmentTimeline><S d="10000" /></SegmentTimeline>
                    </SegmentTemplate>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(mpd))
        server.enqueue(MockResponse().setBody("x".repeat(500)))
        server.enqueue(MockResponse().setBody("x".repeat(1000)))

        val result = downloader.download("${base()}/dash/.eJwTOKEN", emptyMap<String, String>(), tempDir)

        assertNotNull(result)
        val text = File(tempDir, "playlist.m3u8").readText()
        assertTrue(text.contains("segments/seg_00000.m4s"))
        // The init + segment requests must go under the token directory
        // (the MPD request itself is the token path without a trailing slash).
        val paths = (0 until server.requestCount).map { server.takeRequest().path }
        val mediaPaths = paths.drop(1)
        assertTrue(mediaPaths.all { it!!.startsWith("/dash/.eJwTOKEN/") }, "paths: $paths")
    }

    // ------------------------------------------------------------- detection

    @Test
    fun `detects manifest urls`() {
        assertTrue(isManifestUrl("https://x/a/master.m3u8"))
        assertTrue(isManifestUrl("https://x/a/video.mpd"))
        assertTrue(isManifestUrl("https://x/dash/token"))
        assertTrue(isManifestUrl("https://x/hls/stream"))
        assertFalse(isManifestUrl("https://x/stream.mkv"))
        assertFalse(isManifestUrl("magnet:?xt=urn:btih:abc"))
    }
}
