package app.anikku.macos.platform

import app.anikku.macos.platform.auth.TokenResponse
import app.anikku.macos.platform.auth.TrackerTokenStore
import app.anikku.macos.platform.backup.MacOSBackupManager
import app.anikku.macos.platform.data.DownloadRepository
import app.anikku.macos.platform.data.HistoryRepository
import app.anikku.macos.platform.data.LibraryRepository
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import app.anikku.macos.platform.security.MacOSSecretStore
import app.anikku.macos.platform.storage.MacOSStorageManager
import app.anikku.macos.platform.storage.MacOSStorageProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class Phase7StorageLifecycleTest {

    @Test
    fun `legacy backup format migrates with defaults and preserves data`(@TempDir tempDir: Path) {
        val dataDir = tempDir.resolve("data").toFile()
        val preferences = MacOSPreferenceStore(File(dataDir, "preferences.json"))
        val library = LibraryRepository(dataDir)
        val backupManager = MacOSBackupManager(
            libraryRepository = library,
            historyRepository = HistoryRepository(dataDir),
            downloadRepository = DownloadRepository(dataDir),
            preferenceStore = preferences,
        )
        val legacyBackup = tempDir.resolve("legacy.anikku_backup.json").toFile().apply {
            // Version 0 is the legacy export format; fields introduced after
            // it are intentionally absent and must receive their safe defaults.
            writeText(
                """
                {
                  "version": 0,
                  "appName": "Older Anikku",
                  "exportedAt": 1,
                  "library": [{"animeId": 7, "title": "Legacy title"}],
                  "history": [],
                  "downloads": [],
                  "preferences": {"legacyKey": "legacyValue"}
                }
                """.trimIndent(),
            )
        }

        val result = backupManager.importFrom(legacyBackup)

        assertTrue(result.success)
        assertEquals(1, result.libraryCount)
        assertEquals("Legacy title", library.get(7L)?.title)
        assertEquals("legacyValue", preferences.getString("legacyKey", "").get())
    }

    @Test
    fun `newer backup version is rejected without mutating persistence`(@TempDir tempDir: Path) {
        val dataDir = tempDir.resolve("data").toFile()
        val library = LibraryRepository(dataDir)
        val manager = MacOSBackupManager(
            libraryRepository = library,
            historyRepository = HistoryRepository(dataDir),
            downloadRepository = DownloadRepository(dataDir),
        )
        val futureBackup = tempDir.resolve("future.anikku_backup.json").toFile().apply {
            writeText("{\"version\":999,\"library\":[{\"animeId\":9,\"title\":\"Future\"}]}")
        }

        val result = manager.importFrom(futureBackup)

        assertFalse(result.success)
        assertTrue(result.error?.contains("supported") == true)
        assertNull(library.get(9L))
    }

    @Test
    fun `permission-denied persistence failure is deterministic and visible`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("preferences.json").toFile()
        val denied = java.nio.file.AccessDeniedException(file.path)
        val store = MacOSPreferenceStore(
            prefsFile = file,
            writeText = { _, _ -> throw denied },
        )

        assertThrows(java.nio.file.AccessDeniedException::class.java) {
            store.getString("key", "").set("value")
        }
        assertNotNull(store.lastPersistenceError())
        assertEquals("", store.getString("key", "").get())
        assertFalse(file.exists())
    }

    @Test
    fun `persistence failure is visible and does not leave a temporary file`(@TempDir tempDir: Path) {
        val blockedParent = tempDir.resolve("blocked").toFile().apply { writeText("not a directory") }
        val store = MacOSPreferenceStore(File(blockedParent, "preferences.json"))

        assertThrows(Exception::class.java) {
            store.getString("key", "").set("value")
        }
        assertNotNull(store.lastPersistenceError())
        assertTrue(tempDir.toFile().walk().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `malformed preferences are preserved and valid state is not destroyed`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("preferences.json").toFile()
        file.writeText("{ definitely not valid JSON")

        val store = MacOSPreferenceStore(file)

        assertEquals("fallback", store.getString("missing", "fallback").get())
        assertTrue(tempDir.toFile().listFiles()?.any { it.name.startsWith(".preferences.json.corrupt-") } == true)

        store.getString("healthy", "").set("value")
        assertEquals("value", MacOSPreferenceStore(file).getString("healthy", "").get())
    }

    @Test
    fun `concurrent preference writes remain valid and survive restart`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("preferences.json").toFile()
        val store = MacOSPreferenceStore(file)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val finished = CountDownLatch(64)
            repeat(64) { index ->
                executor.execute {
                    try {
                        store.getInt("key-$index", 0).set(index)
                    } finally {
                        finished.countDown()
                    }
                }
            }
            assertTrue(finished.await(10, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }

        val reloaded = MacOSPreferenceStore(file)
        repeat(64) { index ->
            assertEquals(index, reloaded.getInt("key-$index", -1).get())
        }
    }

    @Test
    fun `custom anime state is atomic malformed-safe and reloadable`(@TempDir tempDir: Path) {
        val dataDir = tempDir.toFile()
        val repository = app.anikku.macos.platform.data.MacOSCustomAnimeRepository(dataDir)
        repository.set(42L, title = "Saved title", genre = listOf("action"))

        val reloaded = app.anikku.macos.platform.data.MacOSCustomAnimeRepository(dataDir)
        assertEquals("Saved title", reloaded.get(42L)?.title)
        assertEquals(listOf("action"), reloaded.get(42L)?.genre)

        File(dataDir, "edits.json").writeText("broken")
        val safe = app.anikku.macos.platform.data.MacOSCustomAnimeRepository(dataDir)
        assertNull(safe.get(42L))
        assertTrue(dataDir.listFiles()?.any { it.name.startsWith(".edits.json.corrupt-") } == true)
    }

    @Test
    fun `malformed keychain token data is treated as unavailable`(@TempDir tempDir: Path) {
        val preferences = MacOSPreferenceStore(tempDir.resolve("preferences.json").toFile())
        val keychain = FakeSecretStore(isAvailable = true)
        keychain.values["tracker_token_anilist"] = "not a token blob"
        val store = TrackerTokenStore(preferences, keychain)

        assertNull(store.getTokens("anilist"))
        assertNull(store.lastStorageError)
    }

    @Test
    fun `keychain failure is distinct from missing token and never falls back to plaintext`(@TempDir tempDir: Path) {
        val preferences = MacOSPreferenceStore(tempDir.resolve("preferences.json").toFile())
        val unavailable = FakeSecretStore(isAvailable = false)
        val store = TrackerTokenStore(preferences, unavailable)

        assertNull(store.getTokens("anilist"))
        assertNotNull(store.lastStorageError)
        assertThrows(IllegalStateException::class.java) {
            store.saveTokens("anilist", token())
        }
        assertFalse(preferences.getAll().keys.any { it.contains("token") })
    }

    @Test
    fun `keychain token round trip and logout clear secure and metadata state`(@TempDir tempDir: Path) {
        val preferences = MacOSPreferenceStore(tempDir.resolve("preferences.json").toFile())
        val keychain = FakeSecretStore(isAvailable = true)
        val store = TrackerTokenStore(preferences, keychain)

        store.saveTokensWithUsername("anilist", token(), "tester")
        assertEquals("access-token", store.getTokens("anilist")?.accessToken)
        assertEquals("tester", store.getUsername("anilist"))
        assertTrue(preferences.getAll().keys.any { it == "tracker_meta_anilist" })

        assertTrue(store.removeTokens("anilist"))
        assertNull(store.getTokens("anilist"))
        assertNull(store.getUsername("anilist"))
        assertFalse(keychain.values.keys.any { it.contains("anilist") })
    }

    @Test
    fun `failed secure logout retains metadata and reports failure`(@TempDir tempDir: Path) {
        val preferences = MacOSPreferenceStore(tempDir.resolve("preferences.json").toFile())
        val keychain = FakeSecretStore(isAvailable = true)
        val store = TrackerTokenStore(preferences, keychain)
        store.saveTokensWithUsername("kitsu", token(), "tester")
        keychain.failDeletes = true

        assertFalse(store.removeTokens("kitsu"))
        assertEquals("tester", store.getUsername("kitsu"))
        assertNotNull(store.lastStorageError)
        assertNotNull(keychain.values["tracker_token_kitsu"])
    }

    @Test
    fun `storage manager creates required directories and closes its watcher`(@TempDir tempDir: Path) {
        val provider = object : MacOSStorageProvider() {
            override fun directory(): File = tempDir.toFile()
        }
        val manager = MacOSStorageManager(provider)
        try {
            assertTrue(manager.getAutomaticBackupsDirectory()!!.isDirectory)
            assertTrue(manager.getDownloadsDirectory()!!.isDirectory)
            assertTrue(manager.getLocalSourceDirectory()!!.isDirectory)
            assertTrue(manager.getFontsDirectory()!!.isDirectory)
            assertTrue(manager.getScriptsDirectory()!!.isDirectory)
            assertTrue(manager.getScriptOptsDirectory()!!.isDirectory)
            assertTrue(manager.getLogsDirectory()!!.isDirectory)
        } finally {
            manager.close()
            manager.close()
        }
    }

    @Test
    fun `background tasks cancel predictably and one shot tasks complete`() = runBlocking {
        val scheduler = BackgroundTaskScheduler(this)
        val periodicRuns = AtomicInteger(0)
        scheduler.schedulePeriodic("periodic", 1L) { periodicRuns.incrementAndGet() }
        delay(20)
        scheduler.cancelAll()
        val countAfterCancel = periodicRuns.get()
        delay(20)
        assertEquals(countAfterCancel, periodicRuns.get())
        assertFalse(scheduler.isRunning("periodic"))

        val oneShotRuns = AtomicInteger(0)
        scheduler.runOnce("once") { oneShotRuns.incrementAndGet() }
        repeat(20) {
            if (oneShotRuns.get() == 1) return@repeat
            delay(5)
        }
        assertEquals(1, oneShotRuns.get())
        assertFalse(scheduler.isRunning("once"))
    }

    private fun token() = TokenResponse(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        tokenType = "Bearer",
        expiresIn = 3600,
        scope = "read",
        createdAt = 1L,
    )

    private class FakeSecretStore(
        override val isAvailable: Boolean,
    ) : MacOSSecretStore {
        val values = mutableMapOf<String, String>()
        var failDeletes = false
        override var lastError: String? = null
            private set

        @Synchronized
        override fun store(key: String, value: String): Boolean {
            if (!isAvailable) {
                lastError = "unavailable"
                return false
            }
            values[key] = value
            lastError = null
            return true
        }

        @Synchronized
        override fun retrieve(key: String): String? {
            if (!isAvailable) {
                lastError = "unavailable"
                return null
            }
            lastError = null
            return values[key]
        }

        @Synchronized
        override fun delete(key: String): Boolean {
            if (failDeletes) {
                lastError = "delete denied"
                return false
            }
            values.remove(key)
            lastError = null
            return true
        }
    }
}
