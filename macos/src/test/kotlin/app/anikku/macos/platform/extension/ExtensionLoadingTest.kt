package app.anikku.macos.platform.extension

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader

/**
 * Tests that a test extension JAR (compiled against source-api) can be loaded
 * via URLClassLoader and wrapped using [ReflectiveSourceProxy].
 *
 * This verifies the end-to-end pipeline:
 * 1. Load extension .jar via URLClassLoader
 * 2. Call `Class.forName()` on the extension's main class
 * 3. Instantiate the source
 * 4. Wrap it via [wrapAsSource]
 * 5. Call methods through the reflection bridge
 */
class ExtensionLoadingTest {

    private fun loadExtensionInstance(): Any {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        // When running with `-p macos`, the working directory is already `macos/`.
        // When running from the root, it's the project root.
        val buildDir = if (projectDir.name == "macos") {
            File(projectDir, "build/libs")
        } else {
            File(projectDir, "macos/build/libs")
        }
        val jarFile = File(buildDir, "test-extension-1.0.0.jar")
        assertTrue(jarFile.isFile, "Test extension JAR must exist at ${jarFile.absolutePath}. Run: ./macos/gradlew -p macos buildTestExtensionJar")

        val classLoader = URLClassLoader(
            arrayOf(jarFile.toURI().toURL()),
            this::class.java.classLoader,
        )

        val clazz = Class.forName(
            "app.anikku.macos.testextension.TestAnimeSource",
            true,
            classLoader,
        )
        return clazz.getDeclaredConstructor().newInstance()
    }

    @Test
    fun `extension JAR can be loaded and wrapped via wrapAsSource`() {
        val instance = loadExtensionInstance()
        val source = wrapAsSource(instance)

        assertNotNull(source, "wrapAsSource must return a CatalogueSource")
        assertTrue(source!!.id > 0, "Source should have a valid id")
        assertTrue(source.name.isNotBlank(), "Source should have a name")
        assertTrue(source.lang.isNotBlank(), "Source should have a language")
    }

    @Test
    fun `reflective proxy can call getAnimeDetails via suspend API`() = runBlocking {
        val instance = loadExtensionInstance()
        val source = wrapAsSource(instance)!!

        // Create an SAnime (same classloader as test, so direct call works)
        val anime = SAnime.create().apply { url = "/test/episode/1" }

        // Call through the reflection bridge
        val result = source.getAnimeDetails(anime)

        assertEquals("Big Buck Bunny", result.title)
        assertTrue(result.initialized)
    }

    @Test
    fun `reflective proxy can call getEpisodeList`() = runBlocking {
        val instance = loadExtensionInstance()
        val source = wrapAsSource(instance)!!

        val anime = SAnime.create().apply { url = "/test/episode/1" }
        val details = source.getAnimeDetails(anime)
        val episodes = source.getEpisodeList(details)

        assertEquals(1, episodes.size)
        assertEquals(1f, episodes[0].episode_number)
        assertEquals("Big Buck Bunny", episodes[0].name)
    }

    @Test
    fun `reflective proxy can call getVideoList`() = runBlocking {
        val instance = loadExtensionInstance()
        val source = wrapAsSource(instance)!!

        val anime = SAnime.create().apply { url = "/test/episode/1" }
        val details = source.getAnimeDetails(anime)
        val episode = source.getEpisodeList(details).first()
        val videos = source.getVideoList(episode)

        assertEquals(1, videos.size)
        assertEquals("1080p", videos[0].videoTitle)
        assertTrue(videos[0].videoUrl.contains("BigBuckBunny.mp4"))
    }

    @Test
    fun `reflective proxy handles getPopularAnime and getSearchAnime`() = runBlocking {
        val instance = loadExtensionInstance()
        val source = wrapAsSource(instance)!!

        // getPopularAnime
        val popular = source.getPopularAnime(page = 1)
        assertTrue(popular.animes.isNotEmpty())

        // getSearchAnime with filters
        val searchResult = source.getSearchAnime(
            page = 1,
            query = "",
            filters = AnimeFilterList(),
        )
        assertNotNull(searchResult)
    }
}
