package app.anikku.macos.platform.extension

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Integration test that verifies the extension manager pipeline using the
 * sample extension JAR built from sample-extension/.
 *
 * Tests the full lifecycle:
 * 1. Load JAR metadata (extension.json)
 * 2. Compute SHA-256 hash (trust)
 * 3. Load and instantiate the source class
 * 4. Call suspend API methods (getPopularAnime, getAnimeDetails, etc.)
 * 5. Verify returned data matches the hardcoded sample data
 */
class SampleExtensionIntegrationTest {

    private lateinit var tempDir: File
    private lateinit var jarFile: File
    private lateinit var source: CatalogueSource

    @Before
    fun setUp() {
        tempDir = createTempDir("anikku-sample-ext-test-")
        jarFile = File(tempDir, "com.example.animeextension.jar")

        // Copy the pre-built sample extension JAR into the temp directory
        val builtJar = File("sample-extension/build/libs/sample-extension-1.0.0.jar")
        Assert.assertTrue(
            "Sample extension JAR not built. Run: cd macos/sample-extension && ./gradlew buildExtensionJar",
            builtJar.exists(),
        )
        builtJar.copyTo(jarFile, overwrite = true)
    }

    @After
    fun tearDown() {
        MacOSExtensionLoader.closeClassLoader("com.example.animeextension")
        tempDir.deleteRecursively()
    }

    @Test
    fun `loadExtension reads metadata from JAR`() {
        val metadata = MacOSExtensionLoader.readMetadata(jarFile)
        Assert.assertNotNull("Failed to read extension.json from JAR", metadata)
        Assert.assertEquals("Aniyomi: SampleSource", metadata!!.name)
        Assert.assertEquals("com.example.animeextension", metadata.pkgName)
        Assert.assertEquals("1.0.0", metadata.versionName)
        Assert.assertEquals(100L, metadata.versionCode)
        Assert.assertEquals(14.0, metadata.libVersion, 0.001)
        Assert.assertEquals("en", metadata.lang)
        Assert.assertEquals("com.example.animeextension.SampleAnimeSource", metadata.sourceClass)
    }

