package app.anikku.macos.platform.extension

import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.storage.MacOSStorageProvider
import eu.kanade.tachiyomi.extension.model.InstallStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Integration tests for the extension repository fetch and download pipeline.
 *
 * Tests:
 * 1. Full download pipeline using MockWebServer to simulate a repo
 * 2. End-to-end: fetch index → download → install → trust → browse
 * 3. Progress callback verification
 * 4. Real keiyoushi index parsing (network-dependent, resilient)
 *
 * Each test creates its own [MacOSExtensionManager] and temp directory
 * for full isolation.
 */
class ExtensionRepoIntegrationTest {

    private val sampleJarPath = "sample-extension/build/libs/sample-extension-1.0.0.jar"

    /** Build a real JAR fixture whose signed repository metadata agrees with the index entry. */
    private fun matchingJar(
        baseJar: File,
        directory: File,
        fileName: String,
        packageName: String,
        versionCode: Long,
        libVersion: Double,
        lang: String = "en",
        nsfw: Boolean = false,
    ): File {
        val output = File(directory, fileName)
        val metadata = """
            {
              "name": "Aniyomi: TestExtension",
              "pkgName": "$packageName",
              "versionName": "1.0.0",
              "versionCode": $versionCode,
              "libVersion": $libVersion,
              "lang": "$lang",
              "isNsfw": $nsfw,
              "isTorrent": false,
              "sourceClass": "$packageName.SampleAnimeSource",
              "pkgFactory": null,
              "hasReadme": false,
              "hasChangelog": false
            }
        """.trimIndent().toByteArray()

        JarFile(baseJar).use { input ->
            JarOutputStream(output.outputStream()).use { jar ->
                input.entries().asSequence().forEach { entry ->
                    if (entry.name == "META-INF/extension.json") return@forEach
                    jar.putNextEntry(JarEntry(entry.name))
                    if (!entry.isDirectory) input.getInputStream(entry).use { it.copyTo(jar) }
                    jar.closeEntry()
                }
                jar.putNextEntry(JarEntry("META-INF/extension.json"))
                jar.write(metadata)
                jar.closeEntry()
            }
        }
        return output
    }

    /**
     * Creates an isolated [MacOSExtensionManager] backed by a temp directory.
     */
    private fun createIsolatedManager(): Pair<MacOSExtensionManager, File> {
        val tempDir = createTempDir("anikku-repo-test-")
        File(tempDir, "extensions").mkdirs()
        File(tempDir, "trust").mkdirs()

        val storageProvider = object : MacOSStorageProvider() {
            override fun directory(): File = tempDir
        }
        val networkHelper = MacOSNetworkHelper(storageProvider = storageProvider)
        val manager = MacOSExtensionManager(storageProvider, networkHelper)
        return manager to tempDir
    }

    /**
     * Sets up a MockWebServer to simulate an extension repository.
     * Serves index.json + our pre-built sample extension JAR.
     */
    private fun setupMockRepo(server: MockWebServer, jarFile: File, packageName: String, sourceId: Long) {
        val indexJson = """
        [{
          "name": "Aniyomi: TestExtension",
          "pkg": "$packageName",
          "apk": "${jarFile.name}",
          "lang": "en",
          "code": 100,
          "version": "14.1",
          "nsfw": 0,
          "sources": [{"id": $sourceId, "lang": "en", "name": "TestSource", "baseUrl": "https://test.com"}]
        }]
        """.trimIndent()

        server.enqueue(MockResponse().apply {
            setResponseCode(200)
            setHeader("Content-Type", "application/json")
            setBody(indexJson)
        })
        server.enqueue(MockResponse().apply {
            setResponseCode(200)
            setHeader("Content-Type", "application/java-archive")
            setHeader("Content-Length", jarFile.length().toString())
            setBody(okio.Buffer().write(jarFile.readBytes()))
        })
    }

    // ── MockWebServer tests (isolated, no network dependency) ──────

