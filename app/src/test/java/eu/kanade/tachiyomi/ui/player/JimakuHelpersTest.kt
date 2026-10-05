package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.ui.player.utils.JimakuFile
import eu.kanade.tachiyomi.ui.player.utils.JimakuUiError
import eu.kanade.tachiyomi.ui.player.utils.entryIdCacheKey
import eu.kanade.tachiyomi.ui.player.utils.entrySearchCacheKey
import eu.kanade.tachiyomi.ui.player.utils.fileListCacheKey
import eu.kanade.tachiyomi.ui.player.utils.rankFileFormat
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class JimakuHelpersTest {

    // Tests for rankFileFormat() — 7 test cases

    @Test
    fun testRankFileFormat_Ass() {
        val result = rankFileFormat("subtitle.ass")
        result shouldBe 3
    }

    @Test
    fun testRankFileFormat_AssCaseInsensitive() {
        val resultUpper = rankFileFormat("subtitle.ASS")
        val resultMixed = rankFileFormat("subtitle.Ass")
        resultUpper shouldBe 3
        resultMixed shouldBe 3
    }

    @Test
    fun testRankFileFormat_Ssa() {
        val result = rankFileFormat("subtitle.ssa")
        result shouldBe 2
    }

    @Test
    fun testRankFileFormat_SsaCaseInsensitive() {
        val result = rankFileFormat("subtitle.SSA")
        result shouldBe 2
    }

    @Test
    fun testRankFileFormat_Srt() {
        val result = rankFileFormat("subtitle.srt")
        result shouldBe 1
    }

    @Test
    fun testRankFileFormat_Vtt() {
        val result = rankFileFormat("subtitle.vtt")
        result shouldBe 0
    }

    @Test
    fun testRankFileFormat_NoExtension() {
        val result = rankFileFormat("subtitle")
        result shouldBe 0
    }

    // Tests for ranking/sorting logic — 4 test cases

    @Test
    fun testRanking_MixedFormats() {
        val files = listOf(
            JimakuFile(
                url = "https://example.com/sub1.srt",
                name = "subtitle.srt",
                size = 50000L,
                lastModified = "2024-01-01T00:00:00Z",
            ),
            JimakuFile(
                url = "https://example.com/sub2.ass",
                name = "subtitle.ass",
                size = 30000L,
                lastModified = "2024-01-01T00:00:00Z",
            ),
            JimakuFile(
                url = "https://example.com/sub3.txt",
                name = "subtitle.txt",
                size = 40000L,
                lastModified = "2024-01-01T00:00:00Z",
            ),
        )

        val ranked = files.sortedWith(
            compareByDescending<JimakuFile> { rankFileFormat(it.name) }
                .thenByDescending { it.size }
                .thenByDescending { it.lastModified.orEmpty() },
        )

        ranked.first().name shouldBe "subtitle.ass"
    }

    @Test
    fun testRanking_SameFormat_DifferentSizes() {
        val files = listOf(
            JimakuFile(
                url = "https://example.com/sub1.ass",
                name = "subtitle1.ass",
                size = 100000L,
                lastModified = "2024-01-01T00:00:00Z",
            ),
            JimakuFile(
                url = "https://example.com/sub2.ass",
                name = "subtitle2.ass",
                size = 200000L,
                lastModified = "2024-01-01T00:00:00Z",
            ),
        )

        val ranked = files.sortedWith(
            compareByDescending<JimakuFile> { rankFileFormat(it.name) }
                .thenByDescending { it.size }
                .thenByDescending { it.lastModified.orEmpty() },
        )

        ranked.first().size shouldBe 200000L
    }

    @Test
    fun testRanking_SameFormatAndSize_DifferentTimestamps() {
        val files = listOf(
            JimakuFile(
                url = "https://example.com/sub1.ass",
                name = "subtitle1.ass",
                size = 100000L,
                lastModified = "2024-01-01T00:00:00Z",
            ),
            JimakuFile(
                url = "https://example.com/sub2.ass",
                name = "subtitle2.ass",
                size = 100000L,
                lastModified = "2024-02-01T00:00:00Z",
            ),
        )

        val ranked = files.sortedWith(
            compareByDescending<JimakuFile> { rankFileFormat(it.name) }
                .thenByDescending { it.size }
                .thenByDescending { it.lastModified.orEmpty() },
        )

        ranked.first().lastModified shouldBe "2024-02-01T00:00:00Z"
    }

    @Test
    fun testRanking_EmptyList() {
        val files = emptyList<JimakuFile>()
        val ranked = files.sortedWith(
            compareByDescending<JimakuFile> { rankFileFormat(it.name) }
                .thenByDescending { it.size }
                .thenByDescending { it.lastModified.orEmpty() },
        )

        ranked.firstOrNull().shouldBeNull()
    }

    // Tests for entrySearchCacheKey() — 3 test cases

    @Test
    fun testEntrySearchCacheKey_BasicLowercasing() {
        val result = entrySearchCacheKey("Frieren")
        result shouldBe "query:frieren"
    }

    @Test
    fun testEntrySearchCacheKey_TrimAndLowercase() {
        val result = entrySearchCacheKey("  Frieren  ")
        result shouldBe "query:frieren"
    }

    @Test
    fun testEntrySearchCacheKey_MultipleWords() {
        val result = entrySearchCacheKey("BOCCHI THE ROCK")
        result shouldBe "query:bocchi the rock"
    }

    @Test
    fun testEntrySearchCacheKey_EmptyString() {
        val result = entrySearchCacheKey("")
        result shouldBe "query:"
    }

    // Tests for entryIdCacheKey() — 1 test case

    @Test
    fun testEntryIdCacheKey_LongId() {
        val result = entryIdCacheKey(154587L)
        result shouldBe "anilist:154587"
    }

    // Tests for fileListCacheKey() — 2 test cases

    @Test
    fun testFileListCacheKey_WithEpisodeNumber() {
        val result = fileListCacheKey(12345L, 3)
        result shouldBe "12345:3"
    }

    @Test
    fun testFileListCacheKey_WithNullEpisode() {
        val result = fileListCacheKey(12345L, null)
        result shouldBe "12345:all"
    }

    // Tests for JimakuUiError sealed class — 2 test cases

    @Test
    fun testJimakuUiError_AuthErrorSingleton() {
        val error1 = JimakuUiError.AuthError
        val error2 = JimakuUiError.AuthError
        (error1 === error2) shouldBe true
    }

    @Test
    fun testJimakuUiError_UnknownWithMessage() {
        val message = "Test error message"
        val error = JimakuUiError.Unknown(message)
        error.message shouldBe message
    }
}
