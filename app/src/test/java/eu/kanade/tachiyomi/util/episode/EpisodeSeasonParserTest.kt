package eu.kanade.tachiyomi.util.episode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeSeasonParserTest {

    @Test
    fun `matches Season N word form`() {
        assertEquals(2, EpisodeSeasonParser.parseSeason("Season 2 Episode 5"))
    }

    @Test
    fun `matches SN abbreviation form`() {
        assertEquals(2, EpisodeSeasonParser.parseSeason("S2 - Ep 05"))
    }

    @Test
    fun `matches zero-padded SN abbreviation form`() {
        assertEquals(2, EpisodeSeasonParser.parseSeason("S02E05"))
    }

    @Test
    fun `matches ordinal Season form`() {
        assertEquals(2, EpisodeSeasonParser.parseSeason("2nd Season - 05"))
    }

    @Test
    fun `returns null when no season marker present`() {
        assertNull(EpisodeSeasonParser.parseSeason("Episode 24"))
    }

    @Test
    fun `does not match stray S with no digit`() {
        assertNull(EpisodeSeasonParser.parseSeason("Special Episode"))
    }
}
