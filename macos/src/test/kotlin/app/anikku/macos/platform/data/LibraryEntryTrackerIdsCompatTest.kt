package app.anikku.macos.platform.data

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryEntryTrackerIdsCompatTest {

    @Test
    fun `old backups without tracker id fields deserialize with defaults`() {
        // A backup written before malId/kitsuId existed (or from the Android
        // app) must load cleanly — both fields default to null.
        val json = Json { ignoreUnknownKeys = true }
        val entry = json.decodeFromString<LibraryRepository.LibraryEntry>(
            """{"animeId":1,"title":"Old backup","sourceId":9,"url":"/anime/1"}""",
        )

        assertEquals(1L, entry.animeId)
        assertEquals("Old backup", entry.title)
        assertNull(entry.anilistId)
        assertNull(entry.malId)
        assertNull(entry.kitsuId)
    }

    @Test
    fun `tracker ids survive a full serialization round-trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val entry = LibraryRepository.LibraryEntry(
            animeId = 1535,
            title = "Death Note",
            malId = 1535,
            kitsuId = 20,
            anilistId = 21,
        )

        val restored = json.decodeFromString<LibraryRepository.LibraryEntry>(
            json.encodeToString(LibraryRepository.LibraryEntry.serializer(), entry),
        )

        assertEquals(1535L, restored.malId)
        assertEquals(20L, restored.kitsuId)
        assertEquals(21L, restored.anilistId)
    }
}
