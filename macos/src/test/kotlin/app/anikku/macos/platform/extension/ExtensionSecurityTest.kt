package app.anikku.macos.platform.extension

import app.anikku.macos.platform.network.MacOSNetworkHelper
import app.anikku.macos.platform.storage.MacOSStorageProvider
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.model.LoadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class ExtensionSecurityTest {

    private val resourcesJar = File("build/libs/test-extension-1.0.0.jar")
    private val packageName = "app.anikku.macos.testextension"
    private val sourceId = 999001L
    private val openManagers = mutableListOf<MacOSExtensionManager>()

    @AfterEach
    fun tearDown() {
        openManagers.forEach { it.close() }
        openManagers.clear()
    }

    @Test
    fun `empty trust store leaves a valid artifact untrusted`() {
        assertTrue(resourcesJar.isFile, "Test extension JAR must be built")

        val result = MacOSExtensionLoader.loadExtension(resourcesJar, trustStore = emptyMap())

        assertTrue(result is LoadResult.Untrusted)
    }

    @Test
    fun `malformed metadata and unsupported versions are rejected`() {
        val malformed = createJar(
            "{\"name\":\"Bad\",\"pkgName\":\"../escape\",\"versionName\":\"1\",\"versionCode\":1,\"libVersion\":14.0,\"sourceClass\":\"Bad\"}",
        )
        val unsupported = createJar(
            "{\"name\":\"Bad\",\"pkgName\":\"com.example.bad\",\"versionName\":\"1\",\"versionCode\":1,\"libVersion\":99.0,\"sourceClass\":\"com.example.Bad\"}",
        )

        assertEquals(null, MacOSExtensionLoader.readMetadata(malformed))
        assertEquals(LoadResult.Error, MacOSExtensionLoader.loadExtension(unsupported))
    }

    @Test
    fun `archive traversal absolute and symlink entries are rejected`() {
        val traversal = createJar(entryName = "../outside.txt")
        val absolute = createJar(entryName = "/absolute.txt")
        val symlink = createJar(entryName = "link", patchAsUnixSymlink = true)

        assertFalse(MacOSExtensionLoader.readMetadata(traversal) != null)
        assertFalse(MacOSExtensionLoader.readMetadata(absolute) != null)
        assertFalse(MacOSExtensionLoader.readMetadata(symlink) != null)
    }

    @Test
    fun `filesystem symlink artifacts are not loaded`() {
        val tempDir = Files.createTempDirectory("anikku-extension-link-").toFile()
        val link = File(tempDir, "linked.jar")
        Files.createSymbolicLink(link.toPath(), resourcesJar.toPath())

        assertEquals(null, MacOSExtensionLoader.readMetadata(link))
        tempDir.deleteRecursively()
    }

    @Test
    fun `duplicate source ID is rejected during load`() {
        assertTrue(resourcesJar.isFile, "Test extension JAR must be built")
        val hash = MacOSExtensionLoader.computeSha256(resourcesJar)
        val result = MacOSExtensionLoader.loadExtension(
            jarFile = resourcesJar,
            trustStore = mapOf(packageName to listOf(
                MacOSExtensionLoader.TrustEntry(packageName, 100L, hash),
            )),
            occupiedSourceIds = mapOf(sourceId to "other.extension"),
        )

        assertEquals(LoadResult.Error, result)
    }

    @Test
    fun `duplicate package artifacts are all rejected before code is loaded`() {
        assertTrue(resourcesJar.isFile, "Test extension JAR must be built")
        val tempDir = Files.createTempDirectory("anikku-extension-duplicates-").toFile()
        val first = File(tempDir, "a.jar")
        val second = File(tempDir, "b.jar")
        resourcesJar.copyTo(first)
        resourcesJar.copyTo(second)
        val hash = MacOSExtensionLoader.computeSha256(first)

        try {
            val results = MacOSExtensionLoader.loadExtensions(
                extensionsDir = tempDir,
                trustStore = mapOf(
                    packageName to listOf(
                        MacOSExtensionLoader.TrustEntry(packageName, 100L, hash),
                    ),
                ),
            )

            assertEquals(2, results.size)
            assertTrue(results.all { it == LoadResult.Error })
        } finally {
            MacOSExtensionLoader.closeAll()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `failed replacement preserves the trusted artifact and installed state`() = runBlocking {
        assertTrue(resourcesJar.isFile, "Test extension JAR must be built")
        val tempDir = Files.createTempDirectory("anikku-extension-rollback-").toFile()
        val extensionsDir = File(tempDir, "extensions").apply { mkdirs() }
        val trustDir = File(tempDir, "data/trust").apply { mkdirs() }
        val installedJar = File(extensionsDir, "$packageName.jar")
        resourcesJar.copyTo(installedJar)
        val originalBytes = installedJar.readBytes()
        val originalHash = MacOSExtensionLoader.computeSha256(installedJar)
        trustDir.resolve("trusted_extensions.json").writeText(
            "[{\"pkgName\":\"$packageName\",\"versionCode\":100,\"signatureHash\":\"$originalHash\"}]",
        )

        val replacement = createJar(
            metadata = "{\"name\":\"Test\",\"pkgName\":\"$packageName\",\"versionName\":\"2.0.0\",\"versionCode\":101,\"libVersion\":14.0,\"sourceClass\":\"$packageName.MissingSource\"}",
        )
        val server = MockWebServer().apply { start(0) }
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(replacement.readBytes())))
            val storage = object : MacOSStorageProvider() {
                override fun directory(): File = tempDir
            }
            val manager = MacOSExtensionManager(storage, MacOSNetworkHelper(storage))
            openManagers += manager

            assertEquals(1, manager.installedExtensionsFlow.first { it.size == 1 }.size)
            val available = Extension.Available(
                name = "Test",
                pkgName = packageName,
                versionName = "2.0.0",
                versionCode = 101L,
                libVersion = 14.0,
                lang = "en",
                isNsfw = false,
                isTorrent = false,
                sources = emptyList(),
                apkName = "replacement.jar",
                iconUrl = server.url("icon").toString(),
                repoUrl = server.url("").toString().trimEnd('/'),
            )
            val steps = mutableListOf<InstallStep>()

            manager.installExtension(available) { steps += it }

            assertTrue(steps.last() is InstallStep.Error)
            assertArrayEquals(originalBytes, installedJar.readBytes())
            assertEquals(packageName, manager.installedExtensionsFlow.first { it.size == 1 }.single().pkgName)
        } finally {
            server.shutdown()
            replacement.delete()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `manager removes extension and closes lifecycle state`() = runBlocking {
        assertTrue(resourcesJar.isFile, "Test extension JAR must be built")
        val tempDir = Files.createTempDirectory("anikku-extension-manager-").toFile()
        val extensionsDir = File(tempDir, "extensions").apply { mkdirs() }
        val trustDir = File(tempDir, "data/trust").apply { mkdirs() }
        val installedJar = File(extensionsDir, "$packageName.jar")
        resourcesJar.copyTo(installedJar)
        val hash = MacOSExtensionLoader.computeSha256(installedJar)
        trustDir.resolve("trusted_extensions.json").writeText(
            "[{\"pkgName\":\"$packageName\",\"versionCode\":100,\"signatureHash\":\"$hash\"}]",
        )

        val storage = object : MacOSStorageProvider() {
            override fun directory(): File = tempDir
        }
        val manager = MacOSExtensionManager(storage, MacOSNetworkHelper(storage))
        openManagers += manager

        val installed = manager.installedExtensionsFlow.first { it.isNotEmpty() }
        assertEquals(packageName, installed.single().pkgName)
        manager.removeExtension(installed.single())

        assertTrue(manager.installedExtensionsFlow.first().isEmpty())
        assertFalse(installedJar.exists())
        tempDir.deleteRecursively()
    }

    @Test
    fun `reload update and removal publish current source instances`() = runBlocking {
        assertTrue(resourcesJar.isFile, "Test extension JAR must be built")
        val tempDir = Files.createTempDirectory("anikku-extension-refresh-").toFile()
        val extensionsDir = File(tempDir, "extensions").apply { mkdirs() }
        val trustDir = File(tempDir, "data/trust").apply { mkdirs() }
        val installedJar = File(extensionsDir, "$packageName.jar")
        resourcesJar.copyTo(installedJar)
        val updatedJar = copyWithMetadata(
            resourcesJar,
            """
            {
              "name": "Aniyomi: TestSource",
              "pkgName": "$packageName",
              "versionName": "1.0.1",
              "versionCode": 101,
              "libVersion": 14.0,
              "lang": "en",
              "isNsfw": false,
              "isTorrent": false,
              "sourceClass": "$packageName.TestAnimeSource"
            }
            """.trimIndent(),
        )
        val originalHash = MacOSExtensionLoader.computeSha256(installedJar)
        val updatedHash = MacOSExtensionLoader.computeSha256(updatedJar)
        trustDir.resolve("trusted_extensions.json").writeText(
            """[
              {"pkgName":"$packageName","versionCode":100,"signatureHash":"$originalHash"},
              {"pkgName":"$packageName","versionCode":101,"signatureHash":"$updatedHash"}
            ]""".trimIndent(),
        )

        val server = MockWebServer().apply { start(0) }
        try {
            val storage = object : MacOSStorageProvider() {
                override fun directory(): File = tempDir
            }
            val manager = MacOSExtensionManager(storage, MacOSNetworkHelper(storage))
            openManagers += manager
            val initial = manager.installedExtensionsFlow.first { it.size == 1 }.single()
            val initialSource = initial.sources.single()
            assertEquals(initialSource, manager.getSource(sourceId))

            assertTrue(manager.reloadExtension(packageName))
            val reloaded = manager.installedExtensionsFlow.first {
                it.singleOrNull()?.sources?.singleOrNull() !== initialSource
            }.single()
            val reloadedSource = reloaded.sources.single()
            assertNotSame(initialSource, reloadedSource)
            assertEquals(reloadedSource, manager.getSource(sourceId))

            val baseUrl = server.url("").toString().trimEnd('/')
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[{
                      "name":"Aniyomi: TestSource",
                      "pkg":"$packageName",
                      "apk":"replacement.jar",
                      "lang":"en",
                      "code":101,
                      "version":"14.1",
                      "sha256":"$updatedHash",
                      "sources":[{"id":$sourceId,"lang":"en","name":"TestSource","baseUrl":"https://example.invalid"}]
                    }]""".trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody(okio.Buffer().write(updatedJar.readBytes())),
            )

            val available = manager.findAvailableExtensions(baseUrl, force = true)
            assertEquals(1, available.size)
            val beforeUpdate = manager.installedExtensionsFlow.first {
                it.singleOrNull()?.hasUpdate == true
            }.single()
            assertTrue(beforeUpdate.hasUpdate)
            val steps = mutableListOf<InstallStep>()
            manager.updateExtension(beforeUpdate) { steps += it }

            assertTrue(steps.last() is InstallStep.Complete)
            val updated = manager.installedExtensionsFlow.first {
                it.singleOrNull()?.versionCode == 101L
            }.single()
            val updatedSource = updated.sources.single()
            assertEquals(101L, updated.versionCode)
            assertNotSame(reloadedSource, updatedSource)
            assertEquals(updatedSource, manager.getSource(sourceId))

            manager.removeExtension(updated)
            assertTrue(manager.installedExtensionsFlow.first { it.isEmpty() }.isEmpty())
            assertNull(manager.getSource(sourceId))
        } finally {
            server.shutdown()
            updatedJar.delete()
            tempDir.deleteRecursively()
        }
    }

    private fun createJar(
        metadata: String = "{\"name\":\"Test\",\"pkgName\":\"com.example.test\",\"versionName\":\"1\",\"versionCode\":1,\"libVersion\":14.0,\"sourceClass\":\"com.example.Test\"}",
        entryName: String = "META-INF/extension.json",
        patchAsUnixSymlink: Boolean = false,
    ): File {
        val file = Files.createTempFile("anikku-security-", ".jar").toFile()
        JarOutputStream(file.outputStream()).use { jar ->
            jar.putNextEntry(JarEntry(entryName))
            jar.write(if (entryName.endsWith("json")) metadata.toByteArray() else byteArrayOf(1, 2, 3))
            jar.closeEntry()
        }
        if (patchAsUnixSymlink) patchCentralDirectoryAsSymlink(file, entryName)
        return file
    }

    private fun copyWithMetadata(source: File, metadata: String): File {
        val destination = Files.createTempFile("anikku-extension-update-", ".jar").toFile()
        JarFile(source).use { input ->
            JarOutputStream(destination.outputStream()).use { output ->
                input.entries().asSequence().forEach { entry ->
                    output.putNextEntry(JarEntry(entry.name))
                    if (!entry.isDirectory) {
                        if (entry.name == "META-INF/extension.json") {
                            output.write(metadata.toByteArray())
                        } else {
                            input.getInputStream(entry).use { it.copyTo(output) }
                        }
                    }
                    output.closeEntry()
                }
            }
        }
        return destination
    }

    private fun patchCentralDirectoryAsSymlink(file: File, entryName: String) {
        val bytes = file.readBytes()
        val signature = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
        for (offset in 0..bytes.size - 46) {
            if (!bytes.copyOfRange(offset, offset + 4).contentEquals(signature)) continue
            val nameLength = readLeShort(bytes, offset + 28)
            val name = bytes.copyOfRange(offset + 46, offset + 46 + nameLength).toString(Charsets.UTF_8)
            if (name != entryName) continue
            writeLeShort(bytes, offset + 4, 0x0314)
            writeLeInt(bytes, offset + 38, 0xA0000000.toInt())
            file.writeBytes(bytes)
            return
        }
        error("Central-directory entry not found")
    }

    private fun readLeShort(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun writeLeShort(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeLeInt(bytes: ByteArray, offset: Int, value: Int) {
        for (index in 0 until 4) bytes[offset + index] = (value ushr (index * 8)).toByte()
    }
}