    @Test
    fun `computeSha256 produces a valid hash`() {
        val hash = MacOSExtensionLoader.computeSha256(jarFile)
        Assert.assertEquals("SHA-256 should be 64 hex characters", 64, hash.length)
        Assert.assertTrue("Hash should be lowercase hex", hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `loadExtension returns Untrusted when trust store is empty`() {
        val result = MacOSExtensionLoader.loadExtension(
            jarFile = jarFile,
            trustStore = emptyMap(),
        )

        Assert.assertTrue("Expected Untrusted result", result is LoadResult.Untrusted)
        val untrusted = result as LoadResult.Untrusted
        Assert.assertEquals("com.example.animeextension", untrusted.extension.pkgName)
        Assert.assertEquals("Aniyomi: SampleSource", untrusted.extension.name)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `loadExtension succeeds with correct trust entry`() {
        val hash = MacOSExtensionLoader.computeSha256(jarFile)
        val trustEntry = MacOSExtensionLoader.TrustEntry(
            pkgName = "com.example.animeextension",
            versionCode = 100L,
            signatureHash = hash,
        )

        val result = MacOSExtensionLoader.loadExtension(
            jarFile = jarFile,
            trustStore = mapOf("com.example.animeextension" to listOf(trustEntry)),
            loadNsfw = false,
        )

        Assert.assertTrue("Expected Success result", result is LoadResult.Success)
        val installed = (result as LoadResult.Success).extension
        Assert.assertEquals("Aniyomi: SampleSource", installed.name)
        Assert.assertEquals("com.example.animeextension", installed.pkgName)
        Assert.assertEquals(1, installed.sources.size)
    }

    @Test
    fun `loadExtension returns Untrusted with wrong trust entry`() {
        val badHash = "0000000000000000000000000000000000000000000000000000000000000000"
        val trustEntry = MacOSExtensionLoader.TrustEntry(
            pkgName = "com.example.animeextension",
            versionCode = 100L,
            signatureHash = badHash,
        )

        val result = MacOSExtensionLoader.loadExtension(
            jarFile = jarFile,
            trustStore = mapOf("com.example.animeextension" to listOf(trustEntry)),
        )

        Assert.assertTrue("Expected Untrusted with wrong hash", result is LoadResult.Untrusted)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `loaded source returns hardcoded sample data`() = runBlocking {
        // Load the extension with trust
        val hash = MacOSExtensionLoader.computeSha256(jarFile)
        val trustEntry = MacOSExtensionLoader.TrustEntry(
            pkgName = "com.example.animeextension",
            versionCode = 100L,
            signatureHash = hash,
        )

        val result = MacOSExtensionLoader.loadExtension(
            jarFile = jarFile,
            trustStore = mapOf("com.example.animeextension" to listOf(trustEntry)),
        )

        Assert.assertTrue("Expected Success", result is LoadResult.Success)
        source = (result as LoadResult.Success).extension.sources.first() as CatalogueSource

        // Verify source identity
        Assert.assertEquals(999002L, source.id)
        Assert.assertEquals("SampleSource", source.name)
        Assert.assertEquals("en", source.lang)

        // getPopularAnime
        val popularPage = source.getPopularAnime(page = 1)
        Assert.assertEquals("Should return 5 sample anime", 5, popularPage.animes.size)
        Assert.assertEquals(false, popularPage.hasNextPage)
        Assert.assertEquals("Starlight Adventures", popularPage.animes[0].title)
        Assert.assertEquals("Cyber Frontier", popularPage.animes[1].title)
        Assert.assertEquals("Whisker Chronicles", popularPage.animes[2].title)
        Assert.assertEquals("Crimson Samurai", popularPage.animes[3].title)
        Assert.assertEquals("Neon Dreams", popularPage.animes[4].title)

        // getAnimeDetails
        val anime = popularPage.animes[0]
        val details = source.getAnimeDetails(anime)
        Assert.assertEquals(anime.url, details.url)
        Assert.assertEquals("Starlight Adventures", details.title)
        Assert.assertEquals("Akira Nakamura", details.author)
        Assert.assertEquals("Mariko Tanaka", details.artist)
        Assert.assertTrue(details.description!!.startsWith("In a world where stars"))
        Assert.assertEquals("Adventure, Fantasy, Magic", details.genre)
        Assert.assertEquals(eu.kanade.tachiyomi.animesource.model.SAnime.ONGOING, details.status)
        Assert.assertTrue(details.initialized)

        // getEpisodeList
        val episodes = source.getEpisodeList(details)
        Assert.assertEquals("Should return 12 episodes", 12, episodes.size)
        Assert.assertEquals("Episode 1", episodes[0].name)
        Assert.assertEquals(1f, episodes[0].episode_number, 0.001f)
        Assert.assertEquals("Episode 12", episodes[11].name)
        Assert.assertEquals(12f, episodes[11].episode_number, 0.001f)

        // getVideoList
        val videos = source.getVideoList(episodes[0])
        Assert.assertEquals("Should return 2 video qualities", 2, videos.size)
        Assert.assertEquals("1080p", videos[0].videoTitle)
        Assert.assertTrue(videos[0].videoUrl.contains("BigBuckBunny.mp4"))
        Assert.assertTrue(videos[0].preferred)
        Assert.assertEquals("720p", videos[1].videoTitle)
        Assert.assertTrue(videos[1].videoUrl.contains("ElephantsDream.mp4"))
        Assert.assertFalse(videos[1].preferred)

        // getSearchAnime — match by title
        val searchResult = source.getSearchAnime(page = 1, query = "samurai", filters = AnimeFilterList())
        Assert.assertEquals(1, searchResult.animes.size)
        Assert.assertEquals("Crimson Samurai", searchResult.animes[0].title)

        // getSearchAnime — match by author
        val searchByAuthor = source.getSearchAnime(page = 1, query = "Hana", filters = AnimeFilterList())
        Assert.assertEquals(1, searchByAuthor.animes.size)
        Assert.assertEquals("Whisker Chronicles", searchByAuthor.animes[0].title)

        // getSearchAnime — no matches
        val searchEmpty = source.getSearchAnime(page = 1, query = "nonexistent", filters = AnimeFilterList())
        Assert.assertEquals(0, searchEmpty.animes.size)
    }
}
