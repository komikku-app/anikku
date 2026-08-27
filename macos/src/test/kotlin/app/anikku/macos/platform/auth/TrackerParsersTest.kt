package app.anikku.macos.platform.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackerParsersTest {

    @Test
    fun `parseMalLibrary extracts entries with list status and cover`() {
        val body = """
            {
              "data": [
                {
                  "node": {
                    "id": 1535,
                    "title": "Death Note",
                    "main_picture": {
                      "medium": "https://cdn.myanimelist.net/images/anime/9/9453.jpg",
                      "large": "https://cdn.myanimelist.net/images/anime/9/9453l.jpg"
                    },
                    "num_episodes": 37
                  },
                  "list_status": { "status": "completed", "score": 9, "num_watched_episodes": 37 }
                },
                {
                  "node": { "id": 21, "title": "One Piece", "main_picture": null },
                  "list_status": { "status": "watching", "num_watched_episodes": 1100 }
                }
              ]
            }
        """.trimIndent()

        val entries = parseMalLibrary(body)

        assertEquals(2, entries.size)
        val deathNote = entries[0]
        assertEquals(1535L, deathNote.malId)
        assertEquals("Death Note", deathNote.title)
        assertEquals("completed", deathNote.status)
        assertEquals(37, deathNote.progress)
        assertEquals(37, deathNote.totalEpisodes)
        assertEquals("https://cdn.myanimelist.net/images/anime/9/9453l.jpg", deathNote.coverUrl)

        val onePiece = entries[1]
        assertEquals(21L, onePiece.malId)
        assertEquals("watching", onePiece.status)
        assertEquals(1100, onePiece.progress)
        assertNull(onePiece.totalEpisodes)
        assertNull(onePiece.coverUrl)
    }

    @Test
    fun `parseMalLibrary handles empty or malformed bodies`() {
        assertEquals(0, parseMalLibrary("""{"data":[]}""").size)
        assertEquals(0, parseMalLibrary("not json").size)
    }

    @Test
    fun `parseKitsuLibrary extracts entries with anime include and cover`() {
        val body = """
            {
              "data": [
                {
                  "id": "7001",
                  "type": "libraryEntries",
                  "attributes": { "status": "current", "progress": 5, "kind": "anime" },
                  "relationships": { "anime": { "data": { "id": "40852", "type": "anime" } } }
                }
              ],
              "included": [
                {
                  "id": "40852",
                  "type": "anime",
                  "attributes": {
                    "canonicalTitle": "Jujutsu Kaisen",
                    "episodeCount": 24,
                    "posterImage": {
                      "large": "https://media.kitsu.io/anime/poster_images/40852/large.jpg",
                      "original": "https://media.kitsu.io/anime/poster_images/40852/original.jpg"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val entries = parseKitsuLibrary(body)

        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals(40852L, entry.kitsuId)
        assertEquals("7001", entry.libraryEntryId)
        assertEquals("Jujutsu Kaisen", entry.title)
        assertEquals("current", entry.status)
        assertEquals(5, entry.progress)
        assertEquals(24, entry.totalEpisodes)
        assertEquals("https://media.kitsu.io/anime/poster_images/40852/original.jpg", entry.coverUrl)
    }

    @Test
    fun `parseKitsuLibrary skips entries whose anime is missing from include`() {
        val body = """
            {
              "data": [
                { "id": "1", "type": "libraryEntries", "attributes": { "status": "current" },
                  "relationships": { "anime": { "data": { "id": "999", "type": "anime" } } } }
              ],
              "included": []
            }
        """.trimIndent()

        val entries = parseKitsuLibrary(body)

        assertEquals(0, entries.size)
    }
}
