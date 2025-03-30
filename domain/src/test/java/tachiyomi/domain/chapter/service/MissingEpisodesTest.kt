package tachiyomi.domain.chapter.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.model.Chapter

@Execution(ExecutionMode.CONCURRENT)
class MissingEpisodesTest {

    @Test
    fun `missingEpisodesCount returns 0 when empty list`() {
        emptyList<Double>().missingChaptersCount() shouldBe 0
    }

    @Test
    fun `missingEpisodesCount returns 0 when all unknown episode numbers`() {
        listOf(-1.0, -1.0, -1.0).missingChaptersCount() shouldBe 0
    }

    @Test
    fun `missingEpisodesCount handles repeated base episode numbers`() {
        listOf(1.0, 1.0, 1.1, 1.5, 1.6, 1.99).missingChaptersCount() shouldBe 0
    }

    @Test
    fun `missingEpisodesCount returns number of missing episodes`() {
        listOf(-1.0, 1.0, 2.0, 2.2, 4.0, 6.0, 10.0, 11.0).missingChaptersCount() shouldBe 5
    }

    @Test
    fun `calculateEpisodeGap returns difference`() {
        calculateChapterGap(episode(10.0), episode(9.0)) shouldBe 0f
        calculateChapterGap(episode(10.0), episode(8.0)) shouldBe 1f
        calculateChapterGap(episode(10.0), episode(8.5)) shouldBe 1f
        calculateChapterGap(episode(10.0), episode(1.1)) shouldBe 8f

        calculateChapterGap(10.0, 9.0) shouldBe 0f
        calculateChapterGap(10.0, 8.0) shouldBe 1f
        calculateChapterGap(10.0, 8.5) shouldBe 1f
        calculateChapterGap(10.0, 1.1) shouldBe 8f
    }

    @Test
    fun `calculateEpisodeGap returns 0 if either are not valid episode numbers`() {
        calculateChapterGap(episode(-1.0), episode(10.0)) shouldBe 0
        calculateChapterGap(episode(99.0), episode(-1.0)) shouldBe 0

        calculateChapterGap(-1.0, 10.0) shouldBe 0
        calculateChapterGap(99.0, -1.0) shouldBe 0
    }

    private fun episode(number: Double) = Chapter.create().copy(
        episodeNumber = number,
    )
}