    /**
     * Verifies that the libVersion filter correctly accepts version `"14.1"`.
     * The index format version `"14.1"` must produce `libVersion = 14.0`
     * via [MacOSExtensionManager]'s internal [extractLibVersion] logic
     * (which does `version.substringBeforeLast('.').toDouble()`).
     *
     * Version `"14.1"` → `14.0` passes the `>= 12 && <= 15` filter.
     * Previously we used `"1.0.0"` → `1.0` which failed the filter.
     */
    @Test
    fun `download with progress callback reports all steps`() = runBlocking {
        val sampleJar = File(sampleJarPath)
        Assert.assertTrue("Sample JAR not built: cd macos/sample-extension && ./gradlew buildExtensionJar", sampleJar.exists())
        val fixtureDir = createTempDir("anikku-progress-fixture-")
        val servedJar = matchingJar(
            sampleJar, fixtureDir, "progress.jar", "com.example.progresstest", 100L, 14.0,
        )

        val server = MockWebServer().apply { start(0) }
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            setupMockRepo(server, servedJar, "com.example.progresstest", 999003L)

            val (testManager, tempDir) = createIsolatedManager()
            try {
                val extensions = testManager.findAvailableExtensions(baseUrl, force = true)
                Assert.assertEquals("Should find 1 extension", 1, extensions.size)

                // Verify the index request path
                val indexRequest = server.takeRequest()
                Assert.assertEquals("/index.min.json", indexRequest.path)

                var progressValues = mutableListOf<Float>()
                var completedSteps = mutableListOf<String>()

                testManager.installExtension(extensions.first()) { step ->
                    when (step) {
                        is InstallStep.Downloading -> progressValues.add(step.progress)
                        is InstallStep.Installing -> completedSteps.add("installing")
                        is InstallStep.Complete -> completedSteps.add("complete")
                        is InstallStep.Error -> completedSteps.add("error: ${step.message}")
                    }
                }

                // Verify the JAR download request path — pre-converted JAR repos
                // serve files at the root, not under /apk/
                val jarRequest = server.takeRequest()
                Assert.assertEquals("/${servedJar.name}", jarRequest.path)

                Assert.assertTrue("Should track progress", progressValues.isNotEmpty())
                Assert.assertTrue("Progress should reach near 1.0", progressValues.last() >= 0.9f)
                Assert.assertTrue("Should report Installing", completedSteps.contains("installing"))
                Assert.assertTrue("Should report Complete", completedSteps.contains("complete"))
                Assert.assertFalse("Should not report Error", completedSteps.any { it.startsWith("error") })
            } finally {
                testManager.close()
                tempDir.deleteRecursively()
            }
        } finally {
            server.shutdown()
            fixtureDir.deleteRecursively()
        }
    }

    @Test
    fun `full pipeline fetch download install trust browse`() = runBlocking {
        val sampleJar = File(sampleJarPath)
        Assert.assertTrue("Sample JAR not built", sampleJar.exists())

        val server = MockWebServer().apply { start(0) }
        try {
            val baseUrl = server.url("").toString().trimEnd('/')
            setupMockRepo(server, sampleJar, "com.example.animeextension", 999005L)

            val (testManager, tempDir) = createIsolatedManager()
            try {
                // Fetch index
                val extensions = testManager.findAvailableExtensions(baseUrl, force = true)
                Assert.assertEquals("Should find 1 extension", 1, extensions.size)
                Assert.assertEquals("com.example.animeextension", extensions.first().pkgName)

                // Download & install
                testManager.installExtension(extensions.first())

                // Appears as untrusted (wait for stateIn to propagate)
                val untrusted = testManager.untrustedExtensionsFlow.first { it.isNotEmpty() }
                Assert.assertEquals("Should be untrusted", 1, untrusted.size)
                Assert.assertEquals("com.example.animeextension", untrusted.first().pkgName)

                // Trust it
                testManager.trustExtension(untrusted.first())

                // Moves to installed (wait for stateIn to propagate)
                val installed = testManager.installedExtensionsFlow.first { it.isNotEmpty() }
                Assert.assertEquals("Should be installed", 1, installed.size)
                Assert.assertEquals("com.example.animeextension", installed.first().pkgName)
                Assert.assertEquals(1, installed.first().sources.size)

                // Findable by source ID (use the actual source ID from the loaded extension)
                val sourceId = installed.first().sources.first().id
                val source = testManager.getSource(sourceId)
                Assert.assertNotNull("Source findable by ID", source)
                Assert.assertEquals("SampleSource", source!!.name)

                // Shows up in source list (as BrowseTab would use it)
                val allSources = testManager.installedExtensionsFlow.first { it.isNotEmpty() }.flatMap { it.sources }
                Assert.assertEquals("Should have 1 source in list", 1, allSources.size)
            } finally {
                testManager.close()
                tempDir.deleteRecursively()
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `multiple extensions in repo all install correctly`() = runBlocking {
        val sampleJar = File(sampleJarPath)
        Assert.assertTrue("Sample JAR not built", sampleJar.exists())
        val fixtureDir = createTempDir("anikku-multiple-fixture-")
        val extAJar = matchingJar(sampleJar, fixtureDir, "ext-a.jar", "com.example.exta", 100L, 14.0)
        val extBJar = matchingJar(sampleJar, fixtureDir, "ext-b.jar", "com.example.extb", 101L, 13.0, "ja", true)

        val server = MockWebServer().apply { start(0) }
        try {
            val baseUrl = server.url("").toString().trimEnd('/')

            // Index with 2 extensions
            val indexJson = """
            [
              {"name": "Aniyomi: ExtA", "pkg": "com.example.exta", "apk": "ext-a.jar", "lang": "en", "code": 100, "version": "14.1", "nsfw": 0, "sources": [{"id": 999010, "lang": "en", "name": "ExtA", "baseUrl": "https://a.test"}]},
              {"name": "Aniyomi: ExtB", "pkg": "com.example.extb", "apk": "ext-b.jar", "lang": "ja", "code": 101, "version": "13.0", "nsfw": 1, "sources": [{"id": 999011, "lang": "ja", "name": "ExtB", "baseUrl": "https://b.test"}]}
            ]
            """.trimIndent()
            server.enqueue(MockResponse().apply { setResponseCode(200); setBody(indexJson) })
            server.enqueue(MockResponse().apply { setResponseCode(200); setBody(okio.Buffer().write(extAJar.readBytes())) })
            server.enqueue(MockResponse().apply { setResponseCode(200); setBody(okio.Buffer().write(extBJar.readBytes())) })

            val (testManager, tempDir) = createIsolatedManager()
            try {
                val extensions = testManager.findAvailableExtensions(baseUrl, force = true)
                Assert.assertEquals("Should find 2 extensions", 2, extensions.size)

                // Verify metadata for both
                val extA = extensions.find { it.pkgName == "com.example.exta" }!!
                Assert.assertEquals("ExtA", extA.name)
                Assert.assertEquals("en", extA.lang)
                Assert.assertEquals(100L, extA.versionCode)
                Assert.assertEquals(14.0, extA.libVersion, 0.001)
                Assert.assertEquals(1, extA.sources.size)

                val extB = extensions.find { it.pkgName == "com.example.extb" }!!
                Assert.assertEquals("ExtB", extB.name)
                Assert.assertEquals("ja", extB.lang)
                Assert.assertEquals(101L, extB.versionCode)
                Assert.assertEquals(13.0, extB.libVersion, 0.001)
                Assert.assertTrue("ExtB should be NSFW", extB.isNsfw)

                // Install both
                testManager.installExtension(extA)
                testManager.installExtension(extB)

                // Wait for stateIn to propagate after both installs
                val untrusted = testManager.untrustedExtensionsFlow.first { it.size >= 2 }
                Assert.assertEquals("Both should be untrusted", 2, untrusted.size)
            } finally {
                testManager.close()
                tempDir.deleteRecursively()
            }
        } finally {
            server.shutdown()
            fixtureDir.deleteRecursively()
        }
    }

    @Test
    fun `legacy APK repo downloads from apk subdirectory`() = runBlocking {
        val sampleJar = File(sampleJarPath)
        Assert.assertTrue("Sample JAR not built", sampleJar.exists())

        val server = MockWebServer().apply { start(0) }
        try {
            val baseUrl = server.url("").toString().trimEnd('/')

            // Index with a legacy .apk extension
            val indexJson = """
            [{
              "name": "Aniyomi: LegacyApk",
              "pkg": "com.example.legacyapk",
              "apk": "legacy.apk",
              "lang": "en",
              "code": 100,
              "version": "14.1",
              "nsfw": 0,
              "sources": [{"id": 999020, "lang": "en", "name": "Legacy", "baseUrl": "https://legacy.test"}]
            }]
            """.trimIndent()
            server.enqueue(MockResponse().apply { setResponseCode(200); setBody(indexJson) })
            server.enqueue(MockResponse().apply {
                setResponseCode(200)
                setHeader("Content-Type", "application/octet-stream")
                setBody(okio.Buffer().write(sampleJar.readBytes()))
            })

            val (testManager, tempDir) = createIsolatedManager()
            try {
                val extensions = testManager.findAvailableExtensions(baseUrl, force = true)
                Assert.assertEquals("Should find 1 extension", 1, extensions.size)

                val indexRequest = server.takeRequest()
                Assert.assertEquals("/index.min.json", indexRequest.path)

                var errorMessage: String? = null
                testManager.installExtension(extensions.first()) { step ->
                    if (step is InstallStep.Error) {
                        errorMessage = step.message
                    }
                }

                // The download should hit /apk/ because the index entry ends with .apk
                val apkRequest = server.takeRequest()
                Assert.assertEquals("/apk/legacy.apk", apkRequest.path)

                // Without jadx installed, installation should report an error
                Assert.assertNotNull("Should report an error for APK without jadx", errorMessage)
                Assert.assertTrue(
                    "Error should mention jadx or pre-converted JAR repo",
                    errorMessage.orEmpty().contains("jadx", ignoreCase = true) ||
                        errorMessage.orEmpty().contains("pre-converted", ignoreCase = true)
                )
            } finally {
                testManager.close()
                tempDir.deleteRecursively()
            }
        } finally {
            server.shutdown()
        }
    }

    // ── Real keiyoushi index tests (network-dependent) ────────────

    @Test
    fun `fetch anime extension index from macOS JAR repo`() = runBlocking {
        val (testManager, tempDir) = createIsolatedManager()
        try {
            // Use the Anikku macOS Extensions JAR repo (anime extensions pre-converted for JVM)
            val repoUrl = "https://raw.githubusercontent.com/ErnestHysa/anikku-extensions-jar/main/"
            val extensions = testManager.findAvailableExtensions(repoUrl, force = true)

            if (extensions.isEmpty()) {
                // Network may be unavailable — skip assertions gracefully
                println("WARNING: macOS JAR repo returned empty — network may be unavailable")
                return@runBlocking
            }

            Assert.assertTrue("Should fetch anime extensions", extensions.size >= 1)

            val sample = extensions.first()
            Assert.assertNotNull("Name", sample.name)
            Assert.assertNotNull("pkgName", sample.pkgName)
            Assert.assertNotNull("versionName", sample.versionName)
            Assert.assertTrue("versionCode > 0", sample.versionCode > 0)
            Assert.assertTrue("libVersion >= 12", sample.libVersion >= 12.0)
            Assert.assertTrue("libVersion <= 15", sample.libVersion <= 15.0)
            Assert.assertNotNull("apkName", sample.apkName)

            val allInRange = extensions.all { it.libVersion in 12.0..15.0 }
            Assert.assertTrue("All libVersions in [12, 15]", allInRange)
        } finally {
            testManager.close()
            tempDir.deleteRecursively()
        }
    }
}
