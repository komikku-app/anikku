// AY -->
package eu.kanade.domain.anime.interactor

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.NoSeasonsException
import tachiyomi.domain.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.season.interactor.ShouldUpdateDbSeason
import tachiyomi.domain.season.service.SeasonRecognition
import tachiyomi.source.local.isLocal
import java.time.ZonedDateTime
import eu.kanade.domain.manga.interactor.UpdateManga as UpdateAnime
import mihon.domain.manga.model.toDomainManga as toDomainAnime
import tachiyomi.domain.manga.interactor.NetworkToLocalManga as NetworkToLocalAnime
import tachiyomi.domain.manga.model.toMangaUpdate as toAnimeUpdate
import tachiyomi.domain.manga.repository.MangaRepository as AnimeRepository

class SyncSeasonsWithSource(
    private val updateAnime: UpdateAnime,
    private val animeRepository: AnimeRepository,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val shouldUpdateDbSeason: ShouldUpdateDbSeason,
    private val getAnimeSeasonsByParentId: GetAnimeSeasonsByParentId,
) {

    /**
     * Method to synchronize db seasons with source ones
     *
     * @param rawSourceSeasons the seasons from the source.
     * @param anime the anime the seasons belong to.
     * @param source the source the anime belongs to.
     * @return Newly added seasons
     */
    suspend fun await(
        rawSourceSeasons: List<SAnime>,
        anime: Anime,
        source: AnimeSource,
        manualFetch: Boolean = false,
        fetchWindow: Pair<Long, Long> = Pair(0, 0),
    ): List<Anime> {
        if (rawSourceSeasons.isEmpty() && !source.isLocal()) {
            throw NoSeasonsException()
        }

        val now = ZonedDateTime.now()

        val sourceSeasons = rawSourceSeasons
            .distinctBy { it.url }
            .mapIndexed { i, sAnime ->
                networkToLocalAnime.invoke(sAnime.toDomainAnime(source.id))
                    .copy(parentId = anime.id, seasonSourceOrder = i.toLong())
            }

        val dbSeasons = getAnimeSeasonsByParentId.await(anime.id)

        val newSeasons = mutableListOf<Anime>()
        val updatedSeasons = mutableListOf<Anime>()
        val removedSeasons = dbSeasons.filterNot { dbSeasons ->
            sourceSeasons.any { sourceSeason ->
                dbSeasons.anime.url == sourceSeason.url
            }
        }

        for (sourceSeason in sourceSeasons) {
            var season = sourceSeason

            // Recognize season number for the season
            val seasonNumber = SeasonRecognition.parseSeasonNumber(
                anime.title,
                season.title,
                season.seasonNumber,
            )
            season = season.copy(seasonNumber = seasonNumber)

            val dbSeason = dbSeasons.find { it.anime.url == season.url }?.anime
            if (dbSeason == null) {
                newSeasons.add(season)
            } else {
                if (shouldUpdateDbSeason.await(dbSeason, season)) {
                    val toChangeSeason = dbSeason.copy(
                        // AM (CUSTOM_INFORMATION) -->
                        ogTitle = season.title,
                        // <-- AM (CUSTOM_INFORMATION)
                        seasonNumber = season.seasonNumber,
                        seasonSourceOrder = season.seasonSourceOrder,
                    )
                    updatedSeasons.add(toChangeSeason)
                }
            }
        }

        // Return if there's nothing to add, delete, or update to avoid unnecessary db transactions.
        if (newSeasons.isEmpty() && removedSeasons.isEmpty() && updatedSeasons.isEmpty()) {
            if (manualFetch || anime.fetchInterval == 0 || anime.nextUpdate < fetchWindow.first) {
                updateAnime.awaitUpdateFetchInterval(
                    anime,
                    now,
                    fetchWindow,
                )
            }
            return sourceSeasons
        }

        if (removedSeasons.isNotEmpty()) {
            val toDeleteIds = removedSeasons.map { it.id }
            animeRepository.removeParentIdByIds(toDeleteIds)
        }

        val toUpdate = newSeasons.map { it.toAnimeUpdate() } +
            updatedSeasons.map { it.toAnimeUpdate() }

        if (toUpdate.isNotEmpty()) {
            updateAnime.awaitAll(toUpdate)
        }

        updateAnime.awaitUpdateLastUpdate(anime.id)

        return sourceSeasons
    }
}
// <-- AY
