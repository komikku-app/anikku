// AY -->
package tachiyomi.domain.season.interactor

import tachiyomi.domain.anime.model.Anime

class ShouldUpdateDbSeason {
    fun await(dbSeason: Anime, sourceSeason: Anime): Boolean {
        // ANK -->
        return dbSeason.ogTitle != sourceSeason.title ||
            // ANK <--
            dbSeason.seasonNumber != sourceSeason.seasonNumber ||
            dbSeason.seasonSourceOrder != sourceSeason.seasonSourceOrder
    }
}
// <-- AY
