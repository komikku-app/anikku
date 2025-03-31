package eu.kanade.domain.manga.interactor

import tachiyomi.core.common.util.lang.toLong
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import kotlin.math.pow

class SetMangaViewerFlags(
    private val mangaRepository: MangaRepository,
) {

    suspend fun awaitSetSkipIntroLength(id: Long, flag: Long) {
        val anime = mangaRepository.getMangaById(id)
        mangaRepository.update(
            MangaUpdate(
                id = id,
                viewerFlags = anime.viewerFlags
                    .setFlag(flag, Manga.ANIME_INTRO_MASK)
                    // Disable skip intro button if length is set to 0
                    .setFlag((flag == 0L).toLong().addHexZeros(14), Manga.ANIME_INTRO_DISABLE_MASK),
            ),
        )
    }

    suspend fun awaitSetNextEpisodeAiring(id: Long, flags: Pair<Int, Long>) {
        awaitSetNextEpisodeToAir(id, flags.first.toLong().addHexZeros(zeros = 2))
        awaitSetNextEpisodeAiringAt(id, flags.second.addHexZeros(zeros = 6))
    }

    private suspend fun awaitSetNextEpisodeToAir(id: Long, flag: Long) {
        val anime = mangaRepository.getMangaById(id)
        mangaRepository.update(
            MangaUpdate(
                id = id,
                viewerFlags = anime.viewerFlags.setFlag(flag, Manga.ANIME_AIRING_EPISODE_MASK),
            ),
        )
    }

    private suspend fun awaitSetNextEpisodeAiringAt(id: Long, flag: Long) {
        val anime = mangaRepository.getMangaById(id)
        mangaRepository.update(
            MangaUpdate(
                id = id,
                viewerFlags = anime.viewerFlags.setFlag(flag, Manga.ANIME_AIRING_TIME_MASK),
            ),
        )
    }

    private fun Long.setFlag(flag: Long, mask: Long): Long {
        return this and mask.inv() or (flag and mask)
    }

    private fun Long.addHexZeros(zeros: Int): Long {
        val hex = 16.0
        return this.times(hex.pow(zeros)).toLong()
    }
}
