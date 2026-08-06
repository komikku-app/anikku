package app.anikku.macos.platform.extension

import android.content.AndroidPrefsBridge
import android.content.Context
import app.anikku.macos.platform.preference.MacOSPreferenceStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * The android.* stub's SharedPreferences bridge must persist real values
 * through the app's preference store — this is what makes per-source
 * settings (SourceSettingsDialog) survive restarts.
 */
class AndroidPrefsBridgeTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var store: MacOSPreferenceStore

    @BeforeEach
    fun setUp() {
        store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        AndroidPrefsBridge.store = store
    }

    @AfterEach
    fun tearDown() {
        AndroidPrefsBridge.store = null
    }

    @Test
    fun `extension prefs round-trip through the app store`() {
        val prefs = Context().getSharedPreferences("test.extension", 0)
        prefs.edit()
            .putString("quality", "1080p")
            .putBoolean("fast_stream", true)
            .putLong("timeout", 30L)
            .commit()

        assertEquals("1080p", prefs.getString("quality", null))
        assertTrue(prefs.getBoolean("fast_stream", false))
        assertEquals(30L, prefs.getLong("timeout", 0L))
        assertTrue(prefs.contains("quality"))
        assertEquals(setOf("quality", "fast_stream", "timeout"), prefs.all.keys)

        // A second store over the same file sees the values (real persistence).
        val store2 = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        AndroidPrefsBridge.store = store2
        val prefs2 = Context().getSharedPreferences("test.extension", 0)
        assertEquals("1080p", prefs2.getString("quality", null))
        assertTrue(prefs2.getBoolean("fast_stream", false))
    }

    @Test
    fun `extension namespaces are isolated and remove works`() {
        val a = Context().getSharedPreferences("ext.a", 0)
        val b = Context().getSharedPreferences("ext.b", 0)
        a.edit().putString("key", "value-a").commit()
        b.edit().putString("key", "value-b").commit()

        assertEquals("value-a", a.getString("key", null))
        assertEquals("value-b", b.getString("key", null))

        a.edit().remove("key").commit()
        assertNull(a.getString("key", null))
        // The other namespace is untouched.
        assertEquals("value-b", b.getString("key", null))
        assertTrue(b.contains("key"))
    }
}
