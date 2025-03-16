package tachiyomi.domain.episode.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class EpisodeRecognitionTest {

    @Test
    fun `Basic Ep prefix`() {
        val animeTitle = "Mokushiroku Alice"

        assertEpisode(animeTitle, "Mokushiroku Alice Vol.1 Ep.4: Misrepresentation", 4.0)
    }

    @Test
    fun `Basic Ep prefix with space after period`() {
        val animeTitle = "Mokushiroku Alice"

        assertEpisode(animeTitle, "Mokushiroku Alice Vol. 1 Ep. 4: Misrepresentation", 4.0)
    }

    @Test
    fun `Basic Ep prefix with decimal`() {
        val animeTitle = "Mokushiroku Alice"

        assertEpisode(animeTitle, "Mokushiroku Alice Vol.1 Ep.4.1: Misrepresentation", 4.1)
        assertEpisode(animeTitle, "Mokushiroku Alice Vol.1 Ep.4.4: Misrepresentation", 4.4)
    }

    @Test
    fun `Basic Ep prefix with alpha postfix`() {
        val animeTitle = "Mokushiroku Alice"

        assertEpisode(animeTitle, "Mokushiroku Alice Vol.1 Ep.4.a: Misrepresentation", 4.1)
        assertEpisode(animeTitle, "Mokushiroku Alice Vol.1 Ep.4.b: Misrepresentation", 4.2)
        assertEpisode(animeTitle, "Mokushiroku Alice Vol.1 Ep.4.extra: Misrepresentation", 4.99)
    }

    @Test
    fun `Name containing one number`() {
        val animeTitle = "Bleach"

        assertEpisode(animeTitle, "Bleach 567 Down With Snowwhite", 567.0)
    }

    @Test
    fun `Name containing one number and decimal`() {
        val animeTitle = "Bleach"

        assertEpisode(animeTitle, "Bleach 567.1 Down With Snowwhite", 567.1)
        assertEpisode(animeTitle, "Bleach 567.4 Down With Snowwhite", 567.4)
    }

    @Test
    fun `Name containing one number and alpha`() {
        val animeTitle = "Bleach"

        assertEpisode(animeTitle, "Bleach 567.a Down With Snowwhite", 567.1)
        assertEpisode(animeTitle, "Bleach 567.b Down With Snowwhite", 567.2)
        assertEpisode(animeTitle, "Bleach 567.extra Down With Snowwhite", 567.99)
    }

    @Test
    fun `Episode containing anime title and number`() {
        val animeTitle = "Solanin"

        assertEpisode(animeTitle, "Solanin 028 Vol. 2", 28.0)
    }

    @Test
    fun `Episode containing anime title and number decimal`() {
        val animeTitle = "Solanin"

        assertEpisode(animeTitle, "Solanin 028.1 Vol. 2", 28.1)
        assertEpisode(animeTitle, "Solanin 028.4 Vol. 2", 28.4)
    }

    @Test
    fun `Episode containing anime title and number alpha`() {
        val animeTitle = "Solanin"

        assertEpisode(animeTitle, "Solanin 028.a Vol. 2", 28.1)
        assertEpisode(animeTitle, "Solanin 028.b Vol. 2", 28.2)
        assertEpisode(animeTitle, "Solanin 028.extra Vol. 2", 28.99)
    }

    @Test
    fun `Extreme case`() {
        val animeTitle = "Onepunch-Man"

        assertEpisode(animeTitle, "Onepunch-Man Punch Ver002 028", 28.0)
    }

    @Test
    fun `Extreme case with decimal`() {
        val animeTitle = "Onepunch-Man"

        assertEpisode(animeTitle, "Onepunch-Man Punch Ver002 028.1", 28.1)
        assertEpisode(animeTitle, "Onepunch-Man Punch Ver002 028.4", 28.4)
    }

    @Test
    fun `Extreme case with alpha`() {
        val animeTitle = "Onepunch-Man"

        assertEpisode(animeTitle, "Onepunch-Man Punch Ver002 028.a", 28.1)
        assertEpisode(animeTitle, "Onepunch-Man Punch Ver002 028.b", 28.2)
        assertEpisode(animeTitle, "Onepunch-Man Punch Ver002 028.extra", 28.99)
    }

    @Test
    fun `Episode containing dot v2`() {
        val animeTitle = "random"

        assertEpisode(animeTitle, "Vol.1 Ep.5v.2: Alones", 5.0)
    }

    @Test
    fun `Number in anime title`() {
        val animeTitle = "Ayame 14"

        assertEpisode(animeTitle, "Ayame 14 1 - The summer of 14", 1.0)
    }

    @Test
    fun `Space between ep x`() {
        val animeTitle = "Mokushiroku Alice"

        assertEpisode(animeTitle, "Mokushiroku Alice Vol.1 Ep. 4: Misrepresentation", 4.0)
    }

    @Test
    fun `Episode title with ep substring`() {
        val animeTitle = "Ayame 14"

        assertEpisode(animeTitle, "Vol.1 Ep.1: March 25 (First Day Cohabiting)", 1.0)
    }

    @Test
    fun `Episode containing multiple zeros`() {
        val animeTitle = "random"

        assertEpisode(animeTitle, "Vol.001 Ep.003: Kaguya Doesn't Know Much", 3.0)
    }

    @Test
    fun `Episode with version before number`() {
        val animeTitle = "Onepunch-Man"

        assertEpisode(animeTitle, "Onepunch-Man Punch Ver002 086 : Creeping Darkness [3]", 86.0)
    }

    @Test
    fun `Version attached to episode number`() {
        val animeTitle = "Ansatsu Kyoushitsu"

        assertEpisode(animeTitle, "Ansatsu Kyoushitsu 011v002: Assembly Time", 11.0)
    }

    /**
     * Case where the episode title contains the episode
     * But wait it's not actual the episode number.
     */
    @Test
    fun `Number after anime title with episode in episode title case`() {
        val animeTitle = "Tokyo ESP"

        assertEpisode(animeTitle, "Tokyo ESP 027: Part 002: Episode 001", 027.0)
    }

    /**
     * Case where the episode title contains the unwanted tag
     * But follow by episode number.
     */
    @Test
    fun `Number after unwanted tag`() {
        val animeTitle = "One-punch Man"

        assertEpisode(animeTitle, "Mag Version 195.5", 195.5)
    }

    @Test
    fun `Unparseable episode`() {
        val animeTitle = "random"

        assertEpisode(animeTitle, "Foo", -1.0)
    }

    @Test
    fun `Episode with time in title`() {
        val animeTitle = "random"

        assertEpisode(animeTitle, "Fairy Tail 404: 00:00", 404.0)
    }

    @Test
    fun `Episode with alpha without dot`() {
        val animeTitle = "random"

        assertEpisode(animeTitle, "Asu No Yoichi 19a", 19.1)
    }

    @Test
    fun `Episode title containing extra and vol`() {
        val animeTitle = "Fairy Tail"

        assertEpisode(animeTitle, "Fairy Tail 404.extravol002", 404.99)
        assertEpisode(animeTitle, "Fairy Tail 404 extravol002", 404.99)
    }

    @Test
    fun `Episode title containing omake (japanese extra) and vol`() {
        val animeTitle = "Fairy Tail"

        assertEpisode(animeTitle, "Fairy Tail 404.omakevol002", 404.98)
        assertEpisode(animeTitle, "Fairy Tail 404 omakevol002", 404.98)
    }

    @Test
    fun `Episode title containing special and vol`() {
        val animeTitle = "Fairy Tail"

        assertEpisode(animeTitle, "Fairy Tail 404.specialvol002", 404.97)
        assertEpisode(animeTitle, "Fairy Tail 404 specialvol002", 404.97)
    }

    @Test
    fun `Episode title containing commas`() {
        val animeTitle = "One Piece"

        assertEpisode(animeTitle, "One Piece 300,a", 300.1)
        assertEpisode(animeTitle, "One Piece Ep,123,extra", 123.99)
        assertEpisode(animeTitle, "One Piece the sunny, goes swimming 024,005", 24.005)
    }

    @Test
    fun `Episode title containing hyphens`() {
        val animeTitle = "Solo Leveling"

        assertEpisode(animeTitle, "ep 122-a", 122.1)
        assertEpisode(animeTitle, "Solo Leveling Ep.123-extra", 123.99)
        assertEpisode(animeTitle, "Solo Leveling, 024-005", 24.005)
        assertEpisode(animeTitle, "Ep.191-200 Read Online", 191.200)
    }

    @Test
    fun `Episodes containing season`() {
        assertEpisode("D.I.C.E", "D.I.C.E[Season 001] Ep. 007", 7.0)
    }

    @Test
    fun `Episodes in format sx - episode xx`() {
        assertEpisode("The Gamer", "S3 - Episode 20", 20.0)
    }

    @Test
    fun `Episodes ending with s`() {
        assertEpisode("One Outs", "One Outs 001", 1.0)
    }

    @Test
    fun `Episodes containing ordinals`() {
        val animeTitle = "The Sister of the Woods with a Thousand Young"

        assertEpisode(animeTitle, "The 1st Night", 1.0)
        assertEpisode(animeTitle, "The 2nd Night", 2.0)
        assertEpisode(animeTitle, "The 3rd Night", 3.0)
        assertEpisode(animeTitle, "The 4th Night", 4.0)
    }

    private fun assertEpisode(animeTitle: String, name: String, expected: Double) {
        EpisodeRecognition.parseEpisodeNumber(animeTitle, name) shouldBe expected
    }
}
