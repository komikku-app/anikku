package tachiyomi.domain.items.episode.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.service.calculateChapterGap
import tachiyomi.domain.episode.service.missingEpisodesCount

@Execution(ExecutionMode.CONCURRENT)
class MissingEpisodesTest {

    @Test
    fun `missingEpisodesCount returns 0 when empty list`() {
        emptyList<Double>().missingEpisodesCount() shouldBe 0
    }

    @Test
    fun `missingEpisodesCount returns 0 when all unknown item numbers`() {
        listOf(-1.0, -1.0, -1.0).missingEpisodesCount() shouldBe 0
    }

    @Test
    fun `missingEpisodesCount handles repeated base item numbers`() {
        listOf(1.0, 1.0, 1.1, 1.5, 1.6, 1.99).missingEpisodesCount() shouldBe 0
    }

    @Test
    fun `missingEpisodesCount returns number of missing items`() {
        listOf(-1.0, 1.0, 2.0, 2.2, 4.0, 6.0, 10.0, 10.0).missingEpisodesCount() shouldBe 5
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
    fun `calculateEpisodeGap returns 0 if either are not valid chapter numbers`() {
        calculateChapterGap(episode(-1.0), episode(10.0)) shouldBe 0
        calculateChapterGap(episode(99.0), episode(-1.0)) shouldBe 0

        calculateChapterGap(-1.0, 10.0) shouldBe 0
        calculateChapterGap(99.0, -1.0) shouldBe 0
    }

    private fun episode(number: Double) = Episode.create().copy(
        episodeNumber = number,
    )
}
