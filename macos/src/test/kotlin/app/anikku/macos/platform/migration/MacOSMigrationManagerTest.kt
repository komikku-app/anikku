package app.anikku.macos.platform.migration

import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSSecretStore
import app.anikku.macos.platform.storage.MacOSStorageProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MacOSMigrationManagerTest {

    @Test
    fun `initial install runs always migrations only and records version`(@TempDir tempDir: Path) = runBlocking {
        val fixture = Fixture(tempDir)
        val executed = mutableListOf<String>()
        val manager = fixture.manager(
            currentVersion = 3,
            migrations = listOf(
                migration(1, "always", always = true) { executed += "always" },
                migration(1, "v1") { executed += "v1" },
                migration(2, "v2") { executed += "v2" },
            ),
        )

        val result = manager.migrate()

        assertTrue(result.success)
        assertEquals(listOf("always"), executed)
        assertEquals(3, fixture.preferences.getInt("macos_last_version_code", 0).get())
    }

    @Test
    fun `upgrade runs ordered version range plus always migration`(@TempDir tempDir: Path) = runBlocking {
        val fixture = Fixture(tempDir)
        fixture.preferences.getInt("macos_last_version_code", 0).set(1)
        val executed = mutableListOf<String>()
        val manager = fixture.manager(
            currentVersion = 3,
            migrations = listOf(
                migration(3, "v3") { executed += "v3" },
                migration(1, "always", always = true) { executed += "always" },
                migration(2, "v2") { executed += "v2" },
                migration(4, "future") { executed += "future" },
            ),
        )

        val result = manager.migrate()

        assertTrue(result.success)
        assertEquals(listOf("always", "v2", "v3"), executed)
        assertEquals(3, result.currentVersion)
    }

    @Test
    fun `failed migration does not advance version and succeeds on retry`(@TempDir tempDir: Path) = runBlocking {
        val fixture = Fixture(tempDir)
        fixture.preferences.getInt("macos_last_version_code", 0).set(1)
        var attempts = 0
        val manager = fixture.manager(
            currentVersion = 2,
            migrations = listOf(
                MacOSMigration(2, "retry") {
                    attempts++
                    attempts >= 2
                },
            ),
        )

        val failed = manager.migrate()
        assertFalse(failed.success)
        assertEquals("retry", failed.failedMigration)
        assertEquals(1, fixture.preferences.getInt("macos_last_version_code", 0).get())

        val retried = manager.migrate()
        assertTrue(retried.success)
        assertEquals(2, fixture.preferences.getInt("macos_last_version_code", 0).get())
    }

    @Test
    fun `default migration moves legacy secrets and removes obsolete PEM`(@TempDir tempDir: Path) = runBlocking {
        val fixture = Fixture(tempDir)
        fixture.preferences.getString("tracker_token_anilist", "").set("token-value")
        fixture.preferences.getString("creds_secret_anilist", "").set("secret-value")
        File(fixture.storage.dataDirectory, "cacert.pem").apply {
            parentFile.mkdirs()
            writeText("obsolete")
        }

        val result = fixture.manager().migrate()

        assertTrue(result.success)
        assertEquals("token-value", fixture.secrets.values["tracker_token_anilist"])
        assertEquals("secret-value", fixture.secrets.values["creds_secret_anilist"])
        assertEquals("", fixture.preferences.getString("tracker_token_anilist", "").get())
        assertFalse(File(fixture.storage.dataDirectory, "cacert.pem").exists())
    }

    @Test
    fun `dry run reports work without mutation`(@TempDir tempDir: Path) = runBlocking {
        val fixture = Fixture(tempDir)
        var executed = false
        val manager = fixture.manager(
            currentVersion = 2,
            migrations = listOf(migration(1, "always", always = true) { executed = true }),
        )

        val result = manager.migrate(dryRun = true)

        assertTrue(result.success)
        assertEquals(listOf("always"), result.executed)
        assertFalse(executed)
        assertEquals(0, fixture.preferences.getInt("macos_last_version_code", 0).get())
    }

    private fun migration(
        version: Int,
        name: String,
        always: Boolean = false,
        action: () -> Unit,
    ) = MacOSMigration(version, name, always) {
        action()
        true
    }

    private class Fixture(root: Path) {
        private val directory = root.resolve("app").toFile().apply { mkdirs() }
        val storage = object : MacOSStorageProvider() {
            override fun directory(): File = directory
        }.also { it.ensureDirectories() }
        val preferences = MacOSPreferenceStore(storage.dataDirectory.resolve("preferences.json"))
        val secrets = FakeSecretStore()

        fun manager(
            currentVersion: Int = MacOSMigrationManager.CURRENT_VERSION,
            migrations: List<MacOSMigration> = MacOSMigrationManager.defaultMigrations(),
        ) = MacOSMigrationManager(preferences, storage, secrets, currentVersion, migrations)
    }

    private class FakeSecretStore : MacOSSecretStore {
        override val isAvailable = true
        override val lastError: String? = null
        val values = mutableMapOf<String, String>()
        override fun store(key: String, value: String): Boolean {
            values[key] = value
            return true
        }
        override fun retrieve(key: String): String? = values[key]
        override fun delete(key: String): Boolean = values.remove(key) != null
    }
}
