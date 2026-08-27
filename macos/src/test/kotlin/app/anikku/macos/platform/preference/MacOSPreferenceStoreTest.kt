package app.anikku.macos.platform.preference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class MacOSPreferenceStoreTest {

    @Test
    fun `getString returns default when key not set`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getString("test_key", "default_value")
        assertEquals("default_value", pref.get())
    }

    @Test
    fun `getString returns set value`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getString("name", "default")
        pref.set("Anikku macOS")
        assertEquals("Anikku macOS", pref.get())
    }

    @Test
    fun `getInt returns default when key not set`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getInt("count", 42)
        assertEquals(42, pref.get())
    }

    @Test
    fun `getInt returns set value`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getInt("count", 0)
        pref.set(99)
        assertEquals(99, pref.get())
    }

    @Test
    fun `getBoolean returns default when key not set`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getBoolean("enabled", true)
        assertTrue(pref.get())
    }

    @Test
    fun `getBoolean toggles correctly`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getBoolean("dark_mode", false)
        assertFalse(pref.get())
        pref.set(true)
        assertTrue(pref.get())
        pref.set(false)
        assertFalse(pref.get())
    }

    @Test
    fun `getLong persists and retrieves`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getLong("timestamp", 0L)
        pref.set(123456789L)
        assertEquals(123456789L, pref.get())
    }

    @Test
    fun `getFloat returns set value`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getFloat("volume", 0.5f)
        pref.set(0.75f)
        assertEquals(0.75f, pref.get(), 0.001f)
    }

    @Test
    fun `getStringSet returns and sets`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getStringSet("categories", emptySet())
        pref.set(setOf("action", "fantasy", "comedy"))
        assertEquals(setOf("action", "fantasy", "comedy"), pref.get())
    }

    @Test
    fun `isSet returns true only after value set`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getString("key", "default")
        assertFalse(pref.isSet())
        pref.set("value")
        assertTrue(pref.isSet())
    }

    @Test
    fun `delete removes the key`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getString("key", "default")
        pref.set("value")
        assertTrue(pref.isSet())
        pref.delete()
        assertFalse(pref.isSet())
        assertEquals("default", pref.get())
    }

    @Test
    fun `getAll returns all stored keys`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        store.getString("name", "").set("Anikku")
        store.getInt("count", 0).set(5)
        val all = store.getAll()
        assertEquals("Anikku", all["name"])
        assertEquals("5", all["count"])
    }

    @Test
    fun `values persist across store instances`(@TempDir tempDir: File) {
        val file = File(tempDir, "persist.json")
        MacOSPreferenceStore(file).apply {
            getString("persisted_key", "default").set("survived")
        }

        val reloaded = MacOSPreferenceStore(file)
        assertEquals("survived", reloaded.getString("persisted_key", "default").get())
    }

    @Test
    fun `corrupted file starts fresh`(@TempDir tempDir: File) {
        val file = File(tempDir, "corrupted.json")
        file.writeText("not valid json{{{")

        val store = MacOSPreferenceStore(file)
        val pref = store.getString("key", "default")
        assertEquals("default", pref.get())
    }

    @Test
    fun `getObject serializes and deserializes`(@TempDir tempDir: File) {
        val store = MacOSPreferenceStore(File(tempDir, "prefs.json"))
        val pref = store.getObject(
            key = "custom",
            defaultValue = "fallback",
            serializer = { it.uppercase() },
            deserializer = { it.lowercase() },
        )
        pref.set("Hello World")
        assertEquals("hello world", pref.get())
    }
}
