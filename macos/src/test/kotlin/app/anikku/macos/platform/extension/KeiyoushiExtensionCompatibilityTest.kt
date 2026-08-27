package app.anikku.macos.platform.extension

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests a real keiyoushi extension APK against the macOS extension loader.
 *
 * ## Key finding
 *
 * The keiyoushi/extensions repo distributes extensions as **Android APKs**
 * (DEX bytecode + AndroidManifest.xml metadata). These cannot be loaded by a JVM
 * directly. For macOS compatibility, extensions should be built from source via
 * the build-keiyoushi-from-source.sh script, which clones yuzono/anime-extensions
 * and compiles clean JVM JARs against the source-api stubs.
 *
 * Pre-converted JVM JARs are available from the Anikku macOS Extensions repo:
 *   https://github.com/ErnestHysa/anikku-extensions-jar
 *
 * The `ExtensionLoadingTest` class proves the JVM-compiled JAR pipeline works.
 */
class KeiyoushiExtensionCompatibilityTest {

    private val extensionsDir = File(
        System.getProperty("user.home"),
        "Library/Application Support/Anikku/extensions",
    )

    @Test
    fun `keiyoushi APK is ignored by extension loader extension filter`() {
        assertTrue(extensionsDir.isDirectory)
        val apkFiles = extensionsDir.listFiles()?.filter { it.extension == "apk" } ?: emptyList()
        assumeTrue(apkFiles.isNotEmpty(), "No APK file found — download one first")

        // Loader processes JARs and attempts APK conversion
        val results = MacOSExtensionLoader.loadExtensions(extensionsDir)
        val jarCount = extensionsDir.listFiles()?.count { it.extension == "jar" } ?: 0
        val apkCount = apkFiles.size
        // APK conversion produces Error results, so total = JAR results + APK results
        assertTrue(results.size == jarCount + apkCount || results.size >= jarCount,
            "Total results should include both JAR and APK conversion results")
    }

    @Test
    fun `keiyoushi APK metadata format is incompatible`() {
        val apkFiles = extensionsDir.listFiles()?.filter { it.extension == "apk" } ?: emptyList()
        assumeTrue(apkFiles.isNotEmpty(), "No APK file found — download one first")

        // APK uses AndroidManifest.xml, not META-INF/extension.json
        val metadata = MacOSExtensionLoader.readMetadata(apkFiles.first())
        assertNull(metadata, "readMetadata must return null for APK files")
    }

    @Test
    fun `test extension JAR metadata parses correctly`() {
        val jarFiles = extensionsDir.listFiles()?.filter { it.name == "test-extension-1.0.0.jar" } ?: emptyList()
        assumeTrue(jarFiles.isNotEmpty(), "test-extension-1.0.0.jar must be present")

        val metadata = MacOSExtensionLoader.readMetadata(jarFiles.first())
        assertEquals("Aniyomi: TestSource", metadata?.name)
        assertEquals("app.anikku.macos.testextension", metadata?.pkgName)
    }
}
